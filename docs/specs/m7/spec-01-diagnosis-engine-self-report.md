# spec-01 · 진단 엔진 — self-report + 그래프 적응형 문답 + 그래프 시급도 (백엔드)

**상위 마일스톤:** [Milestone 7](../../milestones/milestone-7-product-pivot.md) — 제품 피벗(자가진단 + 모바일 리디자인)
**대상:** 백엔드(서비스·리포지토리·API 계약). 프론트 React 구현은 spec-02, 링크 큐레이션 데이터는 spec-03.
**작업 브랜치:** 착수 시 `feat/m7-spec-01-diagnosis-engine` (본 설계 문서는 `feat/m7-product-pivot` 에 동반)
**상태:** 📝 **spec 합의 대기** → §7 결정 사인오프 후 코드 단계(`/analyze-before-change` → 구현)
**선행:** M2(완료) — `knowledge_space` 재귀 CTE(`JdbcTemplateConceptRepository.findPrerequisitesWithDepth`)가 새 엔진의 핵심. M7 결정 D1·D2·D3(milestone-7 §결정).

---

## 1. 범위

문제 풀 실부재 우회를 위해 진단을 **"문제 풀이·정오답 채점 → DKT 추론"** 에서 **"개념 self-report(알아요/몰라요) → 지식그래프 적응형 순회 → 그래프 기반 시급도"** 로 교체한다. 본 spec 은 그 **백엔드 엔진과 API 계약**에 한정한다.

### In
1. **self-report 문답 진행** — 개념 단위 "알아요/몰라요" 수집 API.
2. **그래프 적응형 순회** — "몰라요"→선수지식 drill-down, "알아요"→선수지식 skip (지식그래프를 테스트 엔진으로).
3. **그래프 기반 시급도 산출** — 막는 후수 개념 수·깊이·수렴도로 취약 개념 우선순위(DKT 퍼센트 대체).
4. **진단 결과 계약** — 프론트(spec-02)와 링크 큐레이션(spec-03)이 소비할 응답 형태 확정.
5. **DKT/TF Serving 파킹** — 정오답 시퀀스 경로를 플래그로 재움(삭제 X).

### Out (이번 범위 밖)
- **프론트 React 구현·문답 화면·모바일 셸** — spec-02.
- **개념별 외부 학습 링크 데이터·노출** — spec-03. 본 spec 은 결과 계약에 링크 부착 지점만 예약.
- **DKT 모델 재학습/self-report 전용 모델** — 파킹만. 실문항 확보 시 별도 마일스톤.
- **문항 이미지·`items` 풀 정리** — self-report 는 `items` 를 안 쓰므로 유휴화만, 삭제·마이그레이션은 후속.

---

## 2. 📌 데이터 모델 노트 — `knowledge_space` 방향성 (M2 spec-01 정의 준수)

> M2 spec-01 §데이터 모델 노트를 그대로 승계한다. 본 spec 의 모든 순회·SQL 이 이 정의를 기준으로 한다.

| 컬럼 | 의미 |
|---|---|
| `from_concept_id` | **후수** (학습 시간상 뒤) |
| `to_concept_id` | **선수** (학습 시간상 앞, 먼저 알아야 함) |

- **개념 C 의 선수지식** = `WHERE from_concept_id = C` → `to_concept_id` 들. (`findPrerequisitesWithDepth` 가 `pp.concept_id = ks.from_concept_id` 로 이 방향을 탄다.)
- **개념 P 를 막고 있는 후수(P 를 선수로 요구하는 개념)** = `WHERE to_concept_id = P` → `from_concept_id` 들. **시급도의 "막는 후수 수"가 이 방향.**

---

## 3. 코드 사실확인 (현행 진단 파이프라인 — 교체·보존 대상)

Explore 매핑(2026-07-11) 기준. 착수 시 `/analyze-before-change` 로 재검증.

