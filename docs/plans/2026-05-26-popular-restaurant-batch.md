# 인기 음식점 인기도 집계 Batch 적용 계획

## 목표

4단계 목표는 인기 음식점 조회 시점마다 예약 데이터를 직접 집계하지 않고, Spring Batch Job이 미리 계산한 인기도 집계 결과를 조회하도록 바꾸는 것이다.

이번 단계는 단순 기능 추가뿐 아니라 Spring Batch의 기본 구조를 학습하는 것도 목표로 둔다. 그래서 구현 시 Job, Step, Reader, Processor, Writer, JobRepository, JobParameters의 역할이 코드와 주석에 드러나도록 작성한다.

## 해결할 문제

3단계에서는 인기 음식점 조회 결과를 Redis에 캐시했다.

하지만 cache miss가 발생하거나 캐시가 무효화된 직후에는 여전히 Restaurant, Reservation, ReservationSlot을 조인해 실시간 집계를 수행한다. 요청량이나 예약 데이터가 커지면 이 계산 비용이 조회 API에 직접 전파된다.

4단계에서는 조회 요청이 무거운 집계를 직접 수행하지 않도록, 배치 Job이 별도 집계 테이블을 갱신하고 API는 그 집계 결과를 읽도록 변경한다.

## 학습 목표

이번 작업에서는 아래 개념을 코드에서 확인할 수 있어야 한다.

- Job: 하나의 배치 작업 단위. 예: 인기 음식점 인기도 집계 전체 작업
- Step: Job을 구성하는 실행 단계. 예: 기존 집계 삭제, 전체 기간 집계 저장, 최근 7일/30일/90일 집계 저장
- ItemReader: 처리할 데이터를 읽는 역할. 예: DB에서 Restaurant별 예약 건수 집계 결과를 읽음
- ItemProcessor: 읽은 데이터를 저장 가능한 형태로 바꾸는 역할. 예: 집계 projection을 집계 entity로 변환
- ItemWriter: 처리된 데이터를 저장하는 역할. 예: 집계 테이블에 bulk 저장
- JobRepository: Job/Step 실행 이력과 상태를 저장하는 Batch 메타데이터 저장소
- JobParameters: 같은 Job을 어떤 실행으로 구분할지 나타내는 입력값. 예: `runAt=...`

## 결정 필요 항목

아래 항목은 구현 전에 확정이 필요하다.

1. 집계 테이블 추가 여부
   - 제안: `restaurant_popularity` 테이블을 추가한다.
   - 이유: API가 예약 테이블을 매번 직접 집계하지 않도록 하려면 조회용 결과 저장소가 필요하다.

2. 집계 범위
   - 확정: 전체 기간(`ALL_TIME`), 최근 7일(`LAST_7_DAYS`), 최근 30일(`LAST_30_DAYS`), 최근 90일(`LAST_90_DAYS`)을 함께 집계한다.
   - 이유: 기존 전체 기간 기준을 유지하면서 사용자가 기간별 인기도를 비교할 수 있다.

3. 날짜 기준 관리 방식
   - 확정: 날짜 기준은 enum으로 관리한다.
   - 이유: 7일, 30일, 90일 같은 정책값이 코드 여러 곳에 흩어지지 않도록 하고, Batch Step과 API 응답 기준을 같은 enum에서 맞춘다.

4. 스케줄링 포함 여부
   - 확정: 이번 단계에서는 스케줄링을 제외하고 수동 실행 가능한 Job과 테스트까지만 구현한다.
   - 이유: 먼저 Batch 구조와 집계 정확성을 검증하고, 실행 주기는 별도 정책으로 다루는 편이 단순하다.

5. 캐시 유지 여부
   - 제안: Redis 캐시는 유지한다.
   - 이유: Batch는 계산 비용을 줄이고, Redis 캐시는 반복 조회 부하를 줄인다. 두 기술의 역할이 다르다.

6. 패키지 구조
   - 확정: Spring Batch 구성 클래스는 `restaurant.batch` 패키지를 추가해 둔다.
   - 이유: Job/Step/Reader/Writer 설정은 controller/service/repository/component 중 하나에 억지로 넣기보다 batch 역할로 분리하는 편이 학습과 유지보수에 명확하다.

## 우선 제안

이번 단계의 구현 방향은 아래처럼 잡는다.

