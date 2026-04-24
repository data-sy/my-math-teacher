# Spec: `/audit-doc` 슬래시 커맨드 개선

**맥락:** `/audit-doc`의 첫 실사용(Milestone 1 감사) 결과 드러난 개선 지점 4가지를 반영하는 독립 변경. 독립 마일스톤이 아니며 단일 spec으로 처리
**브랜치:** `chore/improve-audit-doc-command`
**예상 Claude Code 세션:** 1회 (25~30분)
**선행 조건:** `.claude/commands/audit-doc.md` 존재 (최초 커맨드 추가 PR 머지 완료)

---

## 이 Spec의 목적

`/audit-doc`이 M1 문서 4개 감사에서 드러낸 네 가지 한계를 보완한다. 커맨드의 감사 정확도와 사용성을 높여 이후 모든 마일스톤의 사전 점검 효율을 개선.

### 반영할 개선 4가지 (audit 리포트 피드백)

1. **다중 문서 배치 감사 모드** — 여러 파일을 한 번에 감사하고 교차 참조 불일치를 자동 탐지
2. **플래그 가중치 규칙** — `[검증 필요]` 플래그 유무에 따른 심각도 조정
3. **경로 기반 타입 자동 판정** — 명확한 경로면 확인 없이 진행, 모호할 때만 확인
4. **산출물 경로 오탐 방지** — Task에서 생성 예정인 경로는 미존재 High로 잡지 않음

제외한 2가지(템플릿 블록 심각도 하향, 자기 검증 메타 단계)는 실사용 더 해본 뒤 판단.

## 전체 플로우 미리보기

```
Stage 1: 브랜치 준비          → main 최신화, 새 브랜치 생성
Stage 2: audit-doc.md 수정    → 기존 파일에 4가지 개선 반영
Stage 3: 로컬 검증 및 커밋    → 변경 확인 후 단일 커밋
Stage 4: 원격 푸시 및 PR      → push + PR 생성
Stage 5: 머지 후 정리         → main 복귀, 로컬 브랜치 삭제
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
   - `.claude/commands/` 하위의 다른 커맨드 파일(analyze-before-change, write-adr, review-pr) 수정

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
- clean이 아니면 **중단하고 사용자에게 보고**

2. main 전환 및 최신화:
```bash
git checkout main
git pull origin main
```

3. 기존 audit-doc.md 존재 확인:
```bash
ls .claude/commands/audit-doc.md
```

- 파일이 없으면 중단 (최초 커맨드 추가 PR이 미완료 상태)

4. 새 브랜치 생성 및 전환:
```bash
git checkout -b chore/improve-audit-doc-command
```

5. 브랜치 확인:
```bash
git branch --show-current
```

- 출력이 정확히 `chore/improve-audit-doc-command` 여야 함

### Stage 1 종료 조건
- 현재 브랜치가 `chore/improve-audit-doc-command`
- `.claude/commands/audit-doc.md` 존재 확인
- working tree가 clean

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 1 완료: 브랜치 준비 (chore/improve-audit-doc-command)

다음 Stage로 진행할까요?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Stage 2: audit-doc.md 수정

### 목표
기존 `.claude/commands/audit-doc.md`에 네 가지 개선을 반영한다. 완전히 새로 쓰지 말고 **기존 구조를 유지하면서 섹션을 추가·수정**할 것.

### 수행 명령

1. 현재 파일 전체 내용 읽기:
```bash
cat .claude/commands/audit-doc.md
```

현재 상태를 요약하여 사용자에게 보고. 이후 아래 수정 계획을 제시하고 승인 대기.

2. 승인 후 아래 네 가지 수정을 순서대로 적용. **각 개선을 위한 편집은 str_replace 또는 유사한 정밀 편집으로 수행. 전체 파일 재작성 금지**.

---

### 개선 1: 다중 문서 배치 감사 모드

**수정 대상 위치:** 파일 최상단의 "$ARGUMENTS ... 다음 순서로만 작업해줘" 직후

**추가할 섹션:** 새 "사전 단계" 섹션을 0단계 위에 추가

```markdown
## 사전 단계: 입력 파싱

$ARGUMENTS는 공백으로 구분된 하나 이상의 문서 경로일 수 있다.