| 접점 | 파일 | self-report 전환 시 |
|---|---|---|
| 제출 API | `AIController` `POST /api/v1/weakness-diagnosis` | **재계약** — item 단위 정오답 → concept 단위 알아요/몰라요 |
| 제출 DTO | `AnswerCreateRequest{userTestId, List<AnswerCodeCreateRequest{itemId, answerCode}>}` | **변경** — `itemId`→`conceptId`, `answerCode`(0/1)=몰라요/알아요 |
| 취약점 산출 | `ProbabilityService.create()` (오답 concept → `conceptService.findPrerequisitesAsDepthMap(cId,3)` → `probabilityList[skillId-1]` 매핑 → `probabilities` 저장) | **부분 교체** — 그래프 순회(`findPrerequisitesAsDepthMap`)는 **보존**, `probabilityList[skillId-1]`(DKT 인덱싱, L76 근방)은 **그래프 시급도로 교체** |
| DKT 입력 빌드 | `AnswerService.findAIInput()` + `AnswerConverter.convertToIntArray()` (`{skillId, answerCode}` 시퀀스·10배 증폭) | **파킹** — self-report 경로에서 미호출 |
| TF Serving 호출 | `ProbabilityService.getPrediction()` (`RestTemplate` → `mmt-ai:8501`) | **파킹** — 플래그 off |
| 저장 스키마 | `probabilities(answer_id, concept_id, to_concept_depth, probability_percent)` | **재사용** — `probability_percent` 자리를 시급도/known 로 재해석(§4.4, analyze-before-change) |
| 결과 조회 | `GET /api/v1/weakness-diagnosis/{userTestId}` → `ResultResponse`(`findResults`) | **계약 확장** — 시급도 필드 + 링크 부착 지점 |
| 맞춤 소비 | `ItemService.findPersonalItems` (`probabilities` 만 소비) | **무변경(엔진 관점)** — probabilities 가 채워지면 동작. 단 출력은 spec-03 링크로 대체 |

**핵심:** 그래프 순회(`knowledge_space` CTE)와 `probabilities` 저장/소비 골격은 살고, **DKT 결합 3곳**(`findAIInput`·`getPrediction`·`probabilityList[skillId-1]`)만 파킹/교체된다.

---

## 4. 설계

### 4.1 문답 진행 (self-report) — 진행 방식이 결정 D1

개념 1,631개를 다 못 물으므로 **영역 진입(학교급/단원)으로 진입점을 좁히고 그래프를 적응형으로 순회**한다. 진행 주체가 갈린다:

- **D1-A (권장) — 서버 주도 stateless 순회.** 클라이언트가 지금까지의 (conceptId→알아요/몰라요) 맵을 매 요청에 실어 보내면, 백엔드가 그래프로 **다음 물어볼 개념(들)** 또는 "종료"를 반환. 순회 로직이 백엔드(엔진)에 집중 = 본 spec 의도와 정합. 세션 저장소 불필요.
- **D1-B — 서버 세션 순회.** 진행 상태를 Redis/`userTest` 세션에 저장. 요청이 가벼우나 상태 수명주기·정합 부담.
- **D1-C — 클라이언트 순회.** 백엔드가 진입 subgraph 를 한 번에 내려주고 클라가 순회, 최종 맵만 제출. 스냅하나 순회 로직이 프론트로 새어 "엔진=백엔드" 정합이 깨짐.

→ **권장 D1-A.** 계약 초안:
```
POST /api/v1/diagnosis/next
  req  { entry: {schoolLevel, unitId?}, answered: [{conceptId, known}] }
  res  { next: [{conceptId, name, example}], done: false }   // 예시(example)는 spec-02 표시용, 데이터는 concepts
       { next: [], done: true, userTestId }                   // 종료 시 결과 확정 트리거
```
종료 시 answered 맵을 `answers`(concept 단위) + 순회 결과를 `probabilities` 로 확정 저장.

### 4.2 그래프 적응형 순회

- **진입 프론티어** = 선택 영역 subgraph 에서 **후수-최상위 개념**(그 영역에서 다른 개념의 선수로만 쓰이지 않는, 가장 상위 목표 개념)부터.
- **"몰라요"(known=false)** → 그 개념의 **선수지식으로 drill-down**(`from_concept_id=C`의 `to_concept_id`). 무너진 토대의 바닥을 찾음.
- **"알아요"(known=true)** → 그 개념의 **선수지식 가지 전체 skip**(선수는 안다고 가정) → 화면 수 급감.
- 순회는 M2 CTE(`findPrerequisitesWithDepth`) 재사용. depth 상한은 현행 3 유지(결정 시 재확인).

### 4.3 그래프 기반 시급도 (DKT 퍼센트 폐기) — 가중치가 결정 D2

각 "몰라요" 개념(=무너진 토대 후보)에 시급도 점수를 부여:

```
urgency(P) = w1 · blockedDescendants(P)   // P 를 선수로 요구하는 후수 개념 수 (to_concept_id=P → from)
           + w2 · foundationDepth(P)      // 근본성 (선수 사슬 깊이 / 낮은 학년일수록 ↑)
           + w3 · convergence(P)          // 사용자의 여러 '몰라요'가 P 로 수렴하는 정도
```