- 기존 API: `GET /restaurants/popular?limit=10`
- 응답 구조: `allTime`, `last7Days`, `last30Days`, `last90Days`를 반환하도록 변경
- 기본 `limit`: 10
- 최대 `limit`: 100
- 조회 기준:
  - 삭제되지 않은 `OPEN` Restaurant만 조회 대상
  - 삭제되지 않은 `CONFIRMED`, `NO_SHOW` Reservation만 집계 대상
  - `CANCELLED` Reservation과 삭제된 Reservation은 제외
  - 예약 건수가 0건인 Restaurant은 집계 row를 만들지 않고 인기 목록에서도 제외
  - 동률 정렬은 `reservation_count desc`, `restaurant.created_at asc`, `restaurant_id asc`
  - 최근 7일/30일/90일은 배치 실행 기준 시각의 직전 기간으로 계산
- 집계 테이블:
  - 전체 기간, 최근 7일, 최근 30일, 최근 90일 집계 row를 함께 저장
  - API는 집계 테이블에서 `reservationCount`가 높은 순서로 조회
- Batch Job:
  - Job 이름: `popularRestaurantAggregationJob`
  - JobParameter: `runAt`
  - Step 1: 기존 집계 결과 삭제
  - Step 2: 전체 기간 인기 음식점 집계 저장
  - Step 3: 최근 7일 인기 음식점 집계 저장
  - Step 4: 최근 30일 인기 음식점 집계 저장
  - Step 5: 최근 90일 인기 음식점 집계 저장
  - Step 6: 인기 음식점 캐시 무효화
- 스케줄링:
  - 이번 단계에서는 제외
  - 필요하면 다음 작은 작업으로 `@Scheduled` 또는 운영 실행 방식 결정
- 캐시:
  - 기존 Redis 캐시는 유지
  - Batch Job 완료 후 인기 음식점 캐시 전체 무효화
- Batch 메타데이터:
  - 이번 단계에서는 Spring Boot 자동 초기화에 맡긴다.
  - 운영 환경에서는 Spring Batch 메타 테이블을 직접 생성하고 관리하는 것이 원칙이다.

## 테이블 계획

### restaurant_popularity

인기 음식점 조회를 위한 읽기 모델이다. 원본 예약 데이터를 대체하지 않고, 조회 성능을 위해 계산 결과만 저장한다.

| 컬럼 | 설명 |
|---|---|
| popularity_id | 집계 row 식별자 |
| restaurant_id | Restaurant 식별자 |
| period_type | `ALL_TIME`, `LAST_7_DAYS`, `LAST_30_DAYS`, `LAST_90_DAYS` |
| period_days | 전체 기간은 null, 기간 기준은 7/30/90 |
| reservation_count | 해당 기간의 인기 기준 예약 건수 |
| aggregated_at | 이 row가 계산된 시각 |
| created_at | 생성일시 |
| created_by | 생성자 |
| updated_at | 수정일시 |
| updated_by | 수정자 |

제약 조건 제안:

- `(restaurant_id, period_type)` 유니크
- `reservation_count`는 0 이상
- 예약 건수가 0인 Restaurant은 저장하지 않는다.

## Enum 계획

### PopularityPeriodType

인기 음식점 집계 기간 정책을 표현한다.

| 값 | 설명 | 기간 일수 |
|---|---|---|
| ALL_TIME | 전체 기간 | null |
| LAST_7_DAYS | 최근 7일 | 7 |
| LAST_30_DAYS | 최근 30일 | 30 |
| LAST_90_DAYS | 최근 90일 | 90 |

역할:

- Batch Step이 어떤 기간을 집계하는지 표현한다.
- 집계 테이블의 `period_type`, `period_days` 값을 일관되게 만든다.
- API 응답 필드(`allTime`, `last7Days`, `last30Days`, `last90Days`)와 매핑되는 기준이 된다.

## Batch 구조 계획

### Job

`popularRestaurantAggregationJob`

전체 인기 음식점 집계 흐름을 대표한다. Job 하나가 실행되면 기존 집계 삭제부터 전체 기간/최근 기간 집계 저장까지 한 번에 끝나야 한다.

### Step 1. 기존 집계 삭제

역할:

- 같은 기준의 이전 집계 결과를 먼저 제거한다.
- 전체 기간, 최근 7일, 최근 30일, 최근 90일 집계를 삭제한다.

구현 방식:

