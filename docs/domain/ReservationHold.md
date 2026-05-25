# ReservationHold.md

## 개요

ReservationHold는 예약 확정 전 특정 사용자가 특정 슬롯의 수용 인원을 임시로 점유한 상태를 나타낸다.

ReservationHold는 DB에 저장하지 않고 Redis TTL 데이터로 관리한다. TTL이 만료되면 임시 점유는 자동으로 사라지며, 만료된 hold로는 예약을 확정할 수 없다.

## 주요 속성

| 필드 | 설명 |
|---|---|
| hold_id | 임시 점유 식별자 |
| slot_id | 임시 점유 대상 ReservationSlot 식별자 |
| user_id | 임시 점유 사용자 식별자 |
| party_size | 임시 점유 인원 |
| ttl_seconds | 남은 TTL 초 |

## 비즈니스 규칙

- 임시 점유 TTL은 5분이다.
- 임시 점유는 `slot_id` 단위 capacity 계산에 포함한다.
- `AVAILABLE` 상태의 슬롯에만 임시 점유를 생성할 수 있다.
- 삭제된 슬롯에는 임시 점유를 생성할 수 없다.
- 존재하지 않거나 삭제된 User는 임시 점유를 생성할 수 없다.
- `party_size`는 1 이상이어야 한다.
- DB의 활성 예약 인원과 Redis의 active hold 인원 합계가 슬롯의 `capacity`를 초과하면 임시 점유를 생성할 수 없다.
- 같은 `user_id + slot_id` 조합에는 active hold를 1개만 허용한다.
- 같은 `user_id + slot_id`로 다시 요청하면 기존 hold를 반환하고 TTL은 연장하지 않는다.
- 한 사용자는 active hold를 최대 3개까지 가질 수 있다.
- hold 응답에는 남은 TTL을 포함한다.
- 예약 확정은 유효한 `hold_id`가 Redis에 존재할 때만 가능하다.
- 예약 확정 시 `slot_id`, `party_size`는 Redis hold에 저장된 값을 사용한다.
- 예약 확정 시 요청 `user_id`와 Redis hold의 `user_id`가 일치해야 한다.
- 예약 확정 성공 후 hold는 삭제한다.
- 명시적 해제 요청 시 요청 `user_id`와 Redis hold의 `user_id`가 일치해야 한다.
- 악의적인 반복 점유 방지를 위한 rate limit, IP 제한, cooldown은 이번 단계에서 구현하지 않고 TODO로 남긴다.

## API

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

### 임시 점유 조회

```http
GET /reservation-holds/{holdId}
```

남은 TTL을 포함한 hold 정보를 반환한다.

### 예약 확정

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

### 임시 점유 해제

```http
DELETE /reservation-holds/{holdId}
```

요청:

```json
{
  "userId": "string"
}
```

## 관련 도메인

- `reservation`
- `reservation_slot`
- `user`

## 저장소 정책

- ReservationHold의 source of truth는 Redis다.
- DB Entity를 추가하지 않는다.
- 슬롯별 임시 점유 합계 계산을 위해 `slot_id` 기준 Redis 인덱스를 둔다.
- 사용자별 active hold 개수 제한을 위해 `user_id` 기준 Redis 인덱스를 둔다.
