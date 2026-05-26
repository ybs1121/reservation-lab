# Reservation 분산락 적용 계획

## 목표

예약 생성 시 같은 슬롯에 동시 요청이 들어와 수용 인원을 초과하는 문제를 Redisson 분산락으로 해결한다.

공통 락 기능은 특정 도메인에 묶지 않고 AOP + 어노테이션 기반으로 작성해 이후 다른 유스케이스에서도 재사용할 수 있게 한다.

## 변경 범위

- `build.gradle`
  - AOP 사용을 위한 의존성 추가
  - Redisson 사용을 위한 의존성 추가
- `src/main/resources/application.yml`
  - local Redis 설정을 Redisson 설정에서도 재사용할 수 있도록 정리
- `common/config`
  - Redisson client 설정
  - 필요 시 Spring Cache 설정은 별도 config로 분리
- `common/component`
  - 분산락 어노테이션
  - 분산락 AOP aspect
  - 락 키 SpEL 평가 보조 컴포넌트
- `reservation/service`
  - 예약 생성 메서드에 슬롯 단위 분산락 어노테이션 적용
- `src/test`
  - 분산락 적용 전 동시성 재현 테스트 유지
  - 분산락 적용 후 동시성 해결 테스트 추가
  - 공통 락 AOP 동작 테스트 추가 여부 검토

## 설계 방향

### 1. AOP 기반 공통 분산락

어노테이션 예시:

```java
@DistributedLock(
        key = "'lock:reservation:slot:' + #slotId",
        waitTime = 3,
        leaseTime = 5,
        timeUnit = TimeUnit.SECONDS
)
```

- `key`는 SpEL로 받는다.
- 락 이름은 캐시 키와 도메인 충돌을 피하기 위해 `lock:` prefix를 명시한다.
- 락 획득 실패는 예상 가능한 비즈니스 예외로 처리한다.
- 에러 코드는 공통 영역의 `COM` prefix를 사용한다.
- 기본값은 과하게 일반화하지 않고 예약 생성 테스트를 통과할 만큼만 둔다.

### 2. 트랜잭션과 락 순서

예약 생성은 DB 읽기/쓰기 트랜잭션을 포함한다.

락은 트랜잭션보다 바깥에서 잡고, 트랜잭션 commit이 끝난 뒤 해제되어야 한다.

예상 흐름:

1. AOP에서 Redis lock 획득
2. `@Transactional` 예약 생성 로직 실행
3. 트랜잭션 commit
4. Redis lock 해제

이를 위해 분산락 aspect의 우선순위를 트랜잭션 interceptor보다 높게 둔다.

### 3. 예약 생성 락 키

예약 생성 동시성의 경쟁 자원은 ReservationSlot이다.

따라서 최초 적용 키는 아래로 둔다.

```text
lock:reservation:slot:{slotId}
```

이 키는 같은 슬롯의 예약 생성 요청만 직렬화하고, 다른 슬롯 예약은 서로 막지 않는다.

### 4. Redis Cache / Spring Cache 고려

분산락과 캐시는 같은 Redis 인스턴스를 사용할 수 있지만 책임을 분리한다.

- 분산락: Redisson `RLock` 사용
- 캐시: Spring Cache 추상화와 RedisCacheManager 사용 예정
- 키 prefix는 분리한다.
  - 락: `lock:*`
  - 캐시: `cache:*`
- Cache 설정은 3단계에서 본격 적용한다.
- 이번 단계에서는 캐시 정책, TTL, 캐시 무효화 정책을 임의로 확정하지 않는다.
- `cache:*`는 Redis 인스턴스를 나눈다는 의미가 아니라 Spring Cache가 생성할 키 네임스페이스를 분리한다는 의미다.

## 수용 기준

- 예약 생성 동시성 테스트에서 수용 인원을 초과한 확정 예약이 생기지 않는다.
- 분산락 기능은 예약 도메인 전용 코드가 아니라 공통 어노테이션/AOP로 제공된다.
- 예약 생성 락은 슬롯 단위로만 잡는다.
- 락 획득 실패는 공통 예외 정책에 맞게 `BizException`으로 처리한다.
- Redis Cache 설정과 충돌하지 않도록 락 설정과 캐시 설정의 책임을 분리한다.

## 테스트 기준

- 기존 순차 예약 테스트는 유지한다.
- 분산락 적용 전 테스트는 아래를 검증한다.
  - 동시 요청에서 수용 인원 초과 예약이 재현된다.
- 분산락 적용 후 테스트는 아래를 검증한다.
  - 성공 예약 수는 슬롯 수용 인원을 초과하지 않는다.
  - 활성 예약 인원 합계는 슬롯 수용 인원을 초과하지 않는다.
  - 초과 요청은 슬롯 마감, 수용 인원 초과, 락 획득 실패 중 하나로 실패한다.

## 결정 필요 항목

1. 락 획득 실패 에러 코드는 `COM00002`를 사용한다.
   - 메시지: 요청이 몰려 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.
2. `waitTime`과 `leaseTime` 기본값은 `waitTime=3초`, `leaseTime=5초`로 둔다.
3. 이번 단계에서는 Spring Cache 설정을 본격 추가하지 않고, Redis 설정 충돌 방지만 고려한다.

## 구현 순서

1. 의존성 및 Redis/Redisson 설정 추가
2. `@DistributedLock` 어노테이션과 aspect 작성
3. 예약 생성 메서드에 슬롯 단위 락 적용
4. 동시성 테스트를 “적용 전 초과 발생”과 “적용 후 초과 방지”로 분리
5. 테스트 실행 후 문서의 결과 섹션 갱신

## 결과

- `@DistributedLock` 어노테이션과 AOP를 추가했다.
- Redisson 설정은 `reservation-lab.distributed-lock.enabled=true`일 때만 활성화되도록 했다.
- local 프로필에서는 분산락을 활성화하고, 기본 테스트 프로필에서는 Redis 실행 여부에 묶이지 않도록 비활성화했다.
- 예약 생성은 `lock:reservation:slot:{slotId}` 키로 슬롯 단위 락을 적용했다.
- 동시성 테스트는 분산락 적용 전/후를 모두 검증하도록 분리했다.
  - 적용 전: `ReservationConcurrencyWithoutLockTest`
  - 적용 후: `ReservationConcurrencyWithDistributedLockTest`
- `./gradlew test` 통과를 확인했다.
