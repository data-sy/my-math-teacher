# Milestone 0: Claude Code 통합 환경 구축

**브랜치:** `chore/setup-claude-code-integration`
**예상 소요:** 1.5~2일
**의존성:** 없음
**위험 수준:** 매우 낮음

---

## 목표

AI 없이 개발되어 온 MMT 모노레포에 Claude Code를 도입하기 위한 기반을 구축합니다. 이후 마일스톤(Neo4j → MySQL 마이그레이션, JPA 전환 등)에서 Claude Code가 각 워크스페이스(`api/`, `web/`)의 컨텍스트를 정확히 분리해 이해하고, 일관된 워크플로우·가드레일로 동작하도록 문서·설정·슬래시 커맨드를 마련합니다.

**이 마일스톤이 먼저인 이유:**
- CLAUDE.md 없이 Claude Code를 돌리면 세션마다 컨텍스트를 재설명해야 하고, 워크스페이스별 컨벤션이 섞임
- 모노레포에서 루트 단일 CLAUDE.md만 두면 Spring 작업 중 Vue 규칙이, Vue 작업 중 Spring 규칙이 끼어드는 오염이 발생
- "Analyze-Before-Change" 가드레일이 없으면 위험도 높은 마이그레이션에서 즉시 롤백 판단이 어려움
- 슬래시 커맨드·훅이 준비되어 있어야 반복 작업(ADR 생성·셀프 PR 리뷰 등)을 자동화할 수 있음

---

## 모노레포 구조와 CLAUDE.md 전략

```
mmt/
├── CLAUDE.md                  ← 루트: 모노레포 개요, 전역 규칙
├── CLAUDE.local.md            ← 개인 (gitignored)
├── docker-compose.yml
├── README.md
│
├── docs/                      ← 읽는 문서
│   ├── roadmap.md
│   ├── milestones/
│   ├── specs/
│   └── adr/
│
├── shared/                    ← 프로젝트 자산 (다이어그램, 스크립트, 시드 데이터)
│
├── api/
│   └── CLAUDE.md              ← Spring 전용
│
├── web/
│   └── CLAUDE.md              ← Vue 전용
│
├── ai/                        ← 비활성 (DKT 모델 학습 용도, 루트에 명시)
└── neo4j/                     ← 비활성 (초기 데이터 생성용, 루트에 명시)
```

**CLAUDE.md를 두는 기준:** 활성 개발이 이루어지는 워크스페이스에만 별도 CLAUDE.md 배치. `ai/`·`neo4j/`는 비활성 레거시이므로 루트 CLAUDE.md에서 "어떤 디렉토리이고 현재 건드리지 않는다"만 명시. `shared/`는 코드가 아닌 자산 저장소이므로 CLAUDE.md 불필요, 루트에서 설명.

**각 CLAUDE.md 크기 목표:** 파일당 **200줄 이내**. 세부 내용은 `@` 참조로 외부 문서에 연결해 지연 로딩 유도.

---

## 포함된 Spec

이 마일스톤은 논리 단위로 세 개의 spec으로 분할됩니다. 세션을 spec 단위로 끊어 실행하세요.

1. [`spec-01-project-context.md`](../specs/m0/spec-01-project-context.md) — CLAUDE.md 계층 작성, 개인 파일 분리, gitignore
2. [`spec-02-workflow-and-commands.md`](../specs/m0/spec-02-workflow-and-commands.md) — `.claude/` 설정, 슬래시 커맨드 3종
3. [`spec-03-guardrails-and-verification.md`](../specs/m0/spec-03-guardrails-and-verification.md) — Analyze-Before-Change 규칙 명시, ADR 템플릿, 통합 검증

---

## 완료 기준

- [ ] Claude Code가 새 세션에서 루트 CLAUDE.md 기반으로 프로젝트를 정확히 요약
- [ ] `api/`, `web/` 각 워크스페이스에서 진입 시 해당 디렉토리의 CLAUDE.md가 로드됨을 확인
- [ ] 슬래시 커맨드 3종(`/analyze-before-change`, `/write-adr`, `/review-pr`) 정상 동작
- [ ] 개인 설정·실험 메모가 공용 레포와 분리되어 gitignore 처리됨
- [ ] "Analyze-Before-Change" 규칙이 루트 CLAUDE.md에 명시되어 이후 마일스톤에서 강제됨
- [ ] 각 CLAUDE.md 파일이 200줄 이내 유지

## 산출물

- 프로젝트 컨텍스트 문서 세트 (루트 + `api/` + `web/` CLAUDE.md)
- 개인용 파일 분리 (CLAUDE.local.md, `.claude/settings.local.json`)
- Claude Code 워크플로우 설정 (`.claude/settings.json`, `.claude/commands/`)
- ADR 템플릿 정비
- 이후 마일스톤에서 재사용 가능한 AI 협업 베이스라인

---

## 이후 마일스톤과의 관계

- **Milestone 1 (테스트 인프라)** — 이 마일스톤에서 만든 `api/CLAUDE.md`의 테스트 컨벤션 위에서 Testcontainers·성능 기준선 작업이 진행됨. Analyze-Before-Change 패턴이 build.gradle 수정 시 적용됨.
- **Milestone 2 (Neo4j → MySQL 마이그레이션)** — 피처 플래그 구조와 마이그레이션 규칙이 루트 CLAUDE.md에 선언되어 있어야 함. 슬래시 커맨드 `/analyze-before-change`가 그래프 쿼리 리팩토링의 진입점.
- **Epic: JdbcTemplate → JPA 전환** — `api/CLAUDE.md`의 영속성 레이어 컨벤션이 전환 기준이 됨.
