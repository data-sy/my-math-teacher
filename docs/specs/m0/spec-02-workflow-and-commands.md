# Spec M0-02: Claude Code 워크플로우 및 슬래시 커맨드

**상위 마일스톤:** [Milestone 0](../../milestones/milestone-0-claude-code-integration.md)
**브랜치:** `chore/setup-claude-code-integration`
**예상 Claude Code 세션:** 1회 (30~45분)
**선행 Spec:** [spec-01-project-context.md](spec-01-project-context.md) 완료 필수

---

## 이 Spec의 범위

Claude Code의 공용·개인 설정 파일과 반복 작업을 자동화할 슬래시 커맨드를 작성한다.

## 작업 규칙

1. `.claude/settings.json`은 팀 공유용이므로 민감 정보 포함 금지
2. 개인 설정은 `.claude/settings.local.json`으로 분리 (Spec 01에서 gitignore 처리됨)
3. 슬래시 커맨드는 모노레포 전체에서 유효하도록 루트 `.claude/commands/`에 배치

---

## Task 2.1: .claude/settings.json 작성 (공용)

**작업 내용:**
프로젝트 루트에 `.claude/settings.json` 생성.

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew test*)",
      "Bash(./gradlew build)",
      "Bash(./gradlew compileJava*)",
      "Bash(cd api && ./gradlew*)",
      "Bash(npm run dev*)",
      "Bash(npm run build*)",
      "Bash(npm run lint*)",
      "Bash(cd web && npm*)",
      "Bash(git diff*)",
      "Bash(git log*)",
      "Bash(git status)",
      "Bash(git check-ignore*)",
      "Bash(docker compose ps)",
      "Bash(docker compose logs*)"
    ],
    "deny": [
      "Bash(./gradlew clean)",
      "Bash(rm -rf*)",
      "Bash(git push --force*)",
      "Bash(docker compose down -v*)"
    ]
  }
}
```

**산출물:**
- [ ] `/.claude/settings.json`

**검증:**
- [ ] Claude Code 재기동 후 `./gradlew test` 실행 시 추가 승인 없이 통과
- [ ] `./gradlew clean` 시도 시 승인 요청됨
- [ ] `docker compose down -v`(볼륨 삭제) 시도 시 차단됨

---

## Task 2.2: .claude/settings.local.json 템플릿 생성 (개인)

**작업 내용:**
`.claude/settings.local.json`을 빈 템플릿으로 생성. gitignore 처리는 Spec 01에서 완료된 상태.

```json
{
  "permissions": {
    "allow": []
  }
}
```

**산출물:**
- [ ] `/.claude/settings.local.json` (gitignored)

**검증:**
- [ ] `git status`에서 tracked 되지 않음
- [ ] `git check-ignore .claude/settings.local.json` 출력 확인

---

## Task 2.3: 슬래시 커맨드 3종 작성

**작업 내용:**
`.claude/commands/` 디렉토리 생성 후 세 파일 작성.

### `.claude/commands/analyze-before-change.md`

```markdown
---
description: 코드 변경 전 영향 범위를 먼저 분석
---

다음 순서로만 작업해줘. 절대 순서를 건너뛰지 말 것:

1. $ARGUMENTS 에 대한 변경을 시작하기 전에 먼저 다음을 분석:
   - 이 코드를 참조하는 모든 호출 지점 (Grep 필수)
   - 영향받는 테스트 파일 목록
   - 스키마·DB·마이그레이션 영향 여부
   - 관련 ADR 존재 여부 (docs/adr/ 검색)
   - 워크스페이스 간 영향 (api 변경이 web 계약에 영향을 주는지 등)

2. 분석 결과를 다음 형식으로 요약:
   - 영향 범위: ...
   - 위험도: (낮음/중간/높음)
   - 제안하는 변경 단계: 1) ... 2) ... 3) ...
   - 롤백 시나리오: ...

3. 내가 "진행해" 또는 "go"라고 승인한 후에만 실제 코드 변경 시작

절대 분석 없이 바로 코드를 수정하지 말 것.
```

### `.claude/commands/write-adr.md`

```markdown
---
description: ADR 초안 작성
---

다음 작업 수행:

1. docs/adr/ 디렉토리에서 가장 최근 ADR 번호 확인 (ls 또는 find 사용)
2. 다음 번호로 새 ADR 파일 생성:
   - 파일명: NNNN-$ARGUMENTS-kebab-case.md (한국어 주제는 영어 슬러그로 변환)
   - 경로: docs/adr/
3. docs/adr/_template.md 존재 시 해당 템플릿 사용. 없으면 다음 섹션 포함:
   - # ADR NNNN: (제목)
   - ## Status (Proposed)
   - ## Context
   - ## Decision
   - ## Consequences (Positive / Negative / Neutral)
   - ## Alternatives Considered

4. 주제: $ARGUMENTS
5. Context 섹션은 "[사용자 입력 대기 — 결정을 유발한 배경을 채워주세요]"로 둘 것
6. Status는 항상 "Proposed"로 시작 (내가 승인하면 수동으로 Accepted로 변경)
```

### `.claude/commands/review-pr.md`

```markdown
---
description: 현재 브랜치 diff를 셀프 리뷰
---

다음 순서로 셀프 리뷰 수행:

1. `git diff main...HEAD` 실행 (main이 없으면 master 또는 기본 브랜치 확인)
2. 변경된 파일 목록을 카테고리별 분류:
   - source (api/, web/, ai/ 하위 코드)
   - test
   - config (build.gradle, package.json, .claude/, docker-compose.yml 등)
   - docs (CLAUDE.md, docs/ 하위)
   - assets (shared/ 하위 — 다이어그램·스크립트·시드 데이터)

3. 다음 관점에서 점검:
   - 로직 오류·엣지 케이스 누락
   - 테스트 커버리지 (신규 코드에 대응하는 테스트 존재 여부)
   - CLAUDE.md 컨벤션 위반 (해당 워크스페이스 기준)
   - 성능 회귀 가능성
   - 민감 정보 커밋 여부 (API 키·패스워드·토큰)
   - 워크스페이스 경계 위반 (api가 web 내부를 직접 참조하는 등)

4. 결과를 다음 형식으로 출력:
   ## 요약 (변경 파일 수, 라인 수)
   ## 카테고리별 변경
   ## 지적 사항 (우선순위순: Critical / Major / Minor)
   ## 누락된 테스트
   ## 제안
```

**산출물:**
- [ ] `/.claude/commands/analyze-before-change.md`
- [ ] `/.claude/commands/write-adr.md`
- [ ] `/.claude/commands/review-pr.md`

**검증:**
- [ ] 새 세션에서 `/analyze-before-change ConceptRepository` 입력 시 분석부터 시작하고 승인 대기
- [ ] `/write-adr MySQL CTE 도입 결정` 입력 시 올바른 번호로 파일 생성 (상태가 Proposed)
- [ ] `/review-pr` 입력 시 지정 형식으로 출력

---

## 전체 완료 체크리스트

- [ ] Task 2.1: .claude/settings.json 작성 및 커밋
- [ ] Task 2.2: .claude/settings.local.json 템플릿 생성 (gitignored 확인)
- [ ] Task 2.3: 슬래시 커맨드 3종 작성 및 커밋
- [ ] 허용·차단 리스트가 의도대로 동작하는지 실제 명령 실행으로 확인

## 다음 Spec

완료 후 [spec-03-guardrails-and-verification.md](spec-03-guardrails-and-verification.md) 진행.
