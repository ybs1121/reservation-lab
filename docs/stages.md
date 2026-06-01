# stages.md

단계별 진행 플랜. 단계가 끝날 때마다 체크하고 README.md의 현재 단계도 함께 갱신한다.

---

## 현재 단계

**5단계 완료, 6단계 준비 중**

---

## 0단계 — 기반 세팅

**해결할 문제:** 다음 단계의 문제를 재현할 수 있는 베이스라인 구축

**작업 목록**
- [x] 프로젝트 초기화 (Spring Initializr)
- [x] 문서 세팅 (AGENTS.md, docs/)
- [x] 도메인 파일 작성 완료
- [x] 기본 CRUD API 구현 (Restaurant, ReservationSlot, User, Reservation)
- [x] 비즈니스 규칙 검증 테스트 작성

**핵심 기술:** Spring Boot, JPA, H2

**테스트 전략**
- `@SpringBootTest` 통합 테스트
- 비즈니스 규칙 검증 위주 (존재하지 않는 User 예약 불가, FULL 슬롯 예약 불가 등)
- 정상 케이스 1개 + 실패 케이스 1개 + 경계 케이스 1개 기준

**완료 기준:** 동시성 처리 없이 기본 예약 플로우가 동작한다

---

## 1단계 — 동시 예약 시 수용 인원 초과 문제 해결

**해결할 문제:** 같은 슬롯에 동시 요청이 들어오면 수용 인원을 초과한 예약이 발생한다

**핵심 기술:** Redisson 분산락

**테스트 전략**
- `ExecutorService` + `CountDownLatch`로 동시 요청 재현
- 분산락 없이 실행 → 수용 인원 초과 문제 재현
- 분산락 적용 후 → 테스트 통과 확인 (해결 증명)

```java
CountDownLatch latch = new CountDownLatch(1);
ExecutorService pool = Executors.newFixedThreadPool(10);

for (int i = 0; i < 10; i++) {
    pool.submit(() -> {
        latch.await();
        reservationService.reserve(slotId, userId, 1);
    });
}
latch.countDown();

// capacity 1인 슬롯에 10명이 동시 요청 → 예약은 1건만
assertThat(reservationRepository.countConfirmed(slotId)).isEqualTo(1);
```

**완료 기준:** 동시 요청 테스트에서 중복 예약이 발생하지 않는다

**진행 결과**
- [x] 동시 예약 문제 재현 테스트 작성
- [x] AOP + 어노테이션 기반 공통 분산락 구현
- [x] 예약 생성에 슬롯 단위 Redisson 분산락 적용
- [x] 분산락 적용 전 테스트에서 수용 인원 초과 재현 확인
- [x] 분산락 적용 후 테스트에서 수용 인원 초과 방지 확인

---

## 2단계 — 예약 확정 전 슬롯 선점 문제 해결

**해결할 문제:** 예약하기를 누르고 확정까지 가는 사이 다른 사람이 슬롯을 가져갈 수 있다

**핵심 기술:** Redis TTL 임시 점유

**테스트 전략**
- Testcontainers Redis
- 임시 점유 후 TTL 만료 시 슬롯 해제 확인
- 임시 점유 중 다른 요청이 들어올 때 거부 확인
- TTL 만료 시점 race condition 확인

**완료 기준:** 임시 점유 TTL 내에 확정하면 예약 성공, 만료 시 슬롯이 자동 해제된다

**진행 결과**
- [x] Redis TTL 기반 ReservationHold 정책 정리
- [x] 임시 점유 생성/조회/해제 API 추가
- [x] 임시 점유 확정 API 추가
- [x] 기존 직접 예약 생성 API 비활성화
- [x] 사용자별 active hold 최대 3개 제한 적용
- [x] hold 응답에 남은 TTL 포함
- [x] hold 생성과 확정에 슬롯 단위 분산락 재사용
- [x] Testcontainers Redis 기반 테스트 작성
- [x] Docker 실행 환경에서 Testcontainers Redis 테스트 실행 확인
- [x] TTL 만료 시점 race condition 테스트 보강

---

## 3단계 — 인기 음식점 조회 트래픽이 DB를 때리는 문제 해결

**해결할 문제:** 인기 음식점 조회 요청이 매번 DB를 조회한다

**핵심 기술:** Spring Cache + Redis (Cache-aside + Write-around)

**테스트 전략**
- Testcontainers Redis
- 첫 요청 → DB 조회 확인 (cache miss)
- 두 번째 요청 → 캐시 조회 확인 (cache hit)
- 음식점 정보 변경 시 캐시 무효화 확인

**완료 기준:** 반복 조회 시 DB가 아닌 캐시에서 응답한다

**진행 결과**
- [x] 전체 기간/최근 N일 인기 음식점 조회 기준 확정
- [x] `GET /restaurants/popular?limit=10` API 추가
- [x] Spring Cache + Redis 기반 인기 음식점 캐시 적용
- [x] Restaurant 수정/삭제, Reservation 생성/상태 변경/삭제 시 캐시 무효화 적용
- [x] Testcontainers Redis 기반 cache hit/miss 테스트 작성
- [x] 전체 테스트 통과 확인

---

## 4단계 — 인기도를 매번 계산하면 비싼 문제 해결

**해결할 문제:** 인기 음식점 선정 기준을 요청마다 계산하면 비용이 크다

**핵심 기술:** Spring Batch 인기도 집계 Job

**테스트 전략**
- `@SpringBatchTest`
- Job 실행 후 인기도 집계 결과 검증
- 집계 기준 (최근 N일 예약 수) 경계값 테스트

**완료 기준:** 배치 Job이 인기도를 집계하고, 인기 음식점 API가 집계 테이블을 기준으로 응답한다