- Tasklet 방식 후보
- 이유: 여러 item을 읽고 쓰는 흐름이 아니라, 기준에 맞는 기존 데이터를 지우는 단일 작업이기 때문이다.

학습 포인트:

- Tasklet Step은 chunk 기반 처리보다 단순한 단발성 작업에 적합하다.
- 이 Step은 Reader/Processor/Writer를 쓰지 않고도 Step을 만들 수 있다는 예시가 된다.

### Step 2. 전체 기간 집계 저장

역할:

- 전체 기간 예약 건수를 Restaurant별로 계산한다.
- 집계 결과를 `restaurant_popularity`에 `ALL_TIME` 기준으로 저장한다.

구현 방식:

- chunk 기반 Step
- Reader: 전체 기간 Restaurant별 예약 건수 projection 조회
- Processor: projection을 `RestaurantPopularity` entity로 변환
- Writer: `RestaurantPopularityRepository`로 저장

학습 포인트:

- Reader는 데이터를 가져오는 책임만 가진다.
- Processor는 변환 책임만 가진다.
- Writer는 저장 책임만 가진다.
- chunk size는 한 번에 읽고 쓰는 묶음 크기이며, 트랜잭션 경계와 함께 이해한다.

### Step 3. 최근 7일 집계 저장

역할:

- `PopularityPeriodType.LAST_7_DAYS` 기준으로 최근 기간 예약 건수를 Restaurant별로 계산한다.
- 집계 결과를 `restaurant_popularity`에 `LAST_7_DAYS` 기준으로 저장한다.
- 기준 시간은 JobParameter `runAt`을 정각으로 맞춘 값으로 사용한다.
- 집계 범위는 `runAt.minusDays(7)` 이상, `runAt` 이하 예약이다.

구현 방식:

- chunk 기반 Step
- Reader: 최근 7일 Restaurant별 예약 건수 projection 조회
- Processor: projection을 `RestaurantPopularity` entity로 변환
- Writer: `RestaurantPopularityRepository`로 저장

학습 포인트:

- enum 기준값을 Step 구성에 전달해 같은 구조의 Step을 기간별로 재사용하는 흐름을 확인한다.
- 중복을 줄이되, 학습을 위해 각 Step이 어떤 기간을 담당하는지 이름과 주석에 드러낸다.

### Step 4. 최근 30일 집계 저장

역할:

- `PopularityPeriodType.LAST_30_DAYS` 기준으로 최근 기간 예약 건수를 Restaurant별로 계산한다.
- 집계 결과를 `restaurant_popularity`에 `LAST_30_DAYS` 기준으로 저장한다.
- 집계 범위는 `runAt.minusDays(30)` 이상, `runAt` 이하 예약이다.

구현 방식:

- Step 3과 같은 chunk 기반 구조를 사용한다.
- Reader의 조회 시작일만 최근 30일 기준으로 달라진다.

학습 포인트:

- 같은 Reader/Processor/Writer 패턴을 다른 기간 정책에 재사용하는 방법을 확인한다.

### Step 5. 최근 90일 집계 저장

역할:

- `PopularityPeriodType.LAST_90_DAYS` 기준으로 최근 기간 예약 건수를 Restaurant별로 계산한다.
- 집계 결과를 `restaurant_popularity`에 `LAST_90_DAYS` 기준으로 저장한다.
- 집계 범위는 `runAt.minusDays(90)` 이상, `runAt` 이하 예약이다.

구현 방식:

- Step 3과 같은 chunk 기반 구조를 사용한다.
- Reader의 조회 시작일만 최근 90일 기준으로 달라진다.

학습 포인트:

- 기간 정책이 늘어나도 enum과 Step 생성 메서드로 변경 범위를 제한하는 구조를 확인한다.

### Step 6. 인기 음식점 캐시 무효화

역할:

- 배치 집계 결과가 교체된 뒤 기존 인기 음식점 Redis 캐시를 비운다.
- 다음 API 조회가 새 집계 테이블 기준으로 캐시를 다시 만들도록 한다.

구현 방식:

- Tasklet 방식 후보
- 이유: 캐시 무효화는 item 단위 처리가 아니라 단일 후처리 작업이기 때문이다.

학습 포인트:

- Job 마지막 Step에서 후처리 작업을 실행하는 흐름을 확인한다.

## 주석 작성 기준

이번 단계는 학습 목적이 있으므로 배치 관련 코드는 평소보다 역할 설명을 더 남긴다.

