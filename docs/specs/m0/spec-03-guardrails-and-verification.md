# Spec M0-03: 가드레일 규칙 명시 및 통합 검증

**상위 마일스톤:** [Milestone 0](../../milestones/milestone-0-claude-code-integration.md)
**브랜치:** `chore/setup-claude-code-integration`
**예상 Claude Code 세션:** 1회 (30분)
**선행 Spec:** [spec-01-project-context.md](spec-01-project-context.md), [spec-02-workflow-and-commands.md](spec-02-workflow-and-commands.md) 완료 필수

---

## 이 Spec의 범위

앞선 두 spec에서 만든 문서·설정을 기반으로 마이그레이션 가드레일 규칙을 명시하고, ADR 템플릿을 정비한 뒤, 전체 통합 검증을 수행한다.

## 작업 규칙

1. 이 spec은 선행 spec의 산출물 위에서 동작하므로, Task 1부터 순서대로 실행할 것
2. 검증 시나리오에서 실패가 나오면 해당 선행 spec으로 돌아가 수정

---

## Task 3.1: Analyze-Before-Change 규칙을 루트 CLAUDE.md에 추가

**입력:**
- Spec 01에서 작성된 `/CLAUDE.md`

**작업 내용:**
루트 CLAUDE.md의 "작업 규칙 (전역)" 섹션 하단에 다음 블록을 추가. 200줄 한계를 넘지 않도록 주의 (넘으면 `@docs/guardrails.md` 같은 외부 파일로 분리).

```markdown
## 마이그레이션·스키마 변경 규칙

모든 마이그레이션·스키마 변경·레이어 간 리팩토링은 "Analyze-Before-Change" 패턴을 준수한다:

1. 변경 대상 코드의 모든 참조 지점을 먼저 조사
2. 영향받는 테스트 목록 작성
3. 롤백 시나리오 명시
4. 분석 결과를 ADR 또는 PR 설명에 포함
5. 이후 실제 변경 착수

이 규칙은 `/analyze-before-change` 슬래시 커맨드로 강제된다.

## 피처 플래그 정책

위험도 중간 이상의 마이그레이션은 다음 조건을 만족해야 한다:
- 구버전·신버전 병행 가능한 구조 (피처 플래그 또는 조건 분기)
- 즉시 롤백 가능한 배포 단위
- 변경 전 성능 기준선 측정 완료 (Milestone 1 산출물)
```

**산출물:**
- [ ] `/CLAUDE.md`에 두 섹션 추가

**검증:**
- [ ] `wc -l CLAUDE.md` 여전히 200줄 이내
- [ ] 새 세션에서 `> 마이그레이션할 때 지켜야 할 규칙 알려줘` 입력 시 정확 답변

---

## Task 3.2: ADR 템플릿 정비

**입력:**
- 기존 `docs/adr/` 하위 ADR 17개 (스타일 파악용)

**작업 내용:**

1. `docs/adr/_template.md` 존재 여부 확인
2. **존재 시**: 기존 스타일과 Task 3.3의 `/write-adr` 커맨드 출력이 일치하는지 검토, 불일치 시 커맨드를 기존 템플릿에 맞춰 수정
3. **없으면**: 다음 내용으로 생성 후, 기존 ADR 17개 중 2~3개를 샘플링해 섹션 구조가 호환되는지 확인

```markdown
# ADR NNNN: (제목)

## Status
Proposed

## Context
(결정을 유발한 배경·제약·요구사항)

## Decision
(선택한 방향)

## Consequences

### Positive
- ...

### Negative
- ...

### Neutral
- ...

## Alternatives Considered

1. (대안 1) — 기각 이유
2. (대안 2) — 기각 이유

## References
- 관련 ADR: ADR-NNNN
- 관련 이슈·PR: (있으면 링크)
```

4. 기존 ADR 구조와 불일치하는 경우, 템플릿을 기존 스타일로 맞춘 뒤 Spec 02의 `/write-adr` 커맨드도 동일하게 수정

**산출물:**
- [ ] `/docs/adr/_template.md` (신규 또는 확인 완료)
- [ ] 필요 시 `/.claude/commands/write-adr.md` 갱신

**검증:**
- [ ] 기존 ADR 3개를 열어 템플릿과 섹션 구조 호환 확인
- [ ] `/write-adr 샘플 주제` 실행 시 템플릿 구조대로 파일 생성

---

## Task 3.3: 통합 검증 시나리오 수행

