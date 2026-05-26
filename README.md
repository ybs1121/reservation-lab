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
- Redis
- Redisson
- H2 Database
- Lombok
- Gradle

## 실행 환경

- Group: `com.toy`
- Artifact: `reservation-lab`
- Java Toolchain: `21`

## 브랜치 전략

- `master` — 안정 브랜치
- `dev` — 작업 브랜치

## 현재 단계

**3단계 — 인기 음식점 조회 트래픽이 DB를 때리는 문제 해결 진행 중**

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

> 단계가 끝날 때마다 이 섹션을 갱신합니다. 다음 단계로 넘어가기 전까지 다른 단계의 작업을 시작하지 않습니다.

## 범위

도메인 정의, API 명세, 비즈니스 규칙은 이 문서에 포함하지 않습니다. 별도 문서로 분리해 이 문서가 비대해지지 않도록 합니다.
