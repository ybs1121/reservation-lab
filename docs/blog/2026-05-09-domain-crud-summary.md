# Reservation Lab 개발 기록 - 사용자, 식당, 예약 슬롯 CRUD API

> 예약 서비스의 기본 흐름을 구성하는 User, Restaurant, ReservationSlot 도메인에 CRUD API와 주요 검증 규칙을 추가했다.

---

## 1. 작업 범위

이번 작업에서는 예약 서비스에서 기본이 되는 세 가지 도메인을 확장했다.

```text
User
Restaurant
ReservationSlot
```

각 도메인에는 생성, 조회, 수정, 삭제 API를 추가했고, 삭제는 실제 데이터를 지우는 방식이 아니라 `delYn` 값을 변경하는 소프트 삭제 방식으로 처리했다.

또한 Reservation 도메인은 아직 API까지 만들지는 않았지만, 다른 도메인의 삭제 정책과 검증에 필요하므로 Entity와 Repository를 먼저 추가했다.

---

## 2. 도메인 문서 정리

기능을 추가하기 전에 도메인별 문서를 먼저 정리했다.

```text
docs/domain/user.md
docs/domain/Restaurant.md
docs/domain/ReservationSlot.md
docs/domain/Reservation.md
```

문서에는 각 도메인의 주요 속성, 상태값, 비즈니스 규칙, 삭제 정책을 정리했다.

이후 구현은 문서에 적힌 정책을 기준으로 맞췄고, 아직 확정되지 않은 부분은 코드에서 임의로 확정하지 않도록 남겨두었다.

---

## 3. User CRUD API

User는 예약을 생성하는 사용자를 나타내는 도메인이다.

추가한 API는 아래와 같다.

```text
POST   /users
GET    /users/{userId}
PUT    /users/{userId}
DELETE /users/{userId}
```

User에는 이름, 연락처, 삭제 여부를 관리하는 기본 필드를 두었다.

연락처는 중복될 수 없도록 생성과 수정 시 검증한다.

사용자 삭제는 소프트 삭제로 처리하고, 삭제되는 사용자의 확정 예약이 있다면 해당 예약을 취소 상태로 변경한다.

---

## 4. Restaurant CRUD API

Restaurant는 예약 가능한 식당을 나타내는 도메인이다.

기존 생성/조회 API에 수정/삭제 API를 추가했다.

```text
POST   /restaurants
GET    /restaurants/{restaurantId}
PUT    /restaurants/{restaurantId}
DELETE /restaurants/{restaurantId}
```

Restaurant는 `OPEN`, `CLOSED`, `SUSPENDED` 세 가지 상태를 가진다.

예약 슬롯은 `OPEN` 상태이고 삭제되지 않은 Restaurant에만 생성할 수 있다.

Restaurant 삭제도 소프트 삭제로 처리한다. 다만 미래 날짜의 확정 예약이 있으면 삭제할 수 없도록 검증을 추가했다.

---

## 5. ReservationSlot CRUD API

ReservationSlot은 특정 식당의 예약 가능한 날짜와 시간대를 나타내는 도메인이다.

추가한 API는 아래와 같다.

```text
POST   /reservation-slots
GET    /reservation-slots/{slotId}
PUT    /reservation-slots/{slotId}
DELETE /reservation-slots/{slotId}
```

ReservationSlot은 `AVAILABLE`, `FULL`, `CLOSED` 세 가지 상태를 가진다.

슬롯 생성과 수정에는 아래 검증을 적용했다.

```text
OPEN 상태의 Restaurant에만 슬롯을 생성할 수 있다.
삭제된 Restaurant에는 슬롯을 생성할 수 없다.
과거 날짜의 슬롯은 생성하거나 수정할 수 없다.
같은 식당, 같은 날짜, 같은 시간의 슬롯은 중복될 수 없다.
FULL 상태의 슬롯은 최대 수용 인원을 줄일 수 없다.
```

슬롯 삭제도 소프트 삭제로 처리한다.

다만 확정된 예약이 있는 슬롯은 삭제할 수 없도록 했다.

---

## 6. Reservation 기반 추가

Reservation은 특정 사용자가 특정 슬롯을 예약한 내역을 나타내는 도메인이다.

이번 단계에서는 Reservation API까지 만들지는 않았지만, 다른 도메인의 규칙을 처리하기 위해 Entity와 Repository를 추가했다.

현재 Reservation은 아래 용도로 사용된다.

```text
Restaurant 삭제 시 미래 확정 예약 존재 여부 확인
ReservationSlot 삭제 시 확정 예약 존재 여부 확인
User 삭제 시 확정 예약을 취소 상태로 변경
```

이후 예약 생성/취소 API를 추가하면 Reservation 문서에 정리된 수용 인원 차감, 슬롯 마감 처리, 취소 처리 규칙까지 이어서 구현할 수 있다.

---

## 7. 테스트로 확인한 것

이번 작업에서는 Controller, Service, Entity 단위로 주요 동작을 확인했다.

테스트 범위는 아래와 같다.

```text
User 생성/조회/수정/삭제
User 연락처 중복 검증
User 삭제 시 확정 예약 취소
Restaurant 생성/조회/수정/삭제
Restaurant 삭제 시 미래 확정 예약 검증
ReservationSlot 생성/조회/수정/삭제
ReservationSlot 중복 생성 방지
ReservationSlot 과거 날짜 검증
ReservationSlot 삭제 시 확정 예약 검증
Reservation 상태값과 소프트 삭제 동작
```

API 응답은 기존 공통 응답 형식인 `ApiResponse`를 유지했고, 존재하지 않는 리소스나 정책 위반은 `BizException`과 공통 예외 처리 구조를 통해 실패 응답으로 반환하도록 했다.

---

## 8. 다음에 할 일

다음 단계에서는 Reservation API를 추가할 수 있다.

예약 생성 시에는 User와 ReservationSlot의 상태를 확인하고, 예약 인원만큼 슬롯 수용 인원을 차감하는 흐름이 필요하다.

예약 취소나 노쇼 처리까지 들어가면 Reservation 상태 변화와 ReservationSlot 상태 변화가 함께 연결된다.

---

이 글은 학습 과정에서 정리한 내용을 AI와의 대화를 통해 다듬었습니다.