**작업 내용:**
Claude Code를 **새 세션**으로 시작 후 다음 프롬프트를 순서대로 실행. 각 결과를 체크리스트로 기록.

### 시나리오 A: 루트 컨텍스트 로딩

```
> 이 프로젝트의 모노레포 구조를 요약하고, 현재 진행 중인 마일스톤을 알려줘
```

**기대 동작:**
- 6개 디렉토리(ai/, api/, docs/, neo4j/, shared/, web/) 정확히 언급
- ai/·neo4j/를 "비활성"으로 분류
- shared/를 "코드가 아닌 자산 저장소"로 분류
- 현재 활성 마일스톤으로 M0·M1 링크

### 시나리오 B: 워크스페이스별 컨텍스트 전환

```
[프로젝트 루트에서 새 세션]
> api/ 안에서 테스트 어떻게 돌려?
```
**기대:** `./gradlew test` 답변, Testcontainers 언급

```
[새 세션]
> web/ 안에서 dev 서버 어떻게 띄워?
```
**기대:** package.json 기반 정확한 npm 스크립트 답변

### 시나리오 C: Analyze-Before-Change 작동

```
> /analyze-before-change api/src/main/java/.../ConceptRepository.java
```

**기대 동작:**
- 즉시 코드를 수정하지 않음
- Grep 등으로 참조 지점 조사
- 위험도·변경 단계·롤백 시나리오 요약
- "진행해" 승인 대기

### 시나리오 D: ADR 생성

```
> /write-adr MySQL CTE 도입 결정
```

**기대 동작:**
- 기존 ADR 번호 확인
- 다음 번호로 `docs/adr/NNNN-mysql-cte-adoption.md` 생성
- Status: Proposed
- Context 섹션: "[사용자 입력 대기...]"

### 시나리오 E: 셀프 리뷰

```
> /review-pr
```

**기대 동작:**
- 현재 브랜치(chore/setup-claude-code-integration)의 diff 분석
- 카테고리별 분류 (config·docs 중심)
- 민감 정보 커밋 여부 확인

### 시나리오 F: 개인 파일 분리 확인

```bash
git check-ignore CLAUDE.local.md .claude/settings.local.json
```

**기대:** 두 경로 모두 ignore 대상으로 출력

---

## 실패 대응

| 시나리오 실패 | 되돌아갈 위치 |
|---|---|
| A, B | Spec 01 Task 1.1~1.3 (CLAUDE.md 내용 검토) |
| C, D, E | Spec 02 Task 2.3 (슬래시 커맨드 문법·경로 검토) |
| D (템플릿) | Spec 03 Task 3.2 |
| F | Spec 01 Task 1.4 (.gitignore 확인) |

---

## Task 3.4: 브랜치 정리 및 PR 준비

**작업 내용:**
1. 커밋 이력 정리 — Task 단위로 분리되어 있는지 `git log --oneline` 확인
2. PR 설명 초안 작성 (GitHub/GitLab에 올릴 내용):

```markdown
## Milestone 0: Claude Code 통합 환경 구축

### 변경 요약
- 계층형 CLAUDE.md 도입 (루트 + api/ + web/)
- 슬래시 커맨드 3종 (analyze-before-change, write-adr, review-pr)
- Analyze-Before-Change 가드레일 규칙 명시
- 개인 설정 파일 gitignore 처리

### 검증 결과
- [x] 시나리오 A~F 모두 통과
- [x] 각 CLAUDE.md 200줄 이내
- [x] 기존 ADR 템플릿과 호환

### 관련 문서
- docs/milestones/milestone-0-claude-code-integration.md
- docs/specs/m0/spec-01~03
```

3. main 머지 후 Milestone 1 착수

**산출물:**
- [ ] 정리된 커밋 이력
- [ ] PR 초안

---

## 전체 완료 체크리스트

- [ ] Task 3.1: Analyze-Before-Change 규칙 CLAUDE.md에 추가
- [ ] Task 3.2: ADR 템플릿 정비 또는 확인
- [ ] Task 3.3: 시나리오 A~F 전부 통과
- [ ] Task 3.4: PR 초안 작성 및 커밋 이력 정리
- [ ] Milestone 0 전체 완료 기준 충족 확인 (상위 milestone 문서 체크리스트 참조)

---

## 이 마일스톤 종료 후

- Milestone 1 착수 — `@docs/milestones/milestone-1-test-infrastructure.md`
- 첫 Task는 반드시 `/analyze-before-change`로 시작하여 가드레일이 실제로 작동하는지 체감