- **단일 경로**: 기존 방식대로 0~5단계를 해당 문서에 적용
- **복수 경로**: 각 문서에 대해 0~5단계를 순차 실행하고, 모든 문서 감사 완료 후 추가로 "교차 참조 점검" 섹션을 생성

### 복수 경로 처리 규칙

1. 각 문서 감사 결과 사이에 구분선 출력:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   📋 파일 N/총개수 감사 완료: (파일명)
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```

2. 모든 개별 감사 완료 후 다음 항목을 점검하여 "🔗 교차 참조 점검" 섹션 생성:
   - 동일 클래스·메서드·경로에 대한 서로 다른 가정 (예: Repository 타입을 문서 A는 JPA로, 문서 B는 Reactive로 가정)
   - 피처 플래그·설정 key 네임스페이스 일관성
   - 선행 문서가 가정한 유틸·Bean을 후행 문서에서 만드는 선후 역전
   - 각 문서의 "선행 Spec" 또는 "의존성" 관계가 실제로 유효한지
   - 공유된 허위 가정 (존재하지 않는 메서드·필드가 여러 문서에 동시에 등장하는 경우)

3. 최종 전체 요약에 다음 포함:
   - 각 문서의 건강도를 표 형태로 집계
   - High 항목 클러스터 (중복되는 이슈는 그룹화)
   - 가장 먼저 수정해야 할 항목 Top 3 (우선순위 근거 포함)
   - 교차 참조 불일치 별도 섹션
```

---

### 개선 2: 플래그 가중치 규칙

**수정 대상 위치:** 기존 "3단계: 불일치 리포트"의 "심각도 기준" 부분

**변경 내용:** 심각도 기준 설명 아래에 다음 블록 추가

```markdown
### 플래그 가중치

항목에 `[검증 필요]` 플래그가 이미 붙어 있는 경우:
- **심각도 한 단계 하향** (High → Medium, Medium → Low, Low → Low 유지)
- 근거: 작성자가 이미 인지한 리스크이므로 발견 가치가 낮음

항목에 플래그가 없음에도 실제 코드베이스와 불일치가 발견된 경우:
- **심각도 한 단계 상향** (Low → Medium, Medium → High, High → High 유지)
- 근거: 작성자가 놓친 가정이므로 발견 가치가 높고 실행 시 실패 가능성도 큼

리포트의 "심각도" 컬럼에는 가중치 적용 **이후**의 최종 값을 기재하되, 비고에 원 심각도와 가중치 사유를 함께 표시:
- 예: "Medium (원래 High, 플래그 존재로 하향)"
- 예: "High (원래 Medium, 플래그 없음으로 상향)"
```

---

### 개선 3: 경로 기반 타입 자동 판정

**수정 대상 위치:** 기존 "0단계: 문서 타입 식별" 전체

**변경 내용:** 해당 섹션을 아래로 **교체**

```markdown
## 0단계: 문서 타입 식별

경로 기반으로 자동 판정하고, 명확한 경우 사용자 확인 없이 진행한다.

### 경로 규칙 (즉시 확정)

- `docs/milestones/` → 마일스톤
- `docs/specs/` → Spec
- `docs/adr/` → ADR
- 루트 또는 하위 디렉토리의 `CLAUDE.md` → AI 컨텍스트 문서
- 루트 또는 하위 디렉토리의 `README.md` → 프로젝트 개요 문서
- `docs/roadmap.md` → Roadmap
- `docs/benchmark/` → 벤치마크 리포트

위 규칙에 해당하는 경로는 **사용자 확인 없이** 해당 타입으로 확정하고 바로 1단계로 진행. 배치 감사 효율을 위해 매번 확인하지 않는다.

### 모호한 경우에만 확인 요청

위 규칙에 해당하지 않는 경로(예: 프로젝트 내 임의 위치의 .md 파일)에 대해서만:
- 파일 첫 몇 줄을 보고 타입 추정
- 추정 결과를 출력하고 사용자에게 확인 요청
- 승인 후 진행

### 추론한 타입을 리포트 상단에 명시

배치 모드의 각 문서 감사 시작 시, 추론된 타입을 한 줄로 출력:
- 예: "0단계: 경로 기반 자동 판정 → Spec 문서"
```

---

### 개선 5: 산출물 경로 오탐 방지

