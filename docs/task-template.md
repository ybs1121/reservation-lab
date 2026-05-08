# task-template.md

작업 요청은 아래 형식을 따른다.

## 1. 목표
무엇을 구현하거나 수정할지 작성한다.

예시:
- WorkLog 생성 기능 추가
- User 삭제 정책 변경
- Project 상태값 검증 로직 수정

## 2. 변경 범위
영향받는 도메인과 주요 클래스 범위를 작성한다.

예시:
- User domain
- Project domain
- WorkLog domain
- 관련 entity / service / repository / dto

## 3. 수용 기준
반드시 만족해야 하는 조건을 작성한다.

예시:
- 존재하지 않는 User로 WorkLog를 생성할 수 없다
- 존재하지 않는 Project로 WorkLog를 생성할 수 없다
- ENDED 상태의 Project에는 WorkLog를 생성할 수 없다
- API 응답은 DTO를 사용한다
- 문서에 없는 상태값이나 정책은 임의로 추가하지 않는다

## 4. 테스트 기준
### 기본 원칙
- domain의 비즈니스 로직은 테스트를 작성한다
- 특별히 단순한 경우가 아니면 최소 3개 이상의 테스트 케이스를 작성한다
- controller, service 테스트는 영향 범위가 크거나 회귀 위험이 큰 경우에만 작성한다
- 단순 CRUD나 단순 위임 로직은 controller, service 테스트를 필수로 요구하지 않는다
- 테스트 메서드명은 한글과 언더스코어를 사용해 검증 의도를 드러낸다

### domain 테스트 기본 예시
- 정상 케이스 1개
- 실패/예외 케이스 1개
- 경계/정책 케이스 1개

예시:
- 정상적으로 WorkLog가 생성된다
- 존재하지 않는 User이면 생성에 실패한다
- ENDED 상태의 Project이면 생성에 실패한다

## 5. 구현 원칙
- 도메인 규칙은 domain 코드에 드러나도록 작성한다
- controller는 요청/응답 처리만 담당한다
- service는 유스케이스와 흐름을 담당한다
- entity를 API 응답으로 직접 반환하지 않는다
- 프론트 변경은 사용자가 명시적으로 요청한 경우에만 진행한다
- 문서에 없는 정책은 임의로 추가하지 않는다
- 정책이 모호하거나 비어 있으면 먼저 사용자에게 확인한다
- 사용자가 정책을 미정으로 두면 TODO 또는 결정 필요 항목으로 남긴다
- 사용자가 요청한 기능 중 정책 미정으로 제외되는 부분은 TODO로 남겨 누락 범위를 추적 가능하게 한다
- 단순 CRUD가 아니면 먼저 계획을 작성하고 사용자 확인 후 구현한다

## 6. 참고 문서
작업 전 아래 문서를 확인한다.

- README.md
- docs/project-structure.md
- docs/domain-rules.md
- docs/domain/*.md
- docs/known-pitfalls.md
- docs/api-policy.md
- AGENTS.md
