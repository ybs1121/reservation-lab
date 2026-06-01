# Reservation Hold RabbitMQ 적용 계획

## 목표

5단계 목표는 예약 오픈 시점에 몰리는 hold 생성 요청을 RabbitMQ 큐로 흡수해, 애플리케이션이 순간 트래픽을 바로 처리하지 않고 순차적으로 처리할 수 있게 만드는 것이다.

이번 단계는 RabbitMQ를 프로젝트에 처음 적용하는 학습 단계이므로, 한 번에 전체 구조를 완성하기보다 작은 단위로 구현하고 각 단계에서 새로 등장하는 개념을 확인하면서 진행한다.

## 해결할 문제

현재 `POST /reservation-holds`는 요청이 들어오면 즉시 hold 생성 로직을 실행한다.

예약 오픈 시점처럼 많은 사용자가 동시에 같은 슬롯에 접근하면 애플리케이션과 Redis/DB가 한 번에 부하를 받는다. 5단계에서는 이 요청을 바로 처리하지 않고 먼저 큐에 넣은 뒤, consumer가 순서대로 기존 hold 생성 로직을 호출하도록 만든다.

## 적용 방향

기존 동기 hold API는 유지한다.

새 비동기 API를 추가해 점진적으로 전환할 수 있게 한다.

```text
기존 동기 흐름
POST /reservation-holds
-> ReservationHoldService.createHold()
-> hold 즉시 생성 또는 실패

새 비동기 흐름
POST /reservation-hold-requests
-> reservation_hold_request row 저장
-> RabbitMQ message 발행(requestId)
-> consumer가 requestId로 row 조회
-> 기존 ReservationHoldService.createHold() 호출
-> request row에 처리 결과 저장
```

이 방식은 기존 핵심 비즈니스 로직을 새로 만들지 않고, 기존 hold 생성 앞에 큐 기반 완충 계층을 추가하는 구조다.

## 확정된 결정

- RabbitMQ 적용 지점은 hold 생성 요청 시점으로 한다.
- 기존 `POST /reservation-holds` 동기 API는 유지한다.
- 새 비동기 API `POST /reservation-hold-requests`를 추가한다.
- 요청 상태는 DB 테이블에 저장한다.
- RabbitMQ 메시지에는 `requestId`만 담는다.
- API 응답 HTTP status는 기존 정책에 맞춰 `200`을 사용한다.
- 사용자에게 노출하는 실패 메시지는 단순하게 `요청 처리에 실패했습니다. 다시 시도해 주세요.` 정도로 둔다.
- 개발자 확인용 실패 구분은 `failureCode`로 저장한다.
- 오래된 요청 row 정리는 이번 단계에서 하지 않는다.
- 처음에는 단일 queue + 단일 consumer로 시작한다.
- 이후 확장 단계에서 consumer concurrency 또는 queue 전략을 바꿔 k6 성능 차이를 비교한다.
- 비즈니스 실패는 재시도하지 않는다.
- 시스템 실패는 1회 재시도한다.

## 요청 상태 모델

`ReservationHoldRequestStatus`

| 상태 | 의미 |
|---|---|
| PENDING | API가 요청을 저장하고 아직 처리 전인 상태 |
| PROCESSING | consumer가 메시지를 받아 처리 중인 상태 |
| SUCCEEDED | hold 생성에 성공한 상태 |
| FAILED | hold 생성에 실패한 상태 |

처음에는 `RETRYING` 상태를 따로 만들지 않고 `retryCount` 값으로 재시도 여부를 추적한다.

## 요청 테이블 계획

### reservation_hold_request

비동기 hold 생성 요청의 상태를 저장하는 테이블이다. RabbitMQ 메시지는 사라질 수 있고, API 사용자는 처리 결과를 나중에 조회해야 하므로 요청 상태는 DB에 남긴다.

| 컬럼 | 설명 |
|---|---|
| request_id | hold 생성 요청 식별자 |
| slot_id | hold 대상 슬롯 |
| user_id | 요청 사용자 |
| party_size | 요청 인원 |
| status | `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED` |
| hold_id | 성공 시 생성 또는 재사용된 hold id |
| retry_count | 시스템 실패 재시도 횟수 |
| failure_code | 개발자 확인용 실패 코드 |
| failure_message | 사용자 노출용 단순 실패 메시지 |
| requested_at | 요청 접수 시각 |
| processed_at | 처리 완료 시각 |
| created_at | 생성일시 |
| created_by | 생성자 |
| updated_at | 수정일시 |
| updated_by | 수정자 |

## API 계획

### hold 생성 요청 접수

`POST /reservation-hold-requests`

