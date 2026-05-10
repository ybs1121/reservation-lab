# ReservationSlot.md

## 개요

ReservationSlot은 특정 식당의 예약 가능한 시간대를 나타내는 도메인이다.

ReservationSlot은 날짜와 시간대, 최대 수용 인원, 슬롯 상태를 관리하며  
예약(`reservation`) 생성 가능 여부를 판단하는 기준이 된다.

## 주요 속성

| 필드 | 설명 |
|---|---|
| slot_id | ReservationSlot 식별자 |
| restaurant_id | 슬롯이 속한 Restaurant 식별자 |
| slot_date | 예약 날짜 |
| slot_time | 예약 시작 시간 (HH:mm 형식) |
| capacity | 최대 수용 인원 |
| status | 슬롯 상태 |
| del_yn | 삭제 여부 |

## 상태

ReservationSlot의 상태값은 아래 세 가지만 사용한다.

| 상태 | 설명 |
|---|---|
| AVAILABLE | 예약 가능 |
| FULL | 예약 마감 (수용 인원 소진) |
| CLOSED | 수동 닫힘 |

임의의 추가 상태값을 만들지 않는다.

## 비즈니스 규칙

- `OPEN` 상태의 Restaurant에만 슬롯을 생성할 수 있다.
- 삭제된 Restaurant에는 슬롯을 생성할 수 없다.
- 과거 날짜의 슬롯은 생성할 수 없다.
- 슬롯 날짜를 과거 날짜로 수정할 수 없다.
- 같은 Restaurant, 같은 날짜, 같은 시작 시간의 슬롯은 중복 생성할 수 없다.
- 같은 Restaurant, 같은 날짜, 같은 시작 시간의 슬롯으로 수정할 수 없다.
- 슬롯 시간은 시작 시간(HH:mm)만 관리한다.
- `AVAILABLE` 상태의 슬롯에만 예약을 생성할 수 있다.
- `FULL` 또는 `CLOSED` 상태의 슬롯에는 예약을 생성할 수 없다.
- 삭제된 슬롯은 예약 생성 대상에서 제외한다.
- 예약이 확정되어 활성 예약 인원 합계가 최대 수용 인원에 도달하면 슬롯 상태를 자동으로 `FULL`로 전환한다.
- `FULL` 상태의 슬롯은 최대 수용 인원을 줄일 수 없다.
- `capacity`는 기초 데이터인 최대 수용 인원이며 예약 생성 또는 취소 시 직접 차감하거나 복구하지 않는다.
- 남은 예약 가능 인원은 같은 슬롯의 활성 예약 인원 합계로 계산한다.
- 활성 예약 인원 합계 계산에는 `del_yn = N`이고 상태가 `CONFIRMED` 또는 `NO_SHOW`인 예약만 포함한다.
- `CANCELLED` 예약은 활성 예약 인원 합계에서 제외한다.
- 예약 취소 또는 확정 예약 삭제 후 활성 예약 인원 합계가 `capacity`보다 작고 슬롯 상태가 `FULL`이면 `AVAILABLE`로 자동 복구한다.
- `CLOSED` 상태는 수동 닫힘 상태이므로 예약 취소 또는 삭제로 자동 복구하지 않는다.

## 삭제 정책

- ReservationSlot 삭제는 소프트 삭제로 처리한다.
- 실제 데이터를 물리 삭제하지 않고 `del_yn` 값을 변경한다.
- 확정된 예약이 존재하는 슬롯은 삭제할 수 없다.

## 관련 도메인

- `restaurant`
- `reservation`

## 테이블 요약

| 컬럼 | 설명 |
|---|---|
| slot_id | ReservationSlot 식별자 |
| restaurant_id | Restaurant 식별자 (FK) |
| slot_date | 예약 날짜 |
| slot_time | 예약 시작 시간 (HH:mm) |
| capacity | 최대 수용 인원 |
| status | 슬롯 상태 |
| del_yn | 삭제 여부 |

## 참고 DDL

> 실제 스키마의 Source of Truth는 Flyway migration이다.  
> 본 DDL은 도메인 이해와 AI 코드 생성 보조를 위한 참고용이다.

```sql
CREATE TABLE reservation_slot (
                                  slot_id       VARCHAR(39)  NOT NULL PRIMARY KEY,
                                  restaurant_id VARCHAR(39)  NOT NULL,
                                  slot_date     DATE         NOT NULL,
                                  slot_time     VARCHAR(5)   NOT NULL,
                                  capacity      INT          NOT NULL,
                                  status        VARCHAR(20)  NOT NULL,
                                  del_yn        CHAR(1)      NOT NULL DEFAULT 'N'
);
```
