# Reservation 임시 점유 적용 계획

## 목표

2단계 목표는 예약 확정 전 사용자가 슬롯을 임시로 선점할 수 있게 하여, 결제 또는 최종 확인 화면에 머무는 동안 다른 사용자가 같은 슬롯을 가져가는 문제를 줄이는 것이다.

핵심 기술은 Redis TTL 기반 임시 점유다.

## 해결할 문제

현재 예약은 `POST /reservations` 요청 시 바로 `CONFIRMED` 상태로 생성된다.

다음과 같은 흐름은 아직 표현할 수 없다.

1. 사용자가 예약 가능한 슬롯을 선택한다.
2. 일정 시간 동안 해당 슬롯의 수용 인원을 임시 점유한다.
3. TTL 안에 확정하면 예약이 생성된다.
4. TTL이 지나면 임시 점유는 자동 만료되고 다른 사용자가 예약할 수 있다.

## 기본 정책

- 임시 점유 TTL은 5분으로 한다.
- 임시 점유는 `slotId` 단위 capacity 계산에 포함한다.
- 예약 확정은 유효한 `holdId`가 Redis에 존재할 때만 가능하다.
- `holdId`는 서버가 생성한다.
- 같은 `userId + slotId` 조합에는 active hold를 1개만 허용한다.
- 같은 `userId + slotId`의 active hold가 이미 있으면 새 hold를 만들지 않고 기존 hold를 반환한다.
- 기존 hold를 반환할 때 TTL은 연장하지 않는다.
- hold 응답에는 남은 TTL을 포함한다.
- 예약 확정 성공 후 hold는 삭제한다.
- 만료되었거나 존재하지 않는 `holdId`로 확정 요청이 오면 예약을 생성하지 않는다.
- 확정 시 요청 사용자와 Redis에 저장된 `userId`가 일치해야 한다.
- 사용자별 active hold 개수 제한은 3개로 한다.
- 악의적인 반복 점유를 완전히 막는 rate limit, IP 제한, cooldown은 이번 단계에서 구현하지 않고 TODO로 남긴다.

## Redis 저장 구조

슬롯별 임시 점유 합계 계산이 필요하므로 hold 단건 키만 두지 않는다.

### hold 단건

```text
reservation-hold:{holdId}
```

값:

```json
{
  "holdId": "string",
  "slotId": "string",
  "userId": "string",
  "partySize": 1
}
```

TTL: 5분

### 슬롯별 hold 인덱스

```text
reservation-holds:slot:{slotId}
```

값:

```text
field: holdId
value: userId, partySize
entry ttl: 5분
```

Redisson `RMapCache` 사용을 우선 검토한다.

### 사용자별 hold 인덱스

```text
reservation-holds:user:{userId}
```

같은 사용자의 전체 active hold 개수 제한을 적용할 수 있도록 인덱스를 둔다.

제한값:

```text
user active hold max count = 3
```

## API 계획

### 임시 점유 생성

```http
POST /reservation-holds
```

요청:

```json
{
  "slotId": "string",
  "userId": "string",
  "partySize": 1
}
```

응답:

```json
{
  "success": true,
  "data": {
    "holdId": "string",
    "slotId": "string",
    "userId": "string",
    "partySize": 1,
    "ttlSeconds": 300
  },
  "message": null,
  "code": null
}
```

처리 흐름:

1. 사용자, 슬롯, 인원 검증
2. 슬롯 단위 분산락 획득
3. 같은 `userId + slotId` active hold 조회
4. 기존 hold가 있으면 TTL 연장 없이 기존 hold 반환
5. DB 활성 예약 인원 + Redis active hold 인원 합계 계산
6. capacity 초과 시 실패
7. hold 생성 후 Redis에 5분 TTL 저장

### 예약 확정

예약 확정 API는 임시 점유 API 하위로 분리한다.

```http
POST /reservation-holds/{holdId}/confirm
```

요청:

