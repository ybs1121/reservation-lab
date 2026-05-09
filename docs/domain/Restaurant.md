# Restaurant.md

## 개요

Restaurant는 예약 가능한 식당 정보를 나타내는 도메인이다.

Restaurant는 식당의 기본 정보, 운영 상태, 삭제 여부를 관리하며  
예약 슬롯(`reservation_slot`) 생성 가능 여부를 판단하는 기준이 된다.

## 주요 속성

| 필드 | 설명 |
|---|---|
| restaurant_id | Restaurant 식별자 |
| name | 식당 이름 |
| address | 식당 주소 |
| status | 식당 상태 |
| del_yn | 삭제 여부 |

## 상태

Restaurant의 상태값은 아래 세 가지만 사용한다.

| 상태 | 설명 |
|---|---|
| OPEN | 운영 중 |
| CLOSED | 운영 종료 또는 닫힘 |
| SUSPENDED | 일시 중지 |

임의의 추가 상태값을 만들지 않는다.

## 비즈니스 규칙

- 예약 슬롯은 `OPEN` 상태의 Restaurant에만 생성할 수 있다.
- `CLOSED` 상태의 Restaurant에는 예약 슬롯을 생성할 수 없다.
- `SUSPENDED` 상태의 Restaurant에는 예약 슬롯을 생성할 수 없다.
- 삭제된 Restaurant은 예약 슬롯 생성 대상에서 제외한다.

## 삭제 정책

- Restaurant 삭제는 소프트 삭제로 처리한다.
- 실제 데이터를 물리 삭제하지 않고 `del_yn` 값을 변경한다.
- 미래 날짜의 예약이 존재하는 경우 Restaurant을 삭제할 수 없다.
- 미래 날짜의 예약이 존재하지 않는 경우 Restaurant을 삭제할 수 있다.

## 미정 사항

- 과거 예약만 존재하는 Restaurant의 삭제 허용 여부를 결정해야 한다.
- 삭제 전 상태가 `OPEN`, `CLOSED`, `SUSPENDED` 중 무엇이어야 하는지 결정해야 한다.

## 관련 도메인

- `reservation_slot`

## 테이블 요약

| 컬럼 | 설명 |
|---|---|
| restaurant_id | Restaurant 식별자 |
| name | 식당 이름 |
| address | 식당 주소 |
| status | 식당 상태 |
| del_yn | 삭제 여부 |

## 참고 DDL

> 실제 스키마의 Source of Truth는 Flyway migration이다.  
> 본 DDL은 도메인 이해와 AI 코드 생성 보조를 위한 참고용이다.

```sql
CREATE TABLE restaurant (
    restaurant_id VARCHAR(39)  NOT NULL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    address       VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    del_yn        CHAR(1)      NOT NULL DEFAULT 'N'
);