주석을 남길 위치:

- Batch Job 설정 클래스
  - Job이 어떤 업무 흐름을 대표하는지 설명
- Step bean
  - 각 Step이 왜 나뉘었는지 설명
- Reader
  - 어떤 데이터를 어떤 기준으로 읽는지 설명
- Processor
  - projection을 왜 별도 entity로 변환하는지 설명
- Writer
  - 저장 대상과 chunk 저장 흐름 설명
- 테스트
  - given 데이터가 어떤 인기 순위를 만들기 위한 것인지 설명

주석을 피할 위치:

- 단순 getter/setter
- Lombok으로 대체되는 생성자/접근자
- 코드만 봐도 명확한 단순 대입

## 구현 범위

- `build.gradle`
  - Spring Batch 의존성 추가
  - Spring Batch 테스트 의존성 추가
- batch config
  - Job/Step 설정
  - JobParameter `runAt` 처리
- restaurant entity
  - `RestaurantPopularity` 추가
  - `PopularityPeriodType` enum 추가
- restaurant repository
  - 집계 테이블 저장/조회 repository 추가
  - 전체 기간/최근 7일/30일/90일 집계 projection 조회 추가
- restaurant service
  - 인기 음식점 API 조회 소스를 실시간 집계 쿼리에서 집계 테이블 조회로 변경
  - 응답 DTO를 `allTime`, `last7Days`, `last30Days`, `last90Days` 구조로 변경
- cache component
  - Batch Job 완료 후 인기 음식점 캐시 무효화
- tests
  - `@SpringBatchTest` 기반 Job 실행 테스트
  - 집계 결과 조회 테스트
  - 기존 인기 음식점 API 응답 회귀 테스트
- docs
  - `README.md`, `docs/stages.md`, `docs/domain/Restaurant.md` 갱신

## 구현 제외 범위

- 운영 스케줄링
- 분산 환경에서 같은 Job 중복 실행 방지
- 대용량 파티셔닝
- 멀티스레드 Step
- 실패 Job 재시작 전략 고도화
- 관리자용 Job 실행 API
- 임의 날짜 기준 전체 조합 사전 집계
- 요청 파라미터 기반 임의 기간 조회
- 운영용 Batch 메타테이블 DDL 작성

위 항목들은 Batch 기본 구조를 익힌 뒤 별도 단계에서 다룬다.

## 수용 기준

- Batch Job 실행 후 `restaurant_popularity`에 전체 기간 인기 집계 결과가 저장된다.
- Batch Job 실행 후 `restaurant_popularity`에 최근 7일, 30일, 90일 인기 집계 결과가 저장된다.
- 최근 기간 집계는 JobParameter `runAt` 기준 직전 7일, 30일, 90일로 계산된다.
- 삭제된 Restaurant은 집계 결과에 포함되지 않는다.
- `OPEN`이 아닌 Restaurant은 집계 결과에 포함되지 않는다.
- 삭제된 Reservation은 집계에 포함되지 않는다.
- `CANCELLED` Reservation은 집계에 포함되지 않는다.
- `CONFIRMED`, `NO_SHOW` Reservation은 집계에 포함된다.
- 예약 건수가 0인 Restaurant은 집계 결과에 포함되지 않는다.
- 예약 건수가 같으면 Restaurant 생성일이 빠른 순서로 정렬된다.
- 생성일도 같으면 `restaurant_id` 오름차순으로 정렬된다.
- `GET /restaurants/popular` 응답은 `allTime`, `last7Days`, `last30Days`, `last90Days`를 반환한다.
- `limit` 기본값은 10이다.
- `limit`은 최대 100까지만 허용한다.
- 인기 음식점 API는 집계 테이블을 기준으로 응답한다.
- Batch Job 완료 후 기존 인기 음식점 Redis 캐시는 무효화된다.

## 테스트 계획

### Batch Job 테스트

- Job을 실행하면 전체 기간 집계 row가 생성된다.
- Job을 실행하면 최근 7일, 30일, 90일 집계 row가 생성된다.
- 같은 Job을 같은 기준으로 다시 실행해도 중복 row가 남지 않고 최신 결과로 교체된다.
- 7일 기준 밖의 예약은 `LAST_7_DAYS` 집계에서 제외된다.
- 30일 기준 밖의 예약은 `LAST_30_DAYS` 집계에서 제외된다.
- 90일 기준 밖의 예약은 `LAST_90_DAYS` 집계에서 제외된다.
- `NO_SHOW` 예약은 집계에 포함된다.
- `CANCELLED` 예약은 집계에서 제외된다.
- 예약 건수가 0인 Restaurant은 집계 row가 생성되지 않는다.
- 예약 건수가 같은 Restaurant은 생성일이 빠른 순서로 정렬된다.