**진행 결과**
- [x] Spring Batch 의존성 및 Batch 기본 설정 추가
- [x] `restaurant_popularity` 집계 테이블 매핑 entity 추가
- [x] `PopularityPeriodType` enum으로 전체 기간/최근 7일/30일/90일 기준 관리
- [x] 인기 음식점 집계 repository 추가
- [x] `popularRestaurantAggregationJob` 추가
- [x] 기존 집계 삭제 Tasklet Step 추가
- [x] 전체 기간/최근 7일/30일/90일 집계 Step 추가
- [x] Reader/Processor/Writer 구조로 집계 결과 저장 구현
- [x] Batch 완료 후 인기 음식점 Redis 캐시 무효화 연결
- [x] 인기 음식점 API 조회 소스를 실시간 집계에서 `restaurant_popularity` 조회로 변경
- [x] API 응답 구조를 `allTime`, `last7Days`, `last30Days`, `last90Days`로 변경
- [x] `limit` 기본값 10, 최대값 100 적용
- [x] `@SpringBatchTest` 기반 Job 실행 테스트 작성
- [x] API/cache 회귀 테스트 수정
- [x] 전체 테스트 통과 확인

---

## 5단계 — 특정 시점 대규모 트래픽 폭주 문제 해결

**해결할 문제:** 흑백요리사 효과처럼 예약 오픈 시 순간 트래픽이 폭주한다

**핵심 기술:** RabbitMQ 트래픽 흡수

**테스트 전략**
- k6로 실제 HTTP 부하 테스트 (JVM 외부)
- RabbitMQ 적용 전/후 에러율 및 응답시간 비교

```javascript
// 예약 오픈 시 500명이 동시에 몰리는 시나리오
export const options = {
    scenarios: {
        spike: {
            executor: 'arrival-rate',
            rate: 500,
            timeUnit: '1s',
            duration: '10s',
        }
    }
};
```

**완료 기준:** 트래픽 폭주 시 RabbitMQ 큐가 요청을 흡수하고 에러율이 감소한다

**진행 결과**
- [x] Spring AMQP 의존성 및 RabbitMQ 기본 설정 추가
- [x] docker compose 로컬 RabbitMQ 구성 추가
- [x] 비동기 예약 임시 점유 요청 entity/repository 추가
- [x] `POST /reservation-hold-requests` 요청 접수 API 추가
- [x] `GET /reservation-hold-requests/{requestId}` 요청 상태 조회 API 추가
- [x] RabbitMQ publisher, consumer, processor 추가
- [x] 기존 `ReservationHoldService.createHold()` 재사용 구조로 비동기 처리 연결
- [x] 비즈니스 실패와 시스템 실패 처리 정책 적용
- [x] Testcontainers RabbitMQ 기반 통합 테스트 추가
- [x] 동기/비동기 예약 임시 점유 k6 부하 테스트 스크립트 추가
- [x] 로컬 k6 성능 대시보드 추가
- [x] k6 비교 실행 문서 작성

---

## 6단계 — 조회/쓰기 부하가 같은 DB를 공유하는 문제 해결

**해결할 문제:** 음식점 조회(Read)와 예약(Write)이 같은 DB를 사용한다

**핵심 기술:** DB 레플리케이션 + Spring 읽기/쓰기 분리

**테스트 전략**
- Testcontainers MySQL (Primary + Replica 구성)
- 조회 요청이 Replica로 라우팅되는지 확인
- 예약 요청이 Primary로 라우팅되는지 확인
- Replication Lag 시나리오 직접 확인

**완료 기준:** 조회는 Replica, 쓰기는 Primary로 분리되어 라우팅된다

---

## 7단계 — 만료 예약, 노쇼가 쌓이는 문제 해결

**해결할 문제:** 지난 날짜의 예약 데이터가 정리되지 않고 쌓인다

**핵심 기술:** Spring Batch 정리 Job (4단계 인프라 재사용)

**테스트 전략**
- `@SpringBatchTest`
- 만료 기준일 경계값 테스트
- 정리 대상/비대상 데이터 분류 정확도 확인

**완료 기준:** 배치 Job이 만료 예약을 기준에 맞게 정리한다

---

## 8단계 — 취소된 슬롯을 빠르게 알리는 문제 해결

**해결할 문제:** 예약 취소로 슬롯이 풀렸을 때 대기 중인 사용자에게 알림이 느리다

**핵심 기술:** RabbitMQ 이벤트 라우팅 (5단계의 트래픽 흡수용 큐와 다른 용도)

**테스트 전략**
- Testcontainers RabbitMQ
- 취소 이벤트 발행 확인
- 대기열 사용자에게 메시지 라우팅 확인

**완료 기준:** 예약 취소 시 대기 사용자에게 알림 메시지가 라우팅된다

---

## 테스트 도구 요약

| 단계 | 도구 | 목적 |
|------|------|------|
| 0단계 | `@SpringBootTest` | 비즈니스 규칙 검증 |
| 1단계 | `CountDownLatch` | 동시성 재현 및 해결 확인 |
| 2단계 | Testcontainers Redis | TTL 임시 점유 동작 확인 |
| 3단계 | Testcontainers Redis | 캐시 hit/miss 검증 |
| 4단계 | `@SpringBatchTest` | 배치 Job 결과 검증 |
| 5단계 | k6 | 실제 HTTP 부하 테스트 |
| 6단계 | Testcontainers MySQL | Primary/Replica 라우팅 확인 |
| 7단계 | `@SpringBatchTest` | 정리 Job 결과 검증 |
| 8단계 | Testcontainers RabbitMQ | 이벤트 라우팅 확인 |