**수정 대상 위치:** 기존 "2단계: 실제 코드베이스 대조"의 "파일·경로" 항목 근처

**변경 내용:** "2단계" 끝부분에 다음 하위 규칙 추가

```markdown
### 산출물 경로 처리 규칙

문서에 언급된 파일·디렉토리 경로가 실제 미존재일 때, 무조건 불일치로 처리하지 않는다. 다음 판단 순서를 따른다:

1. 해당 경로가 문서 내 Task의 **산출물 섹션·"생성"·"신규"·"작성"·`mkdir`·`touch`** 등과 연관되어 있는가?
   - 예 → **Low**로 처리하고 비고에 "Task에서 생성 예정"이라고 기록
   - 아니오 → 다음 단계

2. 해당 경로가 문서에서 **참조·전제·입력**으로 언급되었는가? (존재해야 의미 있는 언급)
   - 예 → **High** (참조 대상이 없으면 Task 실행 불가)
   - 아니오 → **Medium** (네이밍 불일치 가능성)

예시:
- "`docs/benchmark/milestone-1-baseline.md` 작성 (Task 2.2 산출물)" 경로 미존재 → Low (생성 예정)
- "`api/build.gradle` 수정" 경로 미존재 → High (참조 대상 없음)
- "`shared/benchmark/`에 저장" + 해당 디렉토리 미존재 + Task 본문에 `mkdir -p shared/benchmark` 존재 → Low (생성 예정)
```

---

3. 모든 수정 적용 후 파일 최종 확인:

```bash
cat .claude/commands/audit-doc.md
wc -l .claude/commands/audit-doc.md
```

- frontmatter(`---`)는 그대로 유지되어야 함
- 기존 1~5단계 구조는 보존되어야 함
- 줄 수는 수정 전보다 늘어나야 함 (새 규칙이 추가되었으므로)

### Stage 2 종료 조건
- `.claude/commands/audit-doc.md`에 네 가지 개선이 모두 반영됨
- YAML frontmatter 보존
- 기존 1~5단계 섹션 존재 확인
- "사전 단계", 플래그 가중치, 경로 자동 판정, 산출물 규칙 네 블록 존재 확인

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 2 완료: audit-doc.md에 4가지 개선 반영

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
git diff .claude/commands/audit-doc.md
```

- 체크: **`.claude/commands/audit-doc.md` 단 하나의 수정된 파일만 있어야 함**
- 다른 파일이 보이면 중단하고 사용자에게 보고

2. 스테이징:
```bash
git add .claude/commands/audit-doc.md
```

3. 스테이징 상태 재확인:
```bash
git status
```

- "modified: .claude/commands/audit-doc.md" 단 한 줄만 나와야 함

4. 커밋. **메시지는 아래를 정확히 사용**:

```bash
git commit -m "$(cat <<'EOF'
chore: improve audit-doc slash command

- Add batch mode with cross-reference detection
- Weight severity by flag presence or absence
- Auto-detect doc type from path
- Prevent false positives for Task-generated paths

---

작업: /audit-doc 슬래시 커맨드 개선

- 다중 문서 배치 감사 + 교차 참조 자동 탐지 추가
- 검증 필요 플래그 유무에 따른 심각도 가중치 적용
- 경로 기반 문서 타입 자동 판정
- Task에서 생성 예정인 경로에 대한 오탐 방지
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
원격에 푸시하고 PR을 생성한다.

### 수행 명령

1. 푸시:
```bash
git push -u origin chore/improve-audit-doc-command
```

2. PR 생성 방식 판별:
```bash
which gh
```

**Case A — `gh` CLI가 있는 경우:**

```bash
gh pr create \
  --title "chore: improve audit-doc slash command" \
  --body "$(cat <<'EOF'
## 요약
`/audit-doc`의 첫 실사용(Milestone 1 감사) 결과 드러난 4가지 개선 지점 반영.

## 변경
`.claude/commands/audit-doc.md` 수정:
- 다중 문서 배치 감사 모드 + 교차 참조 자동 탐지
- 검증 필요 플래그 유무에 따른 심각도 가중치
- 경로 기반 문서 타입 자동 판정
- Task에서 생성 예정인 경로에 대한 오탐 방지

## 사용 예
\`\`\`
# 단일 문서 (기존과 동일)
/audit-doc docs/specs/m1/spec-01.md

# 배치 감사 + 교차 참조 자동 탐지 (신규)
/audit-doc docs/milestones/milestone-1.md docs/specs/m1/spec-01.md docs/specs/m1/spec-02.md docs/specs/m1/spec-03.md
\`\`\`

## 영향 범위
- 단일 문서 감사의 기존 동작 보존
- 경로가 명확한 문서는 사용자 확인 단계가 생략되어 더 빠름
- 리포트 정확도 향상 (플래그 가중치·오탐 방지)
EOF
)"
```