- **설명가능**: "이거 하나 막히면 위로 N개가 막혀 제일 급함" 을 사용자에게 그대로 노출 가능(블랙박스 % 대체).
- **출발 가중치**(예 w1·w2·w3 = 1·1·1, 정규화 방식 포함)는 **제안 출발값** — ResultView 40/65% 임계 선례처럼 **실데이터 분포로 후속 보정**(백로그). D2 에서 초기값·정규화 확정.
- 산출값은 `probabilities.probability_percent` 자리에 **정규화 시급도**로 저장(또는 컬럼 추가 — §4.4/analyze-before-change).

### 4.4 진단 결과 계약

`GET /api/v1/weakness-diagnosis/{userTestId}` 응답을 시급도순 취약 개념 리스트로 재구성:
- 필드: `conceptId/conceptName`, `urgency`(상/중/하 + 근거 수치 blockedDescendants 등), `toConceptDepth`, `level/chapter`, **`links`(spec-03 에서 부착, 이번엔 빈 배열 예약)**.
- **저장 스키마 결정(analyze-before-change 대상):** `probabilities.probability_percent` 재해석 vs 컬럼 추가(`urgency_score`). 재사용이 마이그레이션 0 이나 의미 혼동 위험 → 착수 시 결정.

### 4.5 DKT / TF Serving 파킹

- 플래그 `mmt.diagnosis.engine`(값: `self-report-graph`(기본) | `dkt`) 로 분기. self-report 경로에서 `findAIInput`·`getPrediction` 미호출.
- 컨벤션상 신규 영역이므로 `mmt.diagnosis.*` 를 api/CLAUDE.md 피처 플래그 섹션에 추가(ADR 경량 기록).
- TF Serving 컨테이너는 M6 배포에서 실서빙 유지(D1=4GB) 상태 — 파킹은 앱 레벨 분기만, 인프라 회수는 별도.

---

## 5. 왜 지금 이 순서(spec-01 먼저)

- 진단 엔진이 **프론트(spec-02)가 소비할 데이터 계약**을 정의 → 계약 먼저 굳혀야 React 화면을 헛돌지 않고 만든다.
- 엔진은 기존 백엔드·그래프 자산 위에 얹혀 **React 작업과 독립**으로 진행 가능(병렬화 여지).

---

## 6. 피처 플래그 · 롤백

- `mmt.diagnosis.engine` 로 신·구 진단 병행(즉시 롤백 = `dkt` 로 되돌림, 단 실문항 부재로 dkt 는 데모 한정).
- self-report 경로는 `items`/DKT 무의존이라 실패해도 그래프 순회까지만 degrade(fail-safe).

---

## 7. 결정 (사인오프 대기)

- **D1 — 문답 진행 방식:** A(서버 주도 stateless) / B(서버 세션) / C(클라 순회). **권장 A.**
- **D2 — 시급도 가중치·정규화:** 초기 w1/w2/w3 와 정규화(상/중/하 컷). 출발값 제안 후 실데이터 보정.
- **D3 — 시급도 저장:** `probability_percent` 재해석 vs `urgency_score` 컬럼 추가(마이그레이션 유무).
- **D4 — 제출 DTO 단위:** `conceptId` 직접 vs 개념당 대표 item 매핑 우회(기존 스키마 보존 정도).
- **D5 — 진입 영역 UX 계약:** 학교급만 vs 학교급+단원(진입 프론티어 크기·문답 길이에 직결, spec-02 와 접점).

---

## 8. Analyze-Before-Change 예고 (합의 후 착수 시 수행)

- **참조 지점 조사:** `ProbabilityService`(create/getPrediction)·`AnswerService`(findAIInput/findIds)·`AnswerConverter`·`AIController`·`AnswerCreateRequest`/`AnswerCodeCreateRequest`·`JdbcTemplateAnswerRepository`·`JdbcTemplateProbabilityRepository`·`ItemService`(소비자)·`probabilities` 스키마.
- **영향 테스트:** 진단/AI 경로 테스트, `*N1Test`, `ConceptServiceFeatureFlagTest`, probabilities/personal 관련.
- **롤백 시나리오:** 플래그 `mmt.diagnosis.engine=dkt` 복귀 + 스키마 재해석 시 데이터 호환.
- 분석 결과는 ADR(신규 `mmt.diagnosis.*` 영역 + 시급도 산정) 또는 PR 설명에 포함.

---

## 9. 검증 (착수 후)

- "알아요"가 선수 가지를 실제로 skip 해 문답 수가 줄어드는지(적응형 동작, 단위 테스트).
- 시급도 순위가 그래프 구조(막는 후수 수·깊이·수렴)를 정확히 반영하는지(Testcontainers + 실시드 subgraph).
- self-report 경로가 DKT/`items` 무의존으로 완주하는지(플래그 off 에서 TF Serving 미호출).
- 결과 계약이 spec-02 프론트·spec-03 링크 부착과 맞물리는지(계약 스냅샷).
