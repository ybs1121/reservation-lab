# 인기 음식점 조회 캐시 적용 계획

## 목표

3단계 목표는 인기 음식점 조회 요청이 반복될 때마다 DB를 직접 조회하지 않도록 Redis 기반 캐시를 적용하는 것이다.

핵심 기술은 Spring Cache + Redis이며, 이번 단계에서는 캐시 hit/miss와 무효화 동작을 테스트로 확인한다.

## 해결할 문제

현재 `Restaurant` 도메인은 단건 조회, 생성, 수정, 삭제 API만 제공한다.

인기 음식점 조회 API가 없으므로 3단계에서는 먼저 인기 음식점 조회 유스케이스를 추가하고, 동일 요청이 반복될 때 DB 조회를 줄이는 캐시를 적용한다.

## 결정 필요 항목

아래 정책은 아직 문서에 정의되어 있지 않으므로 구현 전에 확정이 필요하다.

1. 응답 개수
   - 기본 `limit` 값을 둘지, 요청 파라미터로 받을지 결정 필요
2. 캐시 TTL
   - 짧은 TTL만 둘지, 수정/삭제/예약 확정 시 명시적으로 무효화할지 결정 필요

## 우선 제안

4단계에서 인기도 집계 Batch를 별도로 다룰 예정이므로, 3단계에서는 복잡한 인기도 모델을 만들지 않는다.

확정안:

- API: `GET /restaurants/popular?limit=10&recentDays=7`
- 전체 기간 인기 기준: `CONFIRMED` 또는 `NO_SHOW` 상태이고 삭제되지 않은 예약 건수가 많은 음식점
- 최근 인기 기준: 최근 N일 안에 생성된 `CONFIRMED` 또는 `NO_SHOW` 상태이고 삭제되지 않은 예약 건수가 많은 음식점
- 조회 대상: 삭제되지 않고 `OPEN` 상태인 음식점
- 기본 `limit`: 10
- 기본 `recentDays`: 7
- 캐시 TTL: 60초
- 캐시 key:
  - `popular-restaurants:all-time:{limit}`
  - `popular-restaurants:recent:{recentDays}:{limit}`
- 무효화 기준:
  - Restaurant 생성 시에는 예약 건수가 0이라 인기 목록에 들어갈 수 없으므로 무효화하지 않음
  - Restaurant 수정/삭제 시 인기 음식점 캐시 전체 무효화
  - Reservation 확정/상태 변경/삭제 시 인기 음식점 캐시 전체 무효화

이 확정안은 4단계 Batch 도입 전까지 사용할 단순 조회 기준이다. 4단계에서는 동일한 응답 구조를 유지하면서 내부 조회 방식을 집계 결과 조회로 바꿀 수 있다.

현재 캐시 key가 `limit`, `recentDays` 조합별로 나뉘므로 특정 Restaurant 또는 특정 예약만 부분 무효화하지 않는다. 3단계에서는 정확성을 우선해 인기 기준에 영향을 줄 수 있는 쓰기 작업에서 전체 무효화하고, 4단계 집계 테이블 도입 후에는 변경된 restaurant 단위 집계 갱신 또는 고정된 랭킹 캐시 교체 방식으로 줄인다.

현재 Redis key 삭제는 인기 음식점 cache prefix를 기준으로 직접 삭제한다. 운영 규모에서는 `KEYS` 계열 접근 대신 scan 기반 삭제, 고정 랭킹 캐시 교체, 또는 집계 테이블 갱신 후 캐시 갱신 방식으로 바꾼다.

## API 계획

### 인기 음식점 조회

```http
GET /restaurants/popular?limit=10&recentDays=7
```

응답:

```json
{
  "success": true,
  "data": {
    "allTime": [
      {
        "restaurantId": "string",
        "name": "string",
        "address": "string",
        "status": "OPEN",
        "delYn": "N",
        "reservationCount": 10
      }
    ],
    "recent": [
      {
        "restaurantId": "string",
        "name": "string",
        "address": "string",
        "status": "OPEN",
        "delYn": "N",
        "reservationCount": 3
      }
    ]
  },
  "message": null,
  "code": null
}
```

`reservationCount`는 이번 단계의 인기 기준인 예약 건수를 나타낸다.

## 구현 범위

- `build.gradle`
  - Spring Cache 의존성 확인 및 필요 시 추가
- cache config
  - RedisCacheManager 설정
  - 인기 음식점 캐시 TTL 설정
- restaurant repository
  - 인기 음식점 조회 쿼리 추가
- restaurant dto
  - 인기 음식점 응답 DTO 추가
- restaurant service
  - 인기 음식점 조회 유스케이스 추가
  - `@Cacheable` 적용
  - 생성은 무효화하지 않고, 수정/삭제 시 캐시 무효화
- reservation service
  - 예약 확정/상태 변경/삭제 시 캐시 무효화
- restaurant controller
  - `GET /restaurants/popular` 추가
- tests
  - Testcontainers Redis 기반 cache hit/miss 테스트
  - Restaurant 수정/삭제 후 캐시 무효화 테스트
  - Reservation 확정 또는 취소 후 캐시 무효화 테스트

## 테스트 계획

Testcontainers Redis를 사용한다.

### 기본 테스트

- 첫 인기 음식점 조회는 DB 조회 후 Redis에 캐시한다.
- 같은 조건으로 두 번째 조회하면 DB 변경이 없어도 Redis 캐시에서 응답한다.
- 전체 기간 인기와 최근 N일 인기는 각각 독립된 캐시 key로 저장한다.

### 무효화 테스트

- Restaurant 정보를 수정하면 인기 음식점 캐시가 무효화된다.
- Restaurant을 삭제하면 인기 음식점 캐시가 무효화된다.
- 예약 확정 또는 상태 변경으로 인기 기준이 바뀌면 인기 음식점 캐시가 무효화된다.

### 경계 테스트

- `limit`이 비어 있으면 기본값 10을 사용한다.
- `recentDays`가 비어 있으면 기본값 7을 사용한다.
- 삭제된 Restaurant은 응답에서 제외한다.
- `OPEN`이 아닌 Restaurant은 응답에서 제외한다.

## 작업 순서

1. 인기 음식점 정책 확정
   - 검증: 이 계획 문서의 결정 필요 항목 정리
2. 인기 음식점 조회 API 추가
   - 검증: controller/service 테스트로 응답 확인
3. Redis cache 설정 추가
   - 검증: Testcontainers Redis로 cache hit 확인
4. 캐시 무효화 연결
   - 검증: Restaurant/Reservation 변경 후 cache miss 확인
5. 문서 갱신
   - 검증: `README.md`, `docs/stages.md`, 도메인 문서 갱신

## 진행 결과

- [x] 인기 음식점 정책 확정
- [x] 인기 음식점 조회 API 추가
- [x] Redis cache 설정 추가
- [x] cache hit/miss 테스트 작성
- [x] 캐시 무효화 테스트 작성
- [x] 전체 테스트 통과