요청 예시:

```json
{
  "slotId": "slot-1",
  "userId": "user-1",
  "partySize": 2
}
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "requestId": "request-1",
    "status": "PENDING",
    "holdId": null,
    "failureCode": null,
    "failureMessage": null
  },
  "message": null,
  "code": null
}
```

### hold 생성 요청 상태 조회

`GET /reservation-hold-requests/{requestId}`

성공 예시:

```json
{
  "success": true,
  "data": {
    "requestId": "request-1",
    "status": "SUCCEEDED",
    "holdId": "hold-1",
    "failureCode": null,
    "failureMessage": null
  },
  "message": null,
  "code": null
}
```

실패 예시:

```json
{
  "success": true,
  "data": {
    "requestId": "request-1",
    "status": "FAILED",
    "holdId": null,
    "failureCode": "CAPACITY_EXCEEDED",
    "failureMessage": "요청 처리에 실패했습니다. 다시 시도해 주세요."
  },
  "message": null,
  "code": null
}
```

## RabbitMQ 구성 계획

처음에는 가장 단순한 구조로 시작한다.

```text
exchange: reservation.hold.exchange
queue: reservation.hold.request.queue
routing key: reservation.hold.request
message payload: requestId
consumer concurrency: 1
```

### 학습 포인트

- exchange: 메시지를 어떤 queue로 보낼지 결정하는 라우터 역할
- queue: 처리 대기 중인 메시지를 쌓아두는 공간
- routing key: exchange가 메시지를 queue에 연결할 때 사용하는 키
- publisher: API 요청을 받은 뒤 메시지를 발행하는 역할
- consumer/listener: queue의 메시지를 꺼내 실제 처리를 수행하는 역할
- ack: consumer가 메시지를 정상 처리했음을 RabbitMQ에 알리는 동작
- retry: 일시 실패가 있을 때 같은 메시지를 다시 처리하는 전략

## 재시도 정책

### 비즈니스 실패

재시도하지 않고 `FAILED`로 저장한다.

예시:

- 슬롯 capacity 초과
- 사용자 active hold 최대 개수 초과
- 존재하지 않는 user
- 존재하지 않는 slot
- 삭제된 user 또는 slot

이런 실패는 다시 실행해도 같은 결과가 나올 가능성이 높다.

### 시스템 실패

1회 재시도한다.

예시:

- 일시적인 DB 오류
- 일시적인 Redis 오류
- 예상하지 못한 runtime exception

1회 재시도 후에도 실패하면 `FAILED`로 저장하고 메시지는 완료 처리한다.

## 순차 개발 계획

### 1단계. RabbitMQ 의존성과 기본 설정 추가

목표:

- Spring AMQP 의존성을 추가한다.
- RabbitMQ exchange/queue/routing key 설정 클래스를 만든다.
- 애플리케이션이 RabbitMQ 없이도 기존 기능을 유지할 수 있는 설정 조건을 검토한다.

학습 확인:

- RabbitMQ에서 exchange와 queue가 각각 어떤 역할인지 확인한다.
- direct exchange를 먼저 사용하는 이유를 확인한다.

검증:

- 애플리케이션 context load 테스트 통과

### 2단계. hold request entity/repository 추가

목표:

- `reservation_hold_request` entity를 추가한다.
- 상태 enum을 추가한다.
- 요청 생성, 처리 시작, 성공, 실패 상태 변경 메서드를 entity에 둔다.

학습 확인:

- 메시지 자체에 모든 데이터를 넣지 않고 DB row + requestId를 사용하는 이유를 확인한다.

검증:

- repository 저장/조회 테스트 또는 service 테스트에서 상태 변경 확인

### 3단계. 비동기 hold 요청 접수 API 추가

목표:

- `POST /reservation-hold-requests`를 추가한다.
- 요청 row를 `PENDING`으로 저장한다.
- 아직 consumer 처리 전이므로 응답은 `requestId`와 `PENDING`을 반환한다.

학습 확인:

- 비동기 API가 즉시 최종 결과를 반환하지 않는 이유를 확인한다.

검증:

- controller/service 테스트에서 요청 row 저장과 응답 구조 확인

### 4단계. RabbitMQ publisher 추가

목표:

- 요청 저장 후 RabbitMQ에 `requestId` 메시지를 발행한다.
- 메시지 payload는 `requestId`만 사용한다.

학습 확인:

- publisher가 하는 일과 실제 비즈니스 처리는 분리된다는 점을 확인한다.

검증:

- publisher가 지정 exchange/routing key로 메시지를 보내는지 테스트한다.

### 5단계. RabbitMQ consumer 추가

목표:

