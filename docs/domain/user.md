# User.md

## 개요

User는 예약을 생성하는 사용자를 나타내는 도메인이다.

User는 예약(`reservation`) 생성의 주체가 되며  
삭제된 User는 예약 생성 대상에서 제외된다.

## 주요 속성

| 필드 | 설명 |
|---|---|
| user_id | User 식별자 |
| name | 사용자 이름 |
| phone | 연락처 |
| del_yn | 삭제 여부 |

## 비즈니스 규칙

- `phone`은 중복될 수 없다.

## 삭제 정책

- User 삭제는 소프트 삭제로 처리한다.
- 실제 데이터를 물리 삭제하지 않고 `del_yn` 값을 변경한다.
- 확정된 예약이 존재하는 경우에도 삭제할 수 있다.
- User 삭제 시 해당 User의 확정된 예약은 취소(CANCELLED) 처리한다.

## 미정 사항


## 관련 도메인

- `reservation`

## 테이블 요약

| 컬럼 | 설명 |
|---|---|
| user_id | User 식별자 |
| name | 사용자 이름 |
| phone | 연락처 (unique) |
| del_yn | 삭제 여부 |

## 참고 DDL

> 실제 스키마의 Source of Truth는 Flyway migration이다.  
> 본 DDL은 도메인 이해와 AI 코드 생성 보조를 위한 참고용이다.

```sql
CREATE TABLE user (
    user_id  VARCHAR(39)  NOT NULL PRIMARY KEY,
    name     VARCHAR(50)  NOT NULL,
    phone    VARCHAR(20)  NOT NULL UNIQUE,
    del_yn   CHAR(1)      NOT NULL DEFAULT 'N'
);
```