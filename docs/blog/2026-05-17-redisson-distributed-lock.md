# Reservation Lab 개발 기록 - Redisson 분산락으로 동시 예약 초과 막기

> 1단계에서 같은 슬롯에 동시에 예약 요청이 들어올 때 수용 인원을 초과하는 문제를 Redisson 분산락으로 해결했다.

---

## 1. 현재 단계

`stages.md` 기준으로 이번 작업은 **1단계 - 동시 예약 시 수용 인원 초과 문제 해결**에 해당한다.

0단계에서는 동시성 처리 없이 기본 예약 플로우를 만들고, 동시 요청이 들어오면 수용 인원을 초과할 수 있다는 문제를 테스트로 재현했다.

이번 1단계에서는 그 문제를 Redisson 분산락으로 막고, 적용 전/후 테스트를 분리해 결과를 확인했다.

---

## 2. 문제 상황

예약 생성 로직은 슬롯의 `capacity`와 현재 활성 예약 인원 합계를 비교해서 예약 가능 여부를 판단한다.

순차 실행에서는 이 방식이 정상적으로 동작한다.

하지만 여러 요청이 동시에 들어오면 문제가 생길 수 있다.

```text
요청 A: 활성 예약 인원 합계 조회 -> 0명
요청 B: 활성 예약 인원 합계 조회 -> 0명
요청 A: 예약 가능하다고 판단하고 저장
요청 B: 예약 가능하다고 판단하고 저장
```

수용 인원이 1명인 슬롯이어도 위와 같은 흐름에서는 2건 이상의 예약이 성공할 수 있다.

이 문제의 경쟁 자원은 ReservationSlot이다.

따라서 같은 슬롯에 대한 예약 생성 요청만 직렬화하면 된다.

---

## 3. 해결 방식

이번 단계에서는 Redisson의 `RLock`을 사용해 슬롯 단위 분산락을 적용했다.

락 키는 아래처럼 잡았다.

```text
lock:reservation:slot:{slotId}
```

같은 `slotId`로 들어온 예약 요청은 같은 락을 바라보므로 한 번에 하나씩 처리된다.

반대로 서로 다른 슬롯에 대한 예약 요청은 락 키가 다르기 때문에 서로 막지 않는다.

---

## 4. AOP 기반 공통 분산락

분산락 코드를 ReservationService 안에 직접 넣지 않고, 어노테이션과 AOP로 분리했다.

```java
@DistributedLock(key = "'lock:reservation:slot:' + #slotId")
```

`@DistributedLock`은 메서드 단위로 사용할 수 있는 어노테이션이다.

락 키는 SpEL 표현식으로 작성하고, `DistributedLockKeyParser`가 실제 메서드 인자를 기준으로 키를 계산한다.

`DistributedLockAspect`는 아래 순서로 동작한다.

```text
1. SpEL로 락 키 계산
2. RedissonClient에서 RLock 조회
3. tryLock으로 락 획득
4. 실제 메서드 실행
5. 현재 스레드가 가진 락이면 unlock
```

락 획득에 실패하면 공통 예외 정책에 맞춰 `BizException`을 던진다.

또한 Aspect의 우선순위를 높게 두어 락이 트랜잭션보다 바깥에서 잡히도록 했다.

---

## 5. 예약 생성에 적용

Reservation 생성 메서드에 슬롯 단위 분산락을 적용했다.

```java
@Transactional
@DistributedLock(key = "'lock:reservation:slot:' + #slotId")
public Reservation createReservation(...) {
    ...
}
```

락이 잡힌 상태에서 아래 검증과 저장이 순서대로 실행된다.

```text
User 검증
ReservationSlot 검증
partySize 검증
활성 예약 인원 합계 조회
capacity 초과 여부 확인
Reservation 저장
필요하면 ReservationSlot을 FULL로 변경
```

이렇게 하면 같은 슬롯에 대한 동시 요청도 예약 가능 여부를 순서대로 판단하게 된다.

---

## 6. 설정과 로컬 인프라

Redisson과 Redis를 사용하기 위해 의존성과 설정도 추가했다.

추가된 주요 의존성은 아래와 같다.

```text
redisson
spring-boot-starter-data-redis
spring-aop
aspectjweaver
mysql-connector-j
```

기존 `application.properties`는 `application.yml`로 변경하고, 기본 테스트 프로필과 local 프로필을 분리했다.

기본 프로필에서는 분산락을 비활성화한다.

```yaml
reservation-lab:
  distributed-lock:
    enabled: false
```

local 프로필에서는 Redis 설정과 함께 분산락을 활성화한다.

```yaml
reservation-lab:
  distributed-lock:
    enabled: true
```

로컬에서 MySQL과 Redis를 함께 띄울 수 있도록 `docker-compose.yml`도 추가했다.

---

## 7. 테스트 분리

동시성 테스트는 분산락 적용 전과 적용 후를 분리했다.

```text
ReservationConcurrencyWithoutLockTest
ReservationConcurrencyWithDistributedLockTest
ReservationConcurrencyTestSupport
```

`ReservationConcurrencyWithoutLockTest`는 분산락이 꺼진 상태에서 같은 슬롯에 여러 요청을 동시에 보내고, 수용 인원 초과가 재현되는지 확인한다.

`ReservationConcurrencyWithDistributedLockTest`는 분산락이 켜진 상태에서 같은 조건으로 테스트하고, 성공 예약 수와 활성 예약 인원 합계가 슬롯 수용 인원을 넘지 않는지 확인한다.

Redis가 떠 있지 않은 환경에서는 분산락 테스트가 실행되지 않도록 Redis 연결 가능 여부도 확인한다.

---

## 8. 확인한 결과

이번 단계에서 확인한 내용은 아래와 같다.

```text
분산락 적용 전에는 동시 예약 시 수용 인원 초과가 재현된다.
분산락 적용 후에는 동시 예약해도 수용 인원을 초과하지 않는다.
락은 예약 도메인 전용 코드가 아니라 공통 어노테이션/AOP로 제공된다.
예약 생성 락은 슬롯 단위로만 잡는다.
기본 테스트 프로필은 Redis 실행 여부에 묶이지 않는다.
local 프로필에서는 Redis 기반 분산락을 사용할 수 있다.
```

README와 `stages.md`도 1단계 완료 상태로 갱신했다.

---

## 9. 다음 단계

다음 단계는 `stages.md`의 **2단계 - 예약 확정 전 슬롯 선점 문제 해결**이다.

1단계에서는 이미 들어온 예약 생성 요청을 슬롯 단위로 직렬화했다.

2단계에서는 사용자가 예약하기를 누른 뒤 최종 확정하기 전까지 슬롯을 임시로 점유하는 문제를 다룬다.

핵심 기술은 Redis TTL 임시 점유다.

---

## 10. 참고 링크

이번 글에서 다룬 실습 코드는 아래 저장소에서 확인할 수 있다.

- [reservation-lab GitHub 저장소](https://github.com/ybs1121/reservation-lab)

---

이 글은 학습 과정에서 정리한 내용을 AI와의 대화를 통해 다듬었습니다.