- consumer가 `requestId`를 받아 요청 row를 조회한다.
- 상태를 `PROCESSING`으로 바꾼다.
- 기존 `ReservationHoldService.createHold()`를 호출한다.
- 성공하면 `SUCCEEDED`와 `holdId`를 저장한다.
- 실패하면 `FAILED`, `failureCode`, `failureMessage`를 저장한다.

학습 확인:

- consumer가 기존 service를 재사용하는 구조가 점진적 전환에 왜 유리한지 확인한다.
- ack와 실패 처리 흐름을 확인한다.

검증:

- consumer 직접 호출 테스트로 성공/실패 상태 변경 확인

### 6단계. 요청 상태 조회 API 추가

목표:

- `GET /reservation-hold-requests/{requestId}`를 추가한다.
- 현재 처리 상태, 성공 holdId, 실패 정보를 조회할 수 있게 한다.

학습 확인:

- 비동기 요청에서 상태 조회 API가 필요한 이유를 확인한다.

검증:

- `PENDING`, `SUCCEEDED`, `FAILED` 응답 구조 확인

### 7단계. Testcontainers RabbitMQ 기반 통합 테스트 추가

목표:

- 실제 RabbitMQ 컨테이너를 사용해 API -> queue -> consumer -> 상태 변경 흐름을 검증한다.

학습 확인:

- 단위 테스트와 메시징 통합 테스트의 차이를 확인한다.

검증:

- hold 요청 접수 후 최종적으로 `SUCCEEDED`가 되는지 확인
- capacity 초과 같은 비즈니스 실패가 `FAILED`가 되는지 확인
- 시스템 실패 1회 재시도 정책은 가능한 범위에서 별도 테스트한다.

### 8단계. k6 비교 시나리오 문서화

목표:

- 기존 동기 API와 새 비동기 API의 부하 테스트 시나리오를 문서화한다.
- 단일 queue/consumer 기준 결과를 먼저 남긴다.
- 이후 consumer concurrency 또는 queue 전략을 바꿔 성능 차이를 비교할 수 있게 한다.

학습 확인:

- RabbitMQ가 응답 시간을 줄이는 도구라기보다 요청을 흡수하고 처리량을 안정화하는 도구라는 점을 확인한다.

검증:

- k6 script 또는 실행 방법 문서 작성

## 구현 제외 범위

- 기존 `/reservation-holds` 제거
- 오래된 `reservation_hold_request` 정리 Job
- 관리자용 재처리 API
- dead letter queue 고도화
- slot별 queue sharding
- consumer concurrency 튜닝
- 운영용 RabbitMQ cluster 구성

위 항목은 5단계 기본 흐름이 안정화된 뒤 별도 단계에서 다룬다.

## 수용 기준

- 기존 동기 hold API는 계속 동작한다.
- 새 비동기 hold 요청 API가 `requestId`와 `PENDING` 상태를 반환한다.
- hold 요청 row가 DB에 저장된다.
- RabbitMQ 메시지에는 `requestId`만 담긴다.
- consumer가 기존 `ReservationHoldService.createHold()`를 재사용한다.
- hold 생성 성공 시 요청 상태가 `SUCCEEDED`가 되고 `holdId`가 저장된다.
- hold 생성 실패 시 요청 상태가 `FAILED`가 되고 `failureCode`가 저장된다.
- 사용자 노출 실패 메시지는 단순한 재시도 안내 문구를 사용한다.
- 비즈니스 실패는 재시도하지 않는다.
- 시스템 실패는 1회 재시도한다.
- 요청 상태 조회 API로 처리 결과를 확인할 수 있다.
- Testcontainers RabbitMQ 기반 통합 테스트가 통과한다.

## 진행 방식

이번 단계는 각 구현 단위마다 다음 순서로 진행한다.

1. 필요한 RabbitMQ 개념을 먼저 짧게 설명한다.
2. 해당 개념이 프로젝트 코드에서 어느 파일/역할로 나타나는지 확인한다.
3. 최소 구현을 추가한다.
4. 테스트로 동작을 확인한다.
5. 다음 단계로 넘어가기 전에 모호한 개념이나 정책을 다시 질문한다.

## 진행 결과

- [x] RabbitMQ 의존성과 기본 설정 추가
- [x] hold request entity/repository 추가
- [x] 비동기 hold 요청 접수 API 추가
- [x] RabbitMQ publisher 추가
- [x] RabbitMQ consumer 추가
- [x] 요청 상태 조회 API 추가
- [x] Testcontainers RabbitMQ 통합 테스트 추가
- [x] k6 비교 시나리오 문서화
- [ ] README.md 및 docs/stages.md 갱신
