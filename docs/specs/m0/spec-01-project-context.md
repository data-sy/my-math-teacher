# Spec M0-01: 프로젝트 컨텍스트 문서화

**상위 마일스톤:** [Milestone 0](../../milestones/milestone-0-claude-code-integration.md)
**브랜치:** `chore/setup-claude-code-integration`
**예상 Claude Code 세션:** 1회 (45~60분)

---

## 이 Spec의 범위

모노레포 계층형 CLAUDE.md 세트를 작성하고, 개인용 파일을 공용 레포와 분리한다.

## 전제 조건

- 프로젝트 루트 구조: `ai/`, `api/`, `docs/`, `neo4j/`, `shared/`, `web/`, `docker-compose.yml`, `README.md`
- `api/`는 Spring Boot (Java 17, Gradle), `web/`는 Vue
- `ai/`·`neo4j/`는 현재 비활성 디렉토리 (모델 학습 결과물·초기 데이터 생성에만 사용됨)
- `shared/`는 프로젝트 자산 저장소 (다이어그램·스크립트·시드 데이터 등, 코드 아님)

## 작업 규칙

1. **Analyze-Before-Change 준수**: 파일 생성·수정 전 현재 상태를 요약하고 계획 승인 대기
2. **커밋 분리**: 각 Task 완료 시 단위로 커밋
3. **CLAUDE.md 크기 제약**: 각 파일 200줄 이내. 초과 시 `@` 참조로 외부 문서 분리
4. **금지**: CLAUDE.md에 API 키·비밀번호·실명·로컬 절대경로 포함 금지

---

## Task 1.1: 루트 CLAUDE.md 작성

**입력:**
- `/build.gradle` 또는 `api/build.gradle` (기술 스택 버전 확인)
- `/docker-compose.yml` (서비스 구성 파악)
- `/README.md` (기존 프로젝트 설명)

**작업 내용:**
프로젝트 루트에 `CLAUDE.md` 생성. 다음 템플릿을 골격으로 사용하되, 실제 값은 빌드 파일에서 추출하여 기재.

```markdown
# MMT - AI 기반 수학 튜터링 서비스

## 프로젝트 개요
(1~2문단으로 서비스가 무엇을 하는지. README.md 내용 기반으로 압축)

## 모노레포 구조

- `api/` — Spring Boot 기반 백엔드 서버 (활성 개발). 세부 규칙은 @api/CLAUDE.md 참조
- `web/` — Vue 기반 프론트엔드 (활성 개발). 세부 규칙은 @web/CLAUDE.md 참조
- `ai/` — DKT 모델 학습 스크립트 (비활성). 결과 모델은 TensorFlow Serving으로 배포됨. 현재 건드리지 않음
- `neo4j/` — 초기 데이터 생성 및 Docker Hub 이미지 빌드용 (비활성). 현재 건드리지 않음
- `shared/` — 프로젝트 자산 저장소 (다이어그램, 스크립트, 시드 데이터). 코드가 아닌 운영 자산
- `docs/` — 마일스톤·spec·ADR·roadmap

## 인프라

- `docker-compose.yml`로 MySQL·Redis·Neo4j·TF Serving 일괄 기동
- 로컬 개발 기동: `docker compose up -d`

## 작업 규칙 (전역)

- 모든 작업은 `docs/roadmap.md`의 활성 마일스톤 컨텍스트 안에서 진행
- 스키마 변경·마이그레이션·레이어 간 리팩토링은 반드시 **Analyze-Before-Change** 패턴 준수 (/analyze-before-change 커맨드)
- 중요한 의사결정은 ADR 작성. 위치: `docs/adr/`, 템플릿: `docs/adr/_template.md`
- 커밋은 Task 단위로 분리. 여러 Task를 하나의 커밋에 묶지 말 것

## 현재 활성 작업

- [Roadmap](docs/roadmap.md)
- 현재 진행 중: @docs/milestones/milestone-0-claude-code-integration.md

## 금지 사항

- `ai/`, `neo4j/` 디렉토리는 사용자의 명시적 지시 없이 수정하지 말 것
- `docker-compose.yml`의 서비스 구성은 ADR 없이 변경하지 말 것
- 공용 CLAUDE.md에 민감 정보·로컬 절대경로 포함 금지 (해당 내용은 CLAUDE.local.md로)
```

**산출물:**
- [ ] `/CLAUDE.md` (200줄 이내)

**검증:**
- [ ] `wc -l CLAUDE.md` ≤ 200
- [ ] 새 세션에서 `> 이 프로젝트의 모노레포 구조를 설명해줘` 입력 시 정확히 요약
- [ ] 새 세션에서 `> ai/ 디렉토리는 지금 어떤 상태야?` 입력 시 "비활성, 건드리지 않음"으로 답변

---

## Task 1.2: api/CLAUDE.md 작성

**입력:**
- `api/build.gradle` (Spring Boot 버전, 의존성)
- `api/src/main/java/` 최상위 패키지 구조
- 기존 `docs/adr/` 중 api 관련 ADR (JPA·JdbcTemplate·Neo4j 관련)

**작업 내용:**
`api/CLAUDE.md` 생성. 루트 CLAUDE.md를 참조하되, Spring 전용 규칙에 집중.

