# reservation-lab

토이 프로젝트입니다.

## 목표

완성도 높은 서비스 개발이 아니라 학습이 목적입니다. 백엔드 기술 스택을 단계별로 점진적으로 적용하면서 검증합니다. AI와 함께 작업할 때는 하네스 엔지니어링 원칙을 적용합니다.

## 기술 스택

현재 단계 기준. 단계가 진행되면서 추가·갱신됩니다.

- Java 21
- Spring Boot 4.0.6
- Spring Web MVC
- Spring Data JPA
- Spring Batch
- Spring AMQP
- Redis
- Redisson
- RabbitMQ
- H2 Database
- Lombok
- Gradle
- k6

## 실행 환경

- Group: `com.toy`
- Artifact: `reservation-lab`
- Java Toolchain: `21`

## 브랜치 전략

- `master` — 안정 브랜치
- `dev` — 작업 브랜치

## 현재 단계

**5단계 — 특정 시점 대규모 트래픽 폭주 대응 완료**

- 0단계 기반 세팅 완료
- 1단계 Redisson 분산락 적용 완료
- Redis TTL 기반 임시 점유 API 추가
- hold 생성/조회/해제/확정 API 추가
- 예약 직접 생성 API 비활성화
- 사용자별 active hold 최대 3개 제한 적용
- hold 응답에 남은 TTL 포함
- Testcontainers Redis 테스트 작성 및 실행 확인 완료
- TTL 만료 시점 race condition 테스트 보강 완료
- 전체 기간/최근 N일 인기 음식점 조회 API 추가
- Spring Cache + Redis 기반 인기 음식점 캐시 적용
- Restaurant/Reservation 변경 시 인기 음식점 캐시 무효화 적용
- 인기 음식점 집계 테이블 및 Spring Batch Job 구성
- 전체 기간/최근 7일/30일/90일 인기 음식점 집계 저장
- 인기 음식점 API를 실시간 집계에서 집계 테이블 조회로 변경
- RabbitMQ 기반 비동기 예약 임시 점유 요청 API 추가
- 예약 임시 점유 요청 상태 저장 및 조회 API 추가
- RabbitMQ publisher/consumer/processor 구성
- Testcontainers RabbitMQ 기반 통합 테스트 작성
- 동기/비동기 예약 임시 점유 k6 부하 테스트 스크립트 추가
- 로컬 k6 성능 대시보드 추가

> 단계가 끝날 때마다 이 섹션을 갱신합니다. 다음 단계로 넘어가기 전까지 다른 단계의 작업을 시작하지 않습니다.

## 범위

도메인 정의, API 명세, 비즈니스 규칙은 이 문서에 포함하지 않습니다. 별도 문서로 분리해 이 문서가 비대해지지 않도록 합니다.
