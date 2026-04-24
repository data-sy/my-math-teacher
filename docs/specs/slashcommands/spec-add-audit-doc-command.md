# Spec: `/audit-doc` 슬래시 커맨드 추가

**맥락:** Milestone 0 완료 후 별도 브랜치로 추가되는 재사용 가능한 커맨드. 독립 마일스톤이 아니며 단일 spec으로 처리
**브랜치:** `chore/add-audit-doc-command`
**예상 Claude Code 세션:** 1회 (15~20분)
**선행 조건:** Milestone 0이 main에 머지 완료된 상태

---

## 이 Spec의 목적

프로젝트 내 모든 문서(마일스톤·spec·ADR·README·CLAUDE.md 등)가 **현재 코드베이스와 일치하는지** 감사하는 슬래시 커맨드를 추가한다. 문서 작성 시점과 현 코드베이스 사이의 표류(drift)를 주기적으로 감지하고 갱신 제안을 받기 위한 재사용 도구.

**왜 필요한가:**
- 문서는 작성 시점의 코드베이스를 기준으로 작성되지만, 코드는 계속 진화함
- 오래된 가정이 담긴 문서를 기반으로 새 작업을 하면 실패로 이어짐 (특히 spec을 그대로 Claude Code에 던질 때)
- 사람이 일일이 문서와 코드를 대조하기는 비효율. 기계적 대조는 AI에게 맡기는 것이 적합

## 전체 플로우 미리보기

```
Stage 1: 브랜치 준비      → main 최신화, 새 브랜치 생성
Stage 2: 커맨드 파일 작성  → .claude/commands/audit-doc.md
Stage 3: 로컬 검증 및 커밋 → 변경 확인 후 커밋
Stage 4: 원격 푸시 및 PR   → push + PR 생성
Stage 5: 머지 후 정리      → main 복귀, 로컬 브랜치 삭제
```

## 실행 규칙 (모든 Stage 공통)

