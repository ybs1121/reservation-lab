# Reservation.md

## 개요

Reservation은 특정 사용자가 특정 슬롯을 예약한 내역을 나타내는 도메인이다.

Reservation은 예약자, 슬롯, 예약 인원, 예약 상태를 관리하며  
예약 확정 시 슬롯의 최대 수용 인원과 기존 예약 인원 합계를 기준으로 예약 가능 여부를 판단한다.

## 주요 속성

| 필드 | 설명 |
|---|---|
| reservation_id | Reservation 식별자 |
| slot_id | 예약 대상 슬롯 식별자 |
| user_id | 예약자 User 식별자 |
| party_size | 예약 인원 |
| status | 예약 상태 |
| del_yn | 삭제 여부 |

## 상태

Reservation의 상태값은 아래 세 가지만 사용한다.

| 상태 | 설명 |
|---|---|
| CONFIRMED | 예약 확정 |
| CANCELLED | 예약 취소 |
| NO_SHOW | 노쇼 |

임의의 추가 상태값을 만들지 않는다.

## 비즈니스 규칙

- 존재하지 않는 User로 예약을 생성할 수 없다.
- 삭제된 User로 예약을 생성할 수 없다.
- 존재하지 않는 슬롯으로 예약을 생성할 수 없다.
- 삭제된 슬롯으로 예약을 생성할 수 없다.
- `AVAILABLE` 상태의 슬롯에만 예약을 생성할 수 있다.
- `party_size`는 1 이상이어야 한다.
- `party_size`는 슬롯의 남은 수용 인원을 초과할 수 없다.
- Reservation 생성 시 상태는 `CONFIRMED`로 시작한다.
- 예약 생성 시 슬롯의 `capacity` 값을 직접 차감하지 않는다.
- 예약 가능 여부는 같은 슬롯의 활성 예약 인원 합계로 계산한다.
- 활성 예약 인원 합계 계산에는 `del_yn = N`이고 상태가 `CONFIRMED` 또는 `NO_SHOW`인 예약만 포함한다.
- `CANCELLED` 예약은 활성 예약 인원 합계에서 제외한다.
- 활성 예약 인원 합계가 슬롯의 `capacity`에 도달하면 슬롯 상태를 자동으로 `FULL`로 전환한다.
- 예약 취소(`CANCELLED`) 또는 확정 예약 삭제 후 활성 예약 인원 합계가 슬롯의 `capacity`보다 작고 슬롯 상태가 `FULL`이면 `AVAILABLE`로 자동 복구한다.
- 노쇼(`NO_SHOW`) 처리 시 활성 예약 인원 합계에서 제외하지 않는다.

## 수정 정책

- Reservation 수정 API는 상태만 변경할 수 있다.
- `slot_id`, `user_id`, `party_size`는 수정할 수 없다.
- 상태 전환은 `CONFIRMED`에서 `CANCELLED` 또는 `NO_SHOW`로만 허용한다.
- 이미 `CANCELLED` 또는 `NO_SHOW` 상태인 예약은 다른 상태로 변경할 수 없다.

## 삭제 정책

- Reservation 삭제는 소프트 삭제로 처리한다.
- 실제 데이터를 물리 삭제하지 않고 `del_yn` 값을 변경한다.
- `CONFIRMED` 예약 삭제 시 먼저 `CANCELLED` 처리한 뒤 삭제 처리한다.
- `CANCELLED`, `NO_SHOW` 예약 삭제 시 상태 변경 없이 삭제 처리한다.

## 관련 도메인

- `reservation_slot`
- `user`

## 테이블 요약

| 컬럼 | 설명 |
|---|---|
| reservation_id | Reservation 식별자 |
| slot_id | ReservationSlot 식별자 (FK) |
| user_id | User 식별자 (FK) |
| party_size | 예약 인원 |
| status | 예약 상태 |
| del_yn | 삭제 여부 |

## 참고 DDL

> 실제 스키마의 Source of Truth는 Flyway migration이다.  
> 본 DDL은 도메인 이해와 AI 코드 생성 보조를 위한 참고용이다.

```sql
CREATE TABLE reservation (
                             reservation_id  VARCHAR(39)  NOT NULL PRIMARY KEY,
                             slot_id         VARCHAR(39)  NOT NULL,
                             user_id         VARCHAR(39)  NOT NULL,
                             party_size      INT          NOT NULL,
                             status          VARCHAR(20)  NOT NULL,
                             del_yn          CHAR(1)      NOT NULL DEFAULT 'N'
);
```
