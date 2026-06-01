# Reservation Hold k6 성능 비교

## 목적

동기 hold API와 RabbitMQ 기반 비동기 hold 요청 API를 같은 조건에서 비교한다.

- 동기 API: `POST /reservation-holds`
- 비동기 API: `POST /reservation-hold-requests`
- 비동기 상태 조회 API: `GET /reservation-hold-requests/{requestId}`

RabbitMQ 적용 효과는 단순히 HTTP 응답 시간이 줄었는지가 아니라, 순간 요청을 큐가 흡수하고 consumer 처리량에 맞춰 안정적으로 처리되는지로 확인한다.

## 사전 준비

로컬 인프라를 실행한다.

```powershell
docker compose up -d
```

애플리케이션을 local profile로 실행한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

RabbitMQ Management UI는 아래 주소에서 확인할 수 있다.

```text
http://localhost:15672
id: reservation
password: reservation
```

## k6 설치

Windows에서는 아래 명령으로 설치할 수 있다.

```powershell
winget install k6.k6
```

설치 확인:

```powershell
k6 version
```

## 기본 실행

동기 API 기준선:

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e VUS=20 -e DURATION=30s scripts/k6/reservation-hold-sync.js
```

비동기 API:

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e VUS=20 -e DURATION=30s scripts/k6/reservation-hold-async.js
```

## 대시보드 실행

`local` profile에서는 k6 대시보드가 함께 열린다.

```text
http://localhost:8080/performance-dashboard/index.html
```

대시보드에서 `Sync API` 또는 `Async MQ`를 선택하고 `Run k6`를 누르면 서버가 미리 정해진 k6 스크립트를 실행한다.

대시보드는 임의 명령을 실행하지 않고 아래 두 스크립트만 실행한다.

```text
scripts/k6/reservation-hold-sync.js
scripts/k6/reservation-hold-async.js
```

Windows에서 k6가 PATH에 잡히지 않으면 기본 설치 경로인 `C:\Program Files\k6\k6.exe`를 먼저 사용한다. 다른 경로에 설치했다면 환경 변수로 지정한다.

```powershell
$env:K6_PATH = "C:\path\to\k6.exe"
```

같은 실행 조건을 다시 만들고 싶으면 Redis/DB/RabbitMQ 데이터를 초기화한다.

```powershell
docker compose down -v
docker compose up -d
```

## 스크립트 동작 방식

각 스크립트의 `setup()`은 테스트용 restaurant과 reservation slot을 1개 만든다.

각 반복 요청은 새 user를 만든 뒤 hold 요청을 보낸다. 같은 `userId + slotId` 조합은 기존 hold를 반환하고, 한 user의 active hold는 최대 3개로 제한되기 때문에 부하 테스트에서는 매 요청마다 user를 분리한다.

slot capacity 기본값은 충분히 크게 둔다.

```text
SLOT_CAPACITY=10000
PARTY_SIZE=1
```

이 설정은 capacity 초과 실패가 아니라 hold 생성 처리 흐름 자체를 보기 위한 기본값이다.

## 주요 환경 변수

| 변수 | 기본값 | 설명 |
|---|---:|---|
| `BASE_URL` | `http://localhost:8080` | 애플리케이션 주소 |
| `VUS` | `20` | 동시에 요청하는 가상 사용자 수 |
| `DURATION` | `30s` | 테스트 시간 |
| `SLOT_CAPACITY` | `10000` | 테스트 slot 수용 인원 |
| `PARTY_SIZE` | `1` | 요청당 인원 |
| `RUN_ID` | 현재 시간 기반 | 테스트 데이터 식별자 |
| `POLL_INTERVAL_MS` | `500` | 비동기 상태 조회 간격 |
| `MAX_POLLS` | `60` | 비동기 상태 조회 최대 횟수 |

## 결과 해석

동기 API에서는 아래 지표를 본다.

| 지표 | 의미 |
|---|---|
| `hold_success_rate` | hold 생성 성공 비율 |
| `hold_http_duration` | `POST /reservation-holds` 응답 시간 |

비동기 API에서는 아래 지표를 나눠서 본다.

| 지표 | 의미 |
|---|---|
| `hold_request_accepted_rate` | 요청 접수 성공 비율 |
| `hold_request_completed_rate` | 상태 조회로 최종 상태까지 확인한 비율 |
| `hold_request_succeeded_rate` | 최종 상태가 `SUCCEEDED`인 비율 |
| `hold_request_accept_duration` | `POST /reservation-hold-requests` 접수 응답 시간 |
| `hold_result_ready_duration` | 접수 후 `SUCCEEDED` 또는 `FAILED`가 될 때까지 걸린 시간 |

비동기 API는 `hold_request_accept_duration`이 짧아도 `hold_result_ready_duration`이 길 수 있다. 이 경우 RabbitMQ가 요청을 빠르게 받아 큐에 쌓고, consumer가 설정된 처리량만큼 천천히 처리하고 있다는 뜻이다.

## RabbitMQ consumer 설정 비교

처음 기준선은 단일 consumer다.

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 1
        max-concurrency: 1
        prefetch: 1
```

비교 순서는 아래처럼 잡는다.

1. 동기 API
2. 비동기 API, `concurrency=1`, `prefetch=1`
3. 비동기 API, `concurrency=4`, `prefetch=1`
4. 비동기 API, `concurrency=4`, `prefetch=10`

consumer 수를 늘리면 큐에 쌓인 메시지를 더 빨리 처리할 수 있다. 다만 같은 slot에 대한 hold 생성은 분산락을 사용하므로, consumer를 늘려도 락 경합 때문에 처리량이 선형으로 증가하지 않을 수 있다.