```json
{
  "reservationId": "string",
  "userId": "string",
  "createdBy": "string"
}
```

`slotId`, `partySize`는 Redis hold에 저장된 값을 사용한다. 2단계 이후 클라이언트가 확정 요청에서 다시 보내지 않는다.

검증용으로 `slotId`, `partySize`를 함께 받는 방식도 가능하지만, 이번 단계에서는 API 입력을 줄이고 Redis hold를 source of truth로 둔다.

### 예약 직접 생성

```http
POST /reservations
```

2단계 이후 외부 API에서는 hold 없이 바로 확정 예약을 생성하지 않는다.

기존 `POST /reservations`는 제거하거나, 테스트/내부 helper 성격으로 남길지 구현 시 정리한다. 외부 요청 흐름의 기준은 `POST /reservation-holds/{holdId}/confirm`이다.

처리 흐름:

1. `holdId`로 Redis hold 조회
2. hold가 없으면 실패
3. 요청 사용자 또는 생성 주체가 hold의 `userId`와 일치하는지 검증
4. 슬롯 단위 분산락 획득
5. DB 활성 예약 인원 재계산
6. capacity 초과 시 실패
7. `CONFIRMED` 예약 생성
8. 슬롯이 가득 찼으면 `FULL` 처리
9. Redis hold 삭제

### 임시 점유 해제

```http
DELETE /reservation-holds/{holdId}
```

처리 흐름:

1. hold 조회
2. 요청 사용자와 hold의 `userId` 일치 여부 확인
3. hold 단건 및 인덱스 삭제

클라이언트가 항상 호출한다는 보장은 없으므로 TTL이 최종 안전장치다.

## 동시성 전략

- 임시 점유 생성은 `slotId` 기준 분산락을 사용한다.
- 예약 확정도 `slotId` 기준 분산락을 사용한다.
- 같은 슬롯에 대해 hold 생성과 예약 확정이 동시에 들어와도 capacity 계산이 깨지지 않아야 한다.
- 락 key prefix는 기존 분산락 정책과 맞춰 `lock:` prefix를 사용한다.

예:

```text
lock:reservation-hold:slot:{slotId}
lock:reservation:slot:{slotId}
```

락 key를 분리할 경우 hold 생성과 확정이 동시에 통과할 수 있으므로, 같은 슬롯 capacity를 다루는 작업은 동일한 key를 사용하는 방향을 우선 검토한다.

우선안:

```text
lock:reservation:slot:{slotId}
```

## 도메인/계층 계획

신규 도메인 패키지를 추가한다.

```text
reservationhold
  controller
  service
  component
  dto
```

역할:

- controller: 요청/응답 처리
- service: 임시 점유 생성, 조회, 해제, 확정 전 검증 흐름
- component: Redis 저장/조회 세부 처리
- dto: API 요청/응답

DB Entity는 추가하지 않는다. 임시 점유의 source of truth는 Redis TTL이다.

## 에러 코드 후보

정확한 코드명은 구현 전에 확정한다.

- `RESERVATION_HOLD_NOT_FOUND`: hold가 없거나 만료됨
- `RESERVATION_HOLD_ALREADY_EXISTS`: 필요 시 사용. 기본 정책은 기존 hold 반환이므로 에러로 쓰지 않을 수 있다.
- `RESERVATION_HOLD_LIMIT_EXCEEDED`: 사용자 active hold 개수 제한 초과
- `RESERVATION_HOLD_OWNER_MISMATCH`: hold 소유자 불일치

## 테스트 계획

Testcontainers Redis를 사용한다.

### 기본 테스트

- 임시 점유를 생성하면 Redis에 5분 TTL로 저장된다.
- 임시 점유 생성 시 같은 슬롯의 남은 수용 인원을 초과하면 실패한다.
- 같은 `userId + slotId`로 다시 점유하면 기존 hold를 반환하고 TTL은 연장하지 않는다.
- hold 생성/조회 응답에는 남은 TTL이 포함된다.
- `holdId`가 존재하면 예약 확정에 성공한다.
- 예약 확정 성공 후 hold가 삭제된다.
- 존재하지 않거나 만료된 `holdId`로 확정하면 실패한다.