1. **단계 격리**: 각 Stage를 완료한 뒤 **반드시 멈추고 사용자 승인을 기다릴 것**. 연속 실행 금지
2. **Stage 경계 출력 형식**: 각 Stage 종료 시 아래 블록을 그대로 출력

   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✅ Stage N 완료: (완료한 내용 한 줄 요약)
   
   다음 Stage로 진행할까요?
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```

3. **사용자 승인 대기**: "다음" / "진행" / "go" 등 명시적 승인 전까지 다음 Stage 착수 금지
4. **실패 시 중단**: 어느 Stage든 실패하면 다음으로 넘어가지 말고 상황 보고
5. **금지 명령**:
   - `git push --force`
   - `git reset --hard`
   - `rm -rf`
   - 기존 `.claude/commands/` 하위 파일 수정·삭제

---

## Stage 1: 브랜치 준비

### 목표
main을 최신 상태로 맞추고, 작업용 새 브랜치를 생성한다.

### 수행 명령

1. 현재 상태 확인:
```bash
git status
```

- 체크: 변경 사항 없이 clean 상태여야 함
- clean이 아니면 **중단하고 사용자에게 보고** (stash·commit 등 판단은 사용자가)

2. main 전환 및 최신화:
```bash
git checkout main
git pull origin main
```

3. 새 브랜치 생성 및 전환:
```bash
git checkout -b chore/add-audit-doc-command
```

4. 브랜치 확인:
```bash
git branch --show-current
```

- 출력이 정확히 `chore/add-audit-doc-command` 여야 함

### Stage 1 종료 조건
- 현재 브랜치가 `chore/add-audit-doc-command`
- working tree가 clean

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 1 완료: 브랜치 준비 (chore/add-audit-doc-command)

다음 Stage로 진행할까요?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Stage 2: 커맨드 파일 작성

### 목표
`.claude/commands/audit-doc.md` 파일을 생성한다.

### 수행 명령

1. 디렉토리 존재 확인 (있어야 함, Milestone 0에서 생성됨):
```bash
ls .claude/commands/
```

- 기존 3개 커맨드(`analyze-before-change.md`, `write-adr.md`, `review-pr.md`)가 있는지 확인
- 디렉토리가 없으면 중단하고 사용자에게 보고 (Milestone 0 상태 이상)

2. `.claude/commands/audit-doc.md` 파일 생성. **파일 내용은 아래 블록을 정확히 그대로 기재**:

````markdown
---
description: 프로젝트 문서의 가정을 실제 코드베이스와 대조하여 갱신 필요 항목을 리포트
---

$ARGUMENTS (문서 경로)를 읽고, 다음 순서로만 작업해줘. 절대 순서를 건너뛰지 말 것:

## 0단계: 문서 타입 식별

경로·파일명·첫 몇 줄을 보고 문서 타입 추정:

- `docs/milestones/` 또는 제목에 "Milestone" → 마일스톤
- `docs/specs/` 또는 제목에 "Spec" → 스펙
- `docs/adr/` 또는 "ADR" → ADR
- `README.md` → 프로젝트 개요 문서
- `CLAUDE.md` → AI 컨텍스트 문서
- 기타 → 범용 문서로 처리

타입에 따라 2단계의 체크 항목 강도가 달라진다. 타입을 출력해서 사용자에게 확인받은 뒤 진행.

## 1단계: 문서 파싱

문서에서 다음 요소를 추출하여 리스트업:

- 파일·디렉토리 경로
- 패키지·모듈 경로
- 클래스·함수·어노테이션·메서드명
- 설정 key (예: application.yml의 `mmt.migration.xxx`)
- 명령어 (예: `./gradlew build`, `npm run dev`)
- 수치·지표 (예: `~5ms`, `200줄 이내`)
- 의존성·라이브러리명·버전
- DB 스키마·테이블·컬럼명
- 참조된 다른 문서 경로

## 2단계: 실제 코드베이스 대조

각 요소에 대해:

- 파일·경로: Glob으로 실제 존재 여부 확인
- 패키지·클래스: Grep으로 실제 정의 확인
- 어노테이션·타입: 실제 사용처와 일치하는지 확인 (JPA? Reactive? WebFlux?)
- 설정 key: application.yml·properties 파일에서 실제 존재 여부
- 명령어: build.gradle·package.json·Makefile 등에서 실제 존재하는 task/script인지
- 수치: 실측 근거가 있는지, 추측인지 판단. 실측 근거 없으면 "추측"으로 플래그
- 의존성: build.gradle·package.json에 실제 존재하는지, 버전이 일치하는지
- DB 스키마: 마이그레이션 파일·엔티티에서 실제 컬럼·테이블 확인
- 참조 문서: 링크된 경로가 실제 존재하는지

## 2.5단계: 문서 타입별 추가 체크

**마일스톤 문서:**
- 언급된 spec 파일이 실제로 존재하는가
- 완료 기준·산출물이 구체적이고 검증 가능한가
- 의존성 마일스톤이 실제 상태와 맞는가

**Spec 문서:**
- Task 내 "입력" 파일이 모두 존재하는가
- "산출물"이 구체 파일 경로로 명시되어 있는가
- "검증" 명령이 실제로 실행 가능한가
- 선행 Spec이 실제로 존재하는가

**ADR 문서:**
- Decision이 현재 코드에 실제로 반영되어 있는가
- 이후 이 결정을 뒤집는 ADR은 없는가 (docs/adr/ 전체 스캔)
- Status가 현재 실제 상황을 반영하는가 (Accepted인데 폐기된 경우 등)

**README / CLAUDE.md:**
- 빌드·실행 명령이 실제로 동작하는가
- 디렉토리 구조 설명이 현재와 일치하는가
- 기술 스택·버전이 실제와 일치하는가

## 3단계: 불일치 리포트

결과를 다음 표로 정리:

| # | 위치 | 문서의 내용 | 실제 상태 | 심각도 | 갱신 제안 |
|---|---|---|---|---|---|
| 1 | 라인 42 | @DataJpaTest | Neo4j Reactive 리포지토리 | High | @DataNeo4jTest로 교체 |
| 2 | 라인 78 | ~5ms | 실측 없음 (추측) | Medium | "Task X에서 실측 대체" 플래그 추가 |

심각도 기준:
- **High**: 문서에 따라 행동하면 실패·오류가 확실한 항목 (존재하지 않는 파일, 잘못된 명령, 불일치 어노테이션)
- **Medium**: 실행은 되지만 의미가 왜곡되는 항목 (추측 수치, 낡은 버전 표기, 사라진 참조)
- **Low**: 네이밍·포맷 불일치 등 사소한 정리 항목

## 4단계: 갱신 제안 요약

리포트 하단에 다음 형식으로 요약:

- High: N건
- Medium: N건
- Low: N건

다음에 할 일:
- High 항목은 즉시 수정 권장
- Medium 항목은 Task·Issue로 추적 등록 권장
- Low 항목은 여유 있을 때 정리

**실제 문서 수정은 수행하지 말 것**. 리포트만 제공하고, 사용자가 별도로 수정 지시를 내리면 그때 수행.

## 5단계: 전체 건강도 평가

마지막에 한 줄 평가:

- "Healthy": High 0건, Medium 3건 이하
- "Needs update": High 1~2건 또는 Medium 다수
- "Stale": High 3건 이상 (이 문서는 대대적 재작성 필요)

## 금지 사항

- 문서를 그대로 신뢰하지 말 것. 반드시 실제 코드와 대조
- 리포트 없이 사용자 요청이라며 문서를 직접 수정하지 말 것
- 추측 수치를 "실측 근거 있음"으로 간주하지 말 것
- 불일치를 발견하고도 심각도를 낮게 보고하지 말 것
````

3. 생성된 파일 검증:
```bash
ls -la .claude/commands/audit-doc.md
wc -l .claude/commands/audit-doc.md
head -5 .claude/commands/audit-doc.md
```

- 파일 존재, 줄 수 합리적(70줄 이상), YAML frontmatter(`---` 시작) 확인

### Stage 2 종료 조건
- `.claude/commands/audit-doc.md` 파일 존재
- 파일 시작이 `---` 으로 시작 (YAML frontmatter)
- 본문에 "0단계"~"5단계" 섹션 모두 존재

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 2 완료: audit-doc.md 생성

다음 Stage로 진행할까요?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Stage 3: 로컬 검증 및 커밋

### 목표
변경 사항이 의도대로 되어 있는지 확인하고, 단일 커밋으로 기록한다.

### 수행 명령

1. 변경 사항 확인:
```bash
git status
git diff --stat
```

- 체크: **`.claude/commands/audit-doc.md` 단 하나의 신규 파일만 있어야 함**
- 다른 파일이 보이면 중단하고 사용자에게 보고

2. 스테이징:
```bash
git add .claude/commands/audit-doc.md
```

3. 스테이징 상태 재확인:
```bash
git status
```

- "new file: .claude/commands/audit-doc.md" 단 한 줄만 나와야 함

4. 커밋 메시지 작성 및 커밋. **메시지는 아래를 정확히 사용**:

커밋 명령 (heredoc 사용):
```bash
git commit -m "$(cat <<'EOF'
chore: add audit-doc slash command

