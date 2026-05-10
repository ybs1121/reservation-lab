# Reservation 동시성 테스트 계획

## 목표

0단계 예약 생성 로직에 동시성 문제가 있음을 테스트로 재현한다.

## 변경 범위

- `src/test/java/com/toy/reservationlab/concurrency`
- Reservation 생성 동시성 테스트

## 테스트 기준

- 기초 데이터 init 메서드로 User, Restaurant, ReservationSlot을 준비한다.
- sleep을 둔 순차 예약 생성에서는 수용 인원 초과가 발생하지 않음을 확인한다.
- `CountDownLatch`로 여러 요청을 동시에 시작해 수용 인원 초과 예약이 발생하는지 확인한다.
- 테스트 로그에 성공/실패 건수, 활성 예약 인원 합계, 슬롯 수용 인원, 초과 여부를 출력한다.

## 현재 기대 결과

- 순차 테스트는 수용 인원 1명에 대해 성공 1건, 실패 1건이 된다.
- 동시성 테스트는 수용 인원 1명을 초과하는 예약 성공이 발생한다.
