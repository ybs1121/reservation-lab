# Reservation CRUD 계획

## 목표

Reservation CRUD API를 추가한다.

## 확정 정책

- Reservation 수정 API는 상태만 변경한다.
- Reservation 생성 시 상태는 `CONFIRMED`로 시작한다.
- `slot_id`, `user_id`, `party_size`는 수정하지 않는다.
- 상태 전환은 `CONFIRMED`에서 `CANCELLED` 또는 `NO_SHOW`로만 허용한다.
- 이미 `CANCELLED` 또는 `NO_SHOW` 상태인 예약은 다른 상태로 변경하지 않는다.
- Reservation 삭제는 소프트 삭제로 처리한다.
- `CONFIRMED` 예약 삭제 시 먼저 `CANCELLED` 처리한 뒤 `del_yn = Y`로 변경한다.
- `CANCELLED`, `NO_SHOW` 예약 삭제는 상태 변경 없이 `del_yn = Y`로 변경한다.
- ReservationSlot의 `capacity`는 최대 수용 인원이다.
- 예약 생성, 취소, 삭제 시 `capacity`를 직접 차감하거나 복구하지 않는다.
- 예약 가능 여부는 같은 슬롯의 활성 예약 인원 합계로 계산한다.
- 활성 예약 인원 합계에는 `del_yn = N`이고 상태가 `CONFIRMED` 또는 `NO_SHOW`인 예약만 포함한다.
- `CANCELLED` 예약은 활성 예약 인원 합계에서 제외한다.
- 활성 예약 인원 합계가 `capacity`에 도달하면 슬롯 상태를 `FULL`로 변경한다.
- 예약 취소 또는 확정 예약 삭제 후 활성 예약 인원 합계가 `capacity`보다 작고 슬롯 상태가 `FULL`이면 `AVAILABLE`로 복구한다.
- `CLOSED` 상태 슬롯은 자동으로 `AVAILABLE`로 복구하지 않는다.

## 변경 범위

- `reservation` domain
- `reservationslot` entity/repository 일부
- reservation service/controller/dto
- reservation service/controller tests
- domain docs

## API

- `POST /reservations`
- `GET /reservations/{reservationId}`
- `PUT /reservations/{reservationId}`
- `DELETE /reservations/{reservationId}`

## 검증 기준

- 존재하지 않거나 삭제된 User로 예약을 생성할 수 없다.
- 존재하지 않거나 삭제된 ReservationSlot으로 예약을 생성할 수 없다.
- `AVAILABLE` 상태 슬롯에만 예약을 생성할 수 있다.
- `party_size`는 1 이상이어야 한다.
- 활성 예약 인원 합계와 신규 예약 인원 합이 슬롯 `capacity`를 초과할 수 없다.
- 상태 수정은 `CONFIRMED -> CANCELLED`, `CONFIRMED -> NO_SHOW`만 허용한다.
- `CONFIRMED -> CANCELLED` 후 슬롯이 `FULL`이고 활성 예약 인원 합계가 `capacity`보다 작으면 `AVAILABLE`로 복구한다.
- `CONFIRMED -> NO_SHOW`는 활성 예약 인원 합계에서 제외하지 않는다.
- 삭제는 소프트 삭제로 처리한다.
- API 응답은 DTO를 사용한다.