- Add /audit-doc to check project docs against actual codebase
- Detect drift between written assumptions and current state
- Report findings with severity levels instead of auto-modifying

---

작업: /audit-doc 슬래시 커맨드 추가

- 프로젝트 문서와 실제 코드베이스의 정합성을 점검하는 커맨드 추가
- 문서에 담긴 가정과 현 코드 상태 간의 표류를 감지
- 자동 수정 대신 심각도 기반 리포트만 생성
EOF
)"
```

5. 커밋 확인:
```bash
git log -1 --stat
```

### Stage 3 종료 조건
- 단일 커밋이 생성됨
- 커밋에 포함된 파일은 `.claude/commands/audit-doc.md` 하나뿐
- working tree가 clean

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 3 완료: 로컬 커밋 생성

다음 Stage로 진행할까요?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Stage 4: 원격 푸시 및 PR

### 목표
원격에 푸시하고 PR을 생성(또는 생성 URL 안내)한다.

### 수행 명령

1. 푸시:
```bash
git push -u origin chore/add-audit-doc-command
```

2. PR 생성 방식 판별:
```bash
which gh
```

**Case A — `gh` CLI가 있는 경우:**

```bash
gh pr create \
  --title "chore: add audit-doc slash command" \
  --body "$(cat <<'EOF'
## 요약
프로젝트 문서(마일스톤·spec·ADR·README 등)가 현 코드베이스와 일치하는지 감사하는 슬래시 커맨드 추가.