### TTL 테스트

- TTL 만료 후 같은 슬롯을 다시 점유할 수 있다.
- TTL 만료 후 기존 `holdId`로 예약 확정할 수 없다.

### 동시성 테스트

- capacity 1 슬롯에 여러 사용자가 동시에 hold 요청하면 active hold는 1건만 성공한다.
- hold 생성과 예약 확정이 동시에 들어와도 capacity를 초과한 예약이 생성되지 않는다.

## 확정된 결정

1. 2단계 이후 외부 API에서는 `slotId`, `userId`, `partySize`를 직접 받아 예약을 확정하지 않는다.
2. 예약 확정 API는 `POST /reservation-holds/{holdId}/confirm`로 분리한다.
3. 확정 요청에서 `slotId`, `partySize`는 받지 않고 Redis hold에 저장된 값을 사용한다.
4. 사용자별 active hold 제한 기본값은 3개로 한다.
5. hold 생성/기존 hold 반환 응답에는 남은 TTL을 포함한다.

## 추가 확정 결정

1. 기존 `POST /reservations`는 외부 API로 직접 사용하지 않는다.
   - Controller 엔드포인트는 제거하거나 비활성화한다.
   - 기존 예약 생성 service 메서드는 hold 확정 흐름 내부에서 재사용할 수 있다.
2. hold 남은 TTL 조회 전용 API를 추가한다.
3. hold 해제 요청은 현재 인증 구조가 없으므로 request body의 `userId`를 Redis hold의 `userId`와 비교한다.
4. 에러 코드는 구현 시 아래 후보명을 우선 사용한다.
   - `RESERVATION_HOLD_NOT_FOUND`
   - `RESERVATION_HOLD_LIMIT_EXCEEDED`
   - `RESERVATION_HOLD_OWNER_MISMATCH`

### hold 조회 API

```http
GET /reservation-holds/{holdId}
```

용도:

- 화면에서 남은 시간 표시
- TTL 만료 시 이전 페이지로 되돌리거나 다시 점유하도록 안내
- 새로고침 후 현재 hold 상태 복구

## 작업 순서

1. 임시 점유 정책 문서 보강
   - 검증: `docs/domain/Reservation.md` 또는 별도 `docs/domain/ReservationHold.md`에 정책 반영
2. Redis hold 저장 component 구현
   - 검증: 단위/통합 테스트로 저장, 조회, 삭제, TTL 확인
3. ReservationHold service/controller/dto 추가
   - 검증: hold 생성/중복/제한/해제 테스트 통과
4. 예약 확정 흐름에 `holdId` 검증 연결
   - 검증: 유효 hold 확정 성공, 만료 hold 확정 실패 테스트 통과
5. 동시성 테스트 추가
   - 검증: capacity 초과 hold 및 예약 확정이 발생하지 않음
6. README.md, docs/stages.md 갱신
   - 검증: 2단계 진행 결과와 실행 조건 문서화

## 진행 결과

- [x] `ReservationHold` 도메인 문서 추가
- [x] Redis TTL 기반 hold 저장 component 추가
- [x] hold 생성/조회/해제 API 추가
- [x] hold 확정 API 추가
- [x] 기존 `POST /reservations` 직접 생성 흐름 비활성화
- [x] 사용자별 active hold 최대 3개 제한 적용
- [x] hold 응답에 남은 TTL 포함
- [x] Testcontainers Redis 기반 서비스/컨트롤러 테스트 추가
- [x] `./gradlew.bat test` 통과
- [x] Docker 실행 환경에서 Testcontainers Redis 테스트 실행 확인
- [ ] TTL 만료 시점 race condition 테스트는 추가 보강 필요