생성된 PR URL을 사용자에게 보고.

**Case B — `gh` CLI가 없는 경우:**

푸시 결과 출력에서 "Create a pull request" URL을 찾아 사용자에게 그대로 전달하고, **PR 생성은 사용자가 수동으로 진행**한다고 안내.

3. 사용자 확인 대기. **PR 머지까지 사용자가 직접 진행하므로 여기서 반드시 멈춘다.**

### Stage 4 종료 조건
- 원격 브랜치 `origin/chore/improve-audit-doc-command` 존재
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

3. audit-doc.md 수정 반영 확인:
```bash
grep -c "사전 단계" .claude/commands/audit-doc.md
grep -c "플래그 가중치" .claude/commands/audit-doc.md
```

- 두 grep 모두 1 이상이어야 함 (개선이 실제로 반영됨)

4. 로컬 브랜치 삭제:
```bash
git branch -d chore/improve-audit-doc-command
```

- `-d`가 실패하면 사용자에게 보고. `-D` 강제 삭제는 자동으로 쓰지 말 것

5. 원격 추적 브랜치 정리:
```bash
git fetch --prune
```

### Stage 5 종료 조건
- 현재 브랜치: `main`
- main에 커밋 머지 확인
- audit-doc.md의 4가지 개선이 반영된 상태 확인
- 로컬 feature 브랜치 삭제됨

### 종료 시 출력

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Stage 5 완료: 로컬 정리 완료

개선된 /audit-doc 사용 가능:
- 단일: /audit-doc docs/specs/m2/spec-01.md
- 배치: /audit-doc docs/milestones/milestone-2.md docs/specs/m2/spec-01.md docs/specs/m2/spec-02.md
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 전체 성공 기준

- [ ] Stage 1~5가 순차적으로, 각 사이에서 사용자 승인을 받으며 완료됨
- [ ] `.claude/commands/audit-doc.md`에 4가지 개선이 모두 반영됨
- [ ] 기존 섹션 구조(0~5단계)가 보존됨
- [ ] 로컬 feature 브랜치 정리됨
- [ ] 실사용 (다음 마일스톤 감사 시) 배치 모드·가중치·자동 판정이 기대대로 동작함

## 실패 시 대응

| Stage | 실패 상황 | 대응 |
|---|---|---|
| 1 | working tree dirty | 사용자에게 보고, stash/commit 판단 대기 |
| 1 | audit-doc.md 미존재 | 최초 커맨드 PR 미완료 상태. 중단 |
| 1 | main pull 충돌 | 중단, 사용자에게 보고 |
| 2 | 기존 섹션 누락 발견 | 기존 파일이 예상과 다름. 수정 방향 재논의 |
| 2 | str_replace 실패 | 해당 섹션 위치·문구가 예상과 다름. 사용자에게 현재 상태 보고 후 대응 |
| 3 | 의도하지 않은 파일 포함 | 중단, `git restore --staged` 제안 |
| 4 | 푸시 실패 | 원인 보고 (권한·원격 설정 등) |
| 5 | `-d` 삭제 실패 | 사용자에게 이유 보고, `-D` 강제 여부 확인 대기 |

## 금지 사항 (재강조)

- Stage를 건너뛰거나 묶어서 처리하지 말 것
- audit-doc.md 전체를 새로 작성하지 말 것 (기존 구조 유지하며 섹션 추가·수정만)
- 커밋 메시지를 임의로 변경하지 말 것
- PR을 자동 머지하지 말 것
- `.claude/commands/` 하위의 다른 커맨드 파일은 건드리지 말 것
- `git push --force`, `git reset --hard`, `-D` 강제 삭제 자동 실행 금지