## 변경
- `.claude/commands/audit-doc.md` 신규 추가

## 사용 예
\`\`\`
/audit-doc docs/milestones/milestone-1-test-infrastructure.md
/audit-doc CLAUDE.md
/audit-doc docs/adr/0005-introduce-redis-caching.md
\`\`\`

## 영향 범위
- 기존 동작에 영향 없음 (신규 커맨드 단일 추가)
- 문서 타입(마일스톤·spec·ADR·README·CLAUDE.md)별로 체크 항목이 다르게 적용됨
- 자동 수정은 하지 않고 리포트만 생성
EOF
)"
```

생성된 PR URL을 사용자에게 보고.

**Case B — `gh` CLI가 없는 경우:**

푸시 결과 출력에서 "Create a pull request" URL을 찾아 사용자에게 그대로 전달하고, **PR 생성은 사용자가 수동으로 진행**한다고 안내.

3. 사용자 확인 대기. **PR 머지까지 사용자가 직접 진행하므로 여기서 반드시 멈춘다.**

### Stage 4 종료 조건
- 원격 브랜치 `origin/chore/add-audit-doc-command` 존재
- PR URL이 사용자에게 전달됨

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 4 완료: 원격 푸시 및 PR 준비

PR을 머지한 뒤 "머지 완료"라고 알려주세요.
그 후 Stage 5(로컬 정리)를 진행합니다.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Stage 5: 머지 후 정리

### 목표
PR 머지가 완료된 후 로컬을 정리한다.

**시작 전제 조건**: 사용자가 "머지 완료" 또는 동등한 표현으로 확인해준 뒤에만 진행.

### 수행 명령

1. main 전환 및 최신화:
```bash
git checkout main
git pull origin main
```

2. 머지 확인:
```bash
git log -1 --oneline
```

- 방금 작업한 커밋이 main에 포함되어 있어야 함

3. 파일 존재 재확인 (main 기준):
```bash
ls .claude/commands/audit-doc.md
```

4. 로컬 브랜치 삭제:
```bash
git branch -d chore/add-audit-doc-command
```

- `-d`가 실패하면 (머지 감지 실패 등) 사용자에게 보고. `-D` 강제 삭제는 자동으로 쓰지 말 것

5. 원격 추적 브랜치 정리:
```bash
git fetch --prune
```

### Stage 5 종료 조건
- 현재 브랜치: `main`
- main에 커밋 머지 확인
- `.claude/commands/audit-doc.md` 파일 존재
- 로컬 feature 브랜치 삭제됨

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 5 완료: 로컬 정리 완료

전체 작업 완료. 이제 다음 명령을 사용할 수 있습니다:
/audit-doc docs/milestones/milestone-1-test-infrastructure.md
/audit-doc CLAUDE.md
/audit-doc docs/adr/NNNN-some-decision.md
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 전체 성공 기준

- [ ] Stage 1~5가 순차적으로, 각 사이에서 사용자 승인을 받으며 완료됨
- [ ] main 브랜치에 `.claude/commands/audit-doc.md` 존재
- [ ] 로컬 feature 브랜치 정리됨
- [ ] 새 세션에서 `/audit-doc` 커맨드 자동완성에 노출됨 (선택적 확인)

## 실패 시 대응

| Stage | 실패 상황 | 대응 |
|---|---|---|
| 1 | working tree dirty | 사용자에게 보고, stash/commit 판단 대기 |
| 1 | main pull 충돌 | 중단, 사용자에게 보고 |
| 2 | `.claude/commands/` 부재 | Milestone 0 상태 이상. 중단 후 사용자 확인 |
| 3 | 의도하지 않은 파일 포함 | 중단, `git restore --staged` 제안 |
| 4 | 푸시 실패 | 원인 보고 (권한·원격 설정 등) |
| 5 | `-d` 삭제 실패 | 사용자에게 이유 보고, `-D` 강제 여부 확인 대기 |

## 금지 사항 (재강조)

- Stage를 건너뛰거나 묶어서 처리하지 말 것
- 커밋 메시지를 임의로 변경하지 말 것 (위 메시지를 그대로 사용)
- PR을 자동 머지하지 말 것 (머지는 사용자가 직접)
- `git push --force`, `git reset --hard`, `-D` 강제 삭제 자동 실행 금지