### API 회귀 테스트

- 인기 음식점 API는 `allTime`, `last7Days`, `last30Days`, `last90Days` 응답 구조를 반환한다.
- 전체 기간 인기 목록은 집계 테이블의 `ALL_TIME` 결과를 기준으로 반환된다.
- 최근 7일 인기 목록은 집계 테이블의 `LAST_7_DAYS` 결과를 기준으로 반환된다.
- 최근 30일 인기 목록은 집계 테이블의 `LAST_30_DAYS` 결과를 기준으로 반환된다.
- 최근 90일 인기 목록은 집계 테이블의 `LAST_90_DAYS` 결과를 기준으로 반환된다.
- `limit`이 비어 있으면 기본값 10을 사용한다.
- `limit`이 100을 초과하면 100으로 보정한다.

### 캐시 테스트

- Batch Job 완료 후 인기 음식점 캐시가 무효화된다.
- Batch Job 실행 전 캐시된 결과가 있어도, Job 완료 후 다음 조회는 새 집계 결과를 반영한다.

## 작업 순서

1. 정책 확정
   - 검증: 이 계획 문서의 결정 필요 항목 정리
2. Spring Batch 의존성 추가
   - 검증: 애플리케이션 컨텍스트 로딩 확인
3. 집계 테이블/entity/repository 추가
   - 검증: repository 테스트 또는 Job 테스트에서 저장/조회 확인
4. Batch Job/Step 구성
   - 검증: `@SpringBatchTest`로 Job 실행 상태 확인
5. 집계 정확도 테스트 작성
   - 검증: 전체 기간/최근 7일/30일/90일/상태/삭제 기준 통과
6. 인기 음식점 API 조회 소스 변경
   - 검증: 기존 controller/service 테스트 통과
7. Batch 완료 후 캐시 무효화 연결
   - 검증: 캐시 무효화 테스트 통과
8. 문서 갱신
   - 검증: `README.md`, `docs/stages.md`, `docs/domain/Restaurant.md` 기준 일치

## 확정된 결정

- 집계 테이블을 추가한다.
- 하나의 `popularRestaurantAggregationJob` 안에서 전체 기간, 7일, 30일, 90일 Step을 순서대로 실행한다.
- 전체 기간, 최근 7일, 최근 30일, 최근 90일을 함께 집계한다.
- 날짜 기준은 enum으로 관리한다.
- 최근 기간은 JobParameter `runAt` 기준 직전 기간으로 계산한다.
- 예: `runAt=2026-05-26 00:00`이면 최근 7일은 `2026-05-19 00:00` 이상, `2026-05-26 00:00` 이하로 집계한다.
- Batch 실행 시각은 정각 기준을 우선 사용한다.
- 예약 건수 동률이면 Restaurant 생성일이 빠른 순서로 정렬한다.
- 예약 건수가 0인 Restaurant은 집계 row를 만들지 않는다.
- 인기 음식점 API의 `limit` 기본값은 10, 최대값은 100이다.
- Spring Batch 메타데이터는 이번 단계에서 Spring Boot 자동 초기화에 맡기고, 운영에서는 직접 생성/관리하는 것을 원칙으로 한다.
- Redis 캐시는 유지한다.
- Batch 역할은 `restaurant.batch` 패키지로 분리한다.
- 인기 음식점 API 응답 구조는 `allTime`, `last7Days`, `last30Days`, `last90Days`로 변경한다.

## 확인 필요 질문

- 없음

## 참고 자료

- Spring Batch Reference Documentation: https://docs.spring.io/spring-batch/reference/

## 진행 결과

- [x] 정책 확정
- [x] Spring Batch 의존성 추가
- [x] 집계 테이블/entity 추가
- [x] 집계 repository 추가
- [x] Batch Job/Step 뼈대 구성
- [x] Batch Job/Step 구성
- [x] Batch Job 테스트 작성
- [x] 인기 음식점 API 조회 소스 변경
- [x] Batch 완료 후 캐시 무효화 연결
- [x] 문서 갱신