```markdown
# MMT API (Spring Boot)

루트 규칙은 @/CLAUDE.md 참조. 이 문서는 api/ 워크스페이스에만 적용되는 규칙.

## 기술 스택
- Java 17
- Spring Boot 3.x (build.gradle 실제 버전 기재)
- JPA + Hibernate (주 영속성)
- JdbcTemplate (레거시, JPA로 점진 전환 예정)
- Neo4j Reactive (마이그레이션 예정 — Milestone 2)
- MySQL 8, Redis

## 빌드 & 테스트

- 전체 빌드: `./gradlew build`
- 전체 테스트: `./gradlew test`
- 단일 테스트: `./gradlew test --tests "ClassName"`
- 컴파일만 체크: `./gradlew compileJava`

## 아키텍처

- 레이어: Controller → Service → Repository
- 주요 도메인: (src/main/java 실제 패키지 기반으로 3~5개 추출)
- 세부 패키지 구조는 @docs/architecture-api.md 참조 (필요 시 별도 작성)

## 영속성 레이어 규칙

- 신규 리포지토리는 JPA 사용 (JdbcTemplate 금지)
- 기존 JdbcTemplate 코드 수정 시:
  - 단순 수정은 현행 유지
  - 구조적 변경이 필요하면 JPA 전환을 함께 제안 (ADR 필요)
- 배치 삽입은 BatchPreparedStatementSetter 또는 JPA batch_size 설정 사용

## 테스트 규칙

- 통합 테스트는 Testcontainers 기반 (Milestone 1 완료 후 강제)
- @SpringBootTest 남용 금지, @DataJpaTest·@WebMvcTest 우선
- N+1 쿼리는 테스트로 검증 (QueryCountAssertions)

## 마이그레이션 규칙

- 스키마 변경·쿼리 구조 변경은 Analyze-Before-Change 필수
- 피처 플래그로 구버전·신버전 병행 가능한 구조 우선
- 롤백 시나리오가 없는 마이그레이션은 금지
```

**산출물:**
- [ ] `/api/CLAUDE.md` (200줄 이내)

**검증:**
- [ ] `api/` 디렉토리에서 Claude Code 세션 시작 후 `> 이 워크스페이스의 영속성 규칙 알려줘` 입력 시 정확 답변
- [ ] 루트 CLAUDE.md와 중복되는 전역 규칙이 재작성되지 않았는지 확인 (@ 참조만)

---

## Task 1.3: web/CLAUDE.md 작성

**입력:**
- `web/package.json` (프레임워크 버전, 의존성, 스크립트)
- `web/src/` 최상위 디렉토리 구조 (components, views, store 등)

**작업 내용:**
`web/CLAUDE.md` 생성.

```markdown
# MMT Web (Vue)

루트 규칙은 @/CLAUDE.md 참조. 이 문서는 web/ 워크스페이스에만 적용되는 규칙.

## 기술 스택
- Vue (package.json 실제 버전 기재)
- (상태 관리 라이브러리 — Pinia/Vuex 등 실제 확인)
- (빌드 도구 — Vite/webpack 등 실제 확인)

## 개발 명령

- 개발 서버: `npm run dev` (또는 package.json 실제 스크립트)
- 빌드: `npm run build`
- 린트: `npm run lint`
- 테스트: (설정되어 있다면 기재)

## 디렉토리 구조

(src/ 실제 구조 기반으로 기재)
- `components/` — 재사용 UI 컴포넌트
- `views/` — 라우트 단위 페이지
- `store/` — 상태 관리
- `api/` — 백엔드 API 클라이언트

## 코딩 컨벤션

- 컴포넌트 파일명: PascalCase
- props는 명시적 타입 선언
- API 호출은 `api/` 디렉토리 모듈을 통해서만 (view 내 직접 fetch 금지)

## 백엔드 연동

- API 베이스 URL은 환경변수로 주입 (`.env`)
- 실제 서버는 @/api/CLAUDE.md의 엔드포인트 규칙 참조
```

**산출물:**
- [ ] `/web/CLAUDE.md` (200줄 이내)

**검증:**
- [ ] package.json의 실제 스크립트와 일치
- [ ] `web/` 진입 후 `> dev 서버 어떻게 띄워?` 입력 시 정확 답변

---

## Task 1.4: CLAUDE.local.md 및 .gitignore 설정

**작업 내용:**

1. 프로젝트 루트에 `CLAUDE.local.md` 생성:

```markdown
# CLAUDE.local.md (개인용, gitignored)

## 로컬 환경
- (로컬 포트·경로 등 개인 설정)

## 실험 중인 방향
- (자유 기술)

## 세션 간 전달 메모
- (현재 진행 중인 Task의 중간 상태)
```

2. 프로젝트 루트 `.gitignore`에 다음 블록 추가 (기존 항목은 유지):

```gitignore
# Claude Code 개인 설정
CLAUDE.local.md
.claude/settings.local.json
.claude/*.local.*
```

3. `api/`, `web/`에도 각각 `.gitignore`가 있다면 동일 블록 추가는 **불필요** (루트 .gitignore가 하위 디렉토리까지 커버)

**산출물:**
- [ ] `/CLAUDE.local.md`
- [ ] `/.gitignore` 업데이트

**검증:**
- [ ] `git check-ignore CLAUDE.local.md` 실행 시 해당 파일명 출력
- [ ] `git status`에 `CLAUDE.local.md`가 Untracked로 나타나지 않음

---

## 전체 완료 체크리스트

- [ ] Task 1.1: 루트 CLAUDE.md 작성 및 커밋
- [ ] Task 1.2: api/CLAUDE.md 작성 및 커밋
- [ ] Task 1.3: web/CLAUDE.md 작성 및 커밋
- [ ] Task 1.4: CLAUDE.local.md + .gitignore 갱신 및 커밋
- [ ] 모든 CLAUDE.md 파일이 200줄 이내
- [ ] 새 세션에서 워크스페이스별 진입 시 해당 CLAUDE.md가 정확히 반영됨

## 다음 Spec

완료 후 [spec-02-workflow-and-commands.md](spec-02-workflow-and-commands.md) 진행.
