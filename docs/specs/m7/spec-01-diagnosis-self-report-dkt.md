# spec-01 · 진단 — self-report→DKT 매핑 + 그래프 적응형 문답 + 추천 로직 (백엔드)

**상위 마일스톤:** [Milestone 7](../../milestones/milestone-7-product-pivot.md) — 제품 피벗(자가진단 + 모바일 리디자인). 밀스톤 D3-R·PRD §0.1 F-1~F-4 를 기술 설계로 구체화한다.
**대상:** 백엔드(서비스·리포지토리·API 계약). 프론트 React 구현은 spec-02, 외부 링크 데이터는 spec-03.
**작업 브랜치:** 착수 시 `feat/m7-spec-01-diagnosis` (본 설계 문서는 `feat/m7-product-pivot` 에 동반)
**상태:** ✅ **spec 확정(2026-07-13)** — **S2 = C안·S3 = A안 확정(리뷰)**, S1·S4~S6 = **권장안 잠정 채택(착수 시 재검토 꼬리표)** → 코드 단계(`/analyze-before-change` → 구현) 진입 가능
**선행:** M2(완료) — `knowledge_space` 재귀 CTE. PRD 사인오프(2026-07-13) — 특히 F-1(결과-시점 reverse gate)·F-2(localStorage answered-map)·F-3(스마트 default 진입)·F-4(통합 학습 큐·top-N '상' 등급).
**Supersedes:** 구 spec-01 초안 `spec-01-diagnosis-engine-self-report.md`(2026-07-11, **삭제됨 — git 히스토리 보존**). 구 초안은 시급도=그래프 점수(blockedDescendants·depth·수렴도 가중합으로 DKT 대체) 전제였으나 D3-R(DKT 유지)로 역전되어 본 문서가 전면 대체. 구 초안에서 살릴 내용(§2 데이터 모델 방향성 노트, D1-A stateless 순회 골격과 대안 기각 근거 §4.3)은 본문에 흡수 완료 — **본 문서만으로 자기완결**.

---

## 1. 범위

진단 입력을 **"문제 풀이·정오답 채점" → "개념 self-report OX(알아요/몰라요)"** 로 교체하되, 시급도 산출은 **기존 DKT(TF Serving)를 유지**한다: self-report 를 정오답(**안다=맞음 / 모른다=틀림**)으로 매핑해 기존 입력 시퀀스에 흘려넣는다(D3-R). 본 spec 은 그 **백엔드 엔진·API 계약·추천 로직**에 한정한다.

### In
1. **영역 진입(②) 데이터** — 스마트 default·±후보 pick-list 용 단원 조회 + 선택 단원의 **시작 프론티어** 산출 (F-3).
2. **그래프 적응형 문답(③)** — 서버 주도 stateless 순회 API: "몰라요"→선수지식 drill-down, "알아요"→선수 가지 skip (D1-A, PRD F-2 가 stateless 전제를 확정).
3. **self-report→DKT 재배선** — answered-map 을 정오답 시퀀스로 매핑해 `getPrediction`(TF Serving)→`probabilityList` 경로 재사용, `probabilities` 확정 저장. 시퀀스는 신규 `self_report_answers` 에서 직접 생성 — 구 `findAIInput`/`findBefore` 는 **미사용(무변경 보존)**.
4. **결과 계약(④)** — 헤드라인 + 개념 카드 top-N('상' 등급 컷) + 사람 언어 근거(blocked-descendants) + spec-03 links 부착 지점 예약.
5. **통합 학습 큐** — top-N 개념들의 공유 선수지식을 병합한 단일 계단(위상순 지배 정렬 + 시급도=진입 우선순위) + 재진입 스키마(완료 self-mark·이어가기) (F-4).
6. **결과-시점 게이트 배선** — 문답·결과는 비로그인 완주, 로그인 시 계정 귀속(저장·큐 생성) (F-1·F-2).

### Out (이번 범위 밖)
- **프론트 React 구현·화면** — spec-02. 본 spec 은 API 계약까지.
- **개념별 외부 링크 데이터·큐레이션** — spec-03. 결과 계약에 `links: []` 예약만.
- **DKT 재학습 / self-report 전용 모델** — 실문항 확보 시 별도 마일스톤. 기존 모델에 매핑 입력만(R1 감수, PRD §2.3 정직 프레이밍으로 방어).
- **`items` 풀 정리** — self-report 는 `items` 미사용 → 유휴화만(S2=C 확정으로 self-report 경로의 items 결합 없음).
- **구 진단 경로 제거** — `POST /api/v1/weakness-diagnosis` 등 기존 엔드포인트는 무변경 보존(롤백 대상).

---

## 2. 📌 데이터 모델 노트 — `knowledge_space` 방향성 (M2 정의 승계)

| 컬럼 | 의미 |
|---|---|
| `from_concept_id` | **후수** (학습 시간상 뒤) |
| `to_concept_id` | **선수** (먼저 알아야 함) |

- **개념 C 의 선수지식** = `WHERE from_concept_id = C` → `to_concept_id`. (`findPrerequisitesWithDepth` 가 `pp.concept_id = ks.from_concept_id` 로 이 방향을 탄다.)
- **개념 P 를 막고 있는 후수** = `WHERE to_concept_id = P` → `from_concept_id`. **카드 근거 문구("이걸 모르면 위로 N개가 막혀요")가 이 역방향** — 신규 역방향 CTE 필요(§4.5, S6).

---

## 3. 코드 사실확인 (2026-07-13 Explore 재검증 — 구 spec 2026-07-11 매핑 정정 포함)

| 접점 | 현행 (파일:라인) | 전환 시 |
|---|---|---|
| 제출 API | `AIController` `POST /api/v1/weakness-diagnosis` → `probabilityService.createAndPredict` (AIController.java:26-28). **인증 필수, 소유권(IDOR) 검사 없음** | **보존(롤백용)** — 신규 경로는 `/api/v1/diagnosis/*` 네임스페이스로 병행(§4.1) |
| 제출 DTO | `AnswerCreateRequest{userTestId, List<AnswerCodeCreateRequest{itemId(Long), answerCode(int)}>}` | 신규 DTO — `conceptId + known` (answered-map). item 단위 아님 |
| userTest 생성 | `TestController POST /{testId}` → `getMyUserIdWithAuthorities()` (인증 필수). `users_tests.user_id` = **필수 FK** → **익명 생성 경로 없음** | **S1 결정** — 익명은 무영속 preview, 로그인 시 귀속 저장(권장 S1-A) |
| answers 스키마 | `answers(answer_id, user_test_id, item_id→tests_items FK, answer_code)` (create.sql:138-146). ⚠️ 구 spec 은 FK 제약 미인지 | **구 `answers` 전면 무변경** — self-report 답안은 신규 `self_report_answers` 테이블(§4.4 DDL·§7 S2=C). 신규 경로는 구 answers 를 읽지도 쓰지도 않음 |
| DKT 입력 빌드 | `AnswerService.findAIInput`: `findBefore`(동일 유저 전체 이력) → utId 별 `findAnswerCode`(items→concepts 조인으로 `skill_id` 도출) → `[skillId, answerCode]` 쌍 **×10 증폭** (AnswerService.java:29-47, AnswerConverter.java:32-34) | **미재사용(무변경 보존)** — 신규 경로는 `self_report_answers` 에서 `[skill_id, answer_code]` 시퀀스 직접 생성, `findAIInput`/`findBefore` 무접촉. 이력 포함 여부 = **S3** |
| TF Serving 호출 | `ProbabilityService.getPrediction`: URL **하드코딩** `http://mmt-ai:8501/v1/models/my_model:predict`, `RestTemplate` 동기(타임아웃 미설정), 실패 시 **null 반환** → `createAndPredict` L47 에서 NPE 위험 (ProbabilityService.java:86-117) | **재사용** — 단 신규 경로에선 null 가드 + 타임아웃 필수(익명 preview 가 실패해도 학생 친화 에러로, ⑤). ⚠️ 구 서술 ".block() 호출"은 오류 — 동기 RestTemplate 이며 `.block()` 없음 |
| 시급도 인덱싱 | `probabilityList[skillId-1]` (1-based skill_id, ProbabilityService.java:76). skill_id = `concepts.skill_id` 컬럼(조회: `findSkillIdByConceptId`, 미존재 시 **-1 반환** → 인덱스 가드 없음) | **재사용** — skill_id=-1/범위 초과 가드 추가 |
| 취약 확장 | `create()`: 오답(depth0) 개념마다 `findPrerequisitesAsDepthMap(cId, 3)` → depth 1~3 선수 확장 → `probabilities` 저장 (ProbabilityService.java:53-79) | **재사용** — depth0 = "몰라요" 개념. 결과 조회 시 개념 단위 dedup(§4.4) |
| 저장 스키마 | `probabilities(probability_id, answer_id, concept_id, to_concept_depth, probability_percent, probability_timestamp)` — skill_id 컬럼 없음 | **골격 재사용 + `user_test_id` nullable 컬럼 additive 추가** — 신규 경로는 세션 스코프로 기록(answer_id NULL), 구 경로는 answer_id 로 무변경 |
| 결과 조회 | `findResults`: probabilities+concepts+chapters 조인, `to_concept_depth < 3` 필터, `ResultResponse{…, level("학교-학년-학기"), chapter("대-중-소")}` (JdbcTemplateProbabilityRepository.java:49-56) | **신규 계약 병행** — §4.4. 구 응답 보존 |
| 맞춤 소비 | `ItemService.findPersonalItems` — probabilities 소비, **소유권 검사 있음**(선례, ItemService.java:68-72) | 신규 인증 엔드포인트(귀속·큐)는 이 IDOR 선례를 따름 |
| 영역 데이터 | `GET /api/v1/chapters?grade&semester` (permitAll), `chapters(school_level, grade_level, semester, chapter_main/sub/name)`. `GET /api/v1/concepts?chapterId`·`/search`·`/{id}` (permitAll) | **재사용** + 시작 프론티어 쿼리 신규(§4.2) |
| 그래프 순회 | `findPrerequisitesWithDepth(conceptId, maxDepth)` 재귀 CTE(`MIN(depth)` 집계), `graph:v2:` Redis 캐시 24h, `cte_max_recursion_depth=10`(세션), CTE 플래그 `mmt.migration.use-mysql-cte-for-graph` | **재사용** — 순회·skip·큐 병합의 기반 |
| 보안 경계 | permitAll: `/api/v1/chapters/**`, `/api/v1/concepts/**`, `/api/v1/weakness-diagnosis/sample/**` 등. 그 외 `anyRequest().authenticated()` (SecurityConfig.java:79-95) | 신규 익명 엔드포인트(`/diagnosis/next`·`/diagnosis/preview`) permitAll 추가 + 남용 방어(§4.6) |

**핵심:** DKT 서빙 결합(`getPrediction`·`probabilityList[skillId-1]`)과 `probabilities` 골격은 **재사용**, 구 입력 경로(`answers`·`findAIInput`/`findBefore`·채점 API)는 **무변경 보존하되 신규 경로가 일절 접촉하지 않는다**(S2=C, R1 소멸). 입력 진입점(`self_report_answers`)과 출력 계약(결과·큐)만 신설한다.

---

## 4. 설계

### 4.1 세션 플로우 (F-1 결과-시점 게이트)

```
② 진입: GET /api/v1/chapters?grade&semester (기존·permitAll)     ← 스마트 default 후보
        POST /api/v1/diagnosis/frontier {chapterId}              ← 시작 프론티어 (신규·permitAll)
③ 문답: POST /api/v1/diagnosis/next {entry, answered[]} 반복      ← 적응형 순회 (신규·permitAll·무상태)
        (클라는 매 응답 후 answered-map 을 localStorage 저장 — F-2)
④ 결과: POST /api/v1/diagnosis/preview {entry, answered[]}        ← 익명 결과 (신규·permitAll·무영속, S1)
게이트: POST /api/v1/diagnosis {entry, answered[]} (인증)          ← 귀속 저장: users_tests + self_report_answers + probabilities
        POST /api/v1/learning-queues {userTestId} (인증)          ← 통합 학습 큐 생성 (F-4)
재진입: GET /api/v1/learning-queues/me · PATCH …/items/{id}/done  ← 이어가기·self-mark (인증)
```

- 익명 구간은 서버에 아무것도 남기지 않는다(무상태·무영속). 로그인 시 클라가 localStorage answered-map 을 `POST /diagnosis` 로 재제출해 귀속 — preview 와 동일 입력이 동일 결과를 내는 **결정론**이 계약이다(검증 §8).
- 신규 인증 엔드포인트는 `ItemService.findPersonalItems` 의 소유권 검사 선례를 따른다(구 `AIController` 의 IDOR 부재를 신규 경로에 복제하지 않음).

### 4.2 영역 진입 — 시작 프론티어 (F-3)

- 후보 pick-list 데이터는 기존 `GET /chapters` 재사용(학년 기반 시기 추정 default 는 프론트 로직, spec-02).
- **시작 프론티어** = 선택 단원 개념들 중 **단원 내 후수-최상위**(같은 단원의 다른 개념이 자기를 선수로 요구하지 않는 개념). 신규 쿼리(단순 NOT EXISTS, CTE 불필요):

```sql
SELECT c.concept_id, c.concept_name FROM concepts c
WHERE c.concept_chapter_id = ?
  AND NOT EXISTS (SELECT 1 FROM knowledge_space ks
                  JOIN concepts c2 ON c2.concept_id = ks.from_concept_id
                  WHERE ks.to_concept_id = c.concept_id
                    AND c2.concept_chapter_id = c.concept_chapter_id)
ORDER BY c.concept_id
```

- 고른 단원 = **시작점이지 경계가 아님** — "몰라요" drill-down 은 단원 밖 선수로 자유롭게 내려간다(F-3 관용 흡수).
- "모르겠어 → 전체 훑기" escape(b) = 학교급 대표 단원들의 프론티어 합집합에서 시작(구체 목록은 착수 시 시드 데이터로 확정 — 콘텐츠성 값이라 spec 에 하드코딩하지 않음).

### 4.3 적응형 순회 — 서버 주도 stateless (D1-A)

`POST /api/v1/diagnosis/next` — 매 요청에 전체 answered-map 을 실어 보내면 서버가 결정론적으로 재계산해 다음 1개를 반환(화면당 개념 1개, PRD ③). *진행 주체 대안 2종은 기각:* **서버 세션 순회**(Redis/userTest 에 진행 상태 저장)는 상태 수명주기·정합 부담, **클라이언트 순회**(subgraph 일괄 전달 후 프론트가 순회)는 순회 로직이 프론트로 새어 "엔진=백엔드" 정합이 깨짐 — PRD F-2(localStorage answered-map)가 stateless 전제를 확정하며 A 로 굳음.

```
req  { entry: {chapterId | scope:"full", schoolLevel?}, answered: [{conceptId, known}] }
res  { next: {conceptId, conceptName, description}, progress: {asked, estimatedRemaining}, done: false }
     { done: true }                                   // 프론티어 소진 → 클라가 preview/귀속 호출
```

**순회 규칙** (전부 depth-1 스텝·결정론적 순서 = conceptId 오름차순):

1. 초기 프론티어 = §4.2 시작 프론티어.
2. **"몰라요"(known=false)** → 그 개념의 **직계 선수**(`from_concept_id=C`의 `to_concept_id`)를 프론티어에 push. 무너진 토대의 바닥을 찾는 drill-down.
3. **"알아요"(known=true)** → 그 개념의 **선수 폐쇄 전체를 inferred-known 마킹**(`findPrerequisitesWithDepth(C, 10)` 재사용, 세션 캐시 `graph:v2:` 적중) → 이후 질문 대상에서 제외. 화면 수 급감의 핵심.
4. 다음 질문 = 프론티어에서 (이미 answered ∪ inferred-known) 제외 후 첫 개념. 소진 시 `done`.
5. `description` = `concepts.concept_description` 을 D5 "대표 예시" 1차 소스로 사용(공란·부실 개념의 보강은 spec-03 콘텐츠 트랙과 접점 — 계약만 예약).

무상태이므로 서버 세션·정합 부담 없음. 재계산 비용은 answered-map 크기에 선형 + 그래프 조회는 Redis 캐시 적중(24h TTL)으로 상쇄.

### 4.4 self-report→DKT 매핑 + 결과 계약 (D3-R)

**매핑·저장** (`POST /diagnosis` 귀속 시 확정, preview 는 동일 계산의 무영속판):

1. answered-map → `users_tests` 1행 + **`self_report_answers`** N행 저장(`known` BOOLEAN 그대로 저장 — 정오답 변환 **안다=1(맞음)/모른다=0(틀림)** 은 DKT 시퀀스 생성 시 조회 변환). 구 `answers` 는 읽지도 쓰지도 않음(S2=C).
2. DKT 입력 = `self_report_answers`(preview 는 요청 answered-map)에서 `[skillId, answerCode]` 시퀀스 직접 생성(`skill_id = concepts.skill_id`, items 조인 불요) + **×10 증폭 유지**. **시퀀스 순서 = 답변 입력 순서** — 귀속 재조회는 `self_report_answer_id ASC`(= 저장 입력 순서), preview 는 요청 `answered[]` 배열 순서 그대로. **두 순서의 일치가 결정론의 필요조건**(DKT 는 RNN 계열이라 순서 민감 — 재조회 순서 ≠ 답변 순서면 preview ≠ 귀속 → F-1 "본 결과를 저장" 위반). **×10 증폭은 순서 확정 후 반복, 저장은 `UNIQUE(user_test_id, concept_id)` 대로 개념당 1행**(10행 저장 아님). ⚠️ 현행 "입력 최소 크기 3 안정" 근거는 **구 채점 입력 분포 기준이라 자동 승계 불가** — 얇은 세션 유효성은 아래 R2 엣지 케이스 실측으로 검증. **S3 = A(현재 세션만) 확정** — 근거 = preview 와 귀속 결과의 **결정론 동치(F-1 "본 결과를 저장") 보장**.
3. `getPrediction` 재사용 → `probabilityList`. **가드 신설**: TF Serving null 응답, `skill_id = -1`(미매핑 개념), `skillId-1` 범위 초과 — 셋 다 현행엔 없음(§3).
4. 취약 확장 재사용: depth0 = "몰라요" 개념들 → `findPrerequisitesAsDepthMap(cId, 3)` → `probabilities` 저장 — **`user_test_id` 스코프로 기록(answer_id = NULL)**. `probability_percent = probabilityList[skillId-1]` 인덱싱 유지.

**R2 엣지 케이스 — 빈/얇은/단조 DKT 입력 (2026-07-13 S3 리뷰 반영):**

- **"몰라요" 0개**(전부 알아요) → depth0 없음 → **DKT 호출 생략**, `cards: []` + "약점 없음" 헤드라인 = **정상 결과**(에러 아님).
- **극소 세션**(1~2답 ×10)·**단조 시퀀스**(전답 동일값)에서 TF Serving 확률 분포가 유의미한지는 **미검증** — 착수 시 **대표 4 시나리오 실측**(1답만 / 전부 알아요 / 전부 몰라요 / 혼합, §8). 실측 분포는 등급 컷(S4) 보정의 입력이 된다.
- 실측이 부실해도 **fail-soft**(§4.7): 시급도 등급만 결측되고 "몰라요" 목록 기반 결과는 성립 — 런치 차단 요소 아님.

**신규 DDL** (권장안 — 타입은 analyze-before-change 에서 참조 PK 실측에 맞춰 확정, §6):

```sql
CREATE TABLE self_report_answers (
  self_report_answer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_test_id          BIGINT  NOT NULL,   -- FK users_tests (기존 세션 재사용 — 별도 세션 테이블 신설 안 함)
  concept_id            INT     NOT NULL,   -- FK concepts
  known                 BOOLEAN NOT NULL,   -- 안다=true / 모른다=false
  created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sra_user_test FOREIGN KEY (user_test_id) REFERENCES users_tests(user_test_id),
  CONSTRAINT fk_sra_concept   FOREIGN KEY (concept_id)   REFERENCES concepts(concept_id),
  UNIQUE (user_test_id, concept_id)         -- 세션 내 개념당 1답 (중복 방지)
);

-- probabilities 는 골격 재사용, 세션 스코프 컬럼만 additive:
ALTER TABLE probabilities ADD COLUMN user_test_id BIGINT NULL;  -- FK users_tests
-- 신규(self-report) 경로만 사용 — DKT 산출이 답(answer) 단위가 아니라 세션(answered-map) 단위라
-- user_test_id 스코프가 의미상 정확. answer_id 는 self-report 행에서 NULL.
-- 구 경로는 기존대로 answer_id 로 저장(무변경). output 테이블이라 findBefore 류 조인과 무관 → R1 재발 없음.
```

**결과 계약** — `POST /diagnosis/preview` 응답 = `GET /api/v1/diagnosis/{userTestId}` 응답 (동일 shape, 후자만 영속 조회 — `probabilities.user_test_id` 스코프 직조회, 구 `findResults` 의 answers 조인 불요. 전환 가능성 실측 = §6):

```
{ headline: { totalAsked, weakCount, topConceptName },        // "총 12개 중 3개 약점, 가장 급한 건 '인수분해'"
  cards: [ { conceptId, conceptName, level, chapter,
             urgency: "HIGH|MID|LOW",                          // §4.5 등급 컷 (S4)
             urgencyBasis: { blockedDescendants },             // "이걸 모르면 위로 N개가 막혀요" (S6)
             links: [] } ],                                    // spec-03 부착 지점 예약
  more: [ …MID/LOW 카드 동일 shape… ] }                        // "더 보기" (F-4)
```

- 카드 = **개념 단위 dedup**(depth0 확장 경로가 겹치면 probabilities 에 개념 중복 행 허용 — 조회 시 개념별 대표값 집계: `MIN(to_concept_depth)`, percent 는 skill 단위라 동일).
- `probability_percent`·`toConceptDepth` 등 내부 용어는 응답에 노출하되 **프론트 표기 금지**(PRD §4.2)는 spec-02 규율. `urgency` 등급·`urgencyBasis` 사람 언어 근거가 표기용 정본.
- top-N 규칙(PRD §4.1 확정): default = HIGH 등급 전부, **바닥 3**(부족 시 MID 에서 시급도순 보충)·**캡 5**(초과분 `more`).

### 4.5 등급 컷 · 근거 수치 (S4·S6)

- `probability_percent` = DKT 의 정답(이해) 확률 → **낮을수록 시급**. 시급도 정렬 = percent 오름차순.
- **등급 컷 출발값(S4):** `HIGH: p < 40` · `MID: 40 ≤ p < 65` · `LOW: p ≥ 65` — 구 ResultView 40/65 임계 선례 승계. **출발값일 뿐** — 실데이터 분포로 후속 보정(백로그, self-report 입력의 percent 분포는 실채점과 다를 수 있음).
- **blockedDescendants(S6):** 역방향 재귀 CTE 신규(`to_concept_id=P → from_concept_id` 방향, `findPrerequisitesWithDepth` 의 미러). depth 상한 3 출발(정방향 확장과 대칭·`cte_max_recursion_depth=10` 내), transitive distinct count. Redis 캐시 동일 네임스페이스(`graph:v2:blocked:{conceptId}:{depth}`).

### 4.6 통합 학습 큐 (F-4)

**병합 알고리즘** (`POST /learning-queues` 시 1회 계산·영속):

1. **노드 집합** = top-N 카드 개념 ∪ 각각의 선수 폐쇄(depth≤3, §4.4 확장과 동일 소스) 중 **known=true·inferred-known 제외**(이미 아는 계단은 안 밟음).
2. **간선** = 노드 집합 내부의 `knowledge_space` 선후 관계 → DAG.
3. **정렬 = 위상순 지배**(선수지식 먼저 — 계단 불변식). **시급도는 진입 우선순위로만**: Kahn 알고리즘에서 진입차수 0 후보가 복수일 때, "그 노드가 속한 카드 가지의 최고 시급도(최저 percent)" 우선 → 동률은 conceptId 오름차순(결정론). 순수 시급도순 정렬 금지(PRD §4.3 — "3단 건너뛰고 5단부터"로 계단 파괴).
4. 사이클 발견 시(데이터 이상) 해당 간선 drop + 경고 로그 — 큐 생성이 죽지 않게 degrade.

**재진입 스키마(S5)** — 신규 2테이블:

```sql
learning_queues       (queue_id PK AUTO, user_id FK users, user_test_id FK users_tests,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
learning_queue_items  (queue_item_id PK AUTO, queue_id FK, position INT,
                       concept_id INT FK concepts, done BOOLEAN DEFAULT FALSE, done_at TIMESTAMP NULL,
                       UNIQUE (queue_id, position))
```

- **현재 위치 = 파생값**(position 순 첫 `done=false`) — 포인터 컬럼 없음(드리프트 원천 차단, S5 권장안).
- 완료 = 유저 self-mark(`PATCH /learning-queues/{qId}/items/{itemId}/done`) — 앱은 외부 학습을 검증하지 않음(PRD §4.3).
- 유저당 활성 큐 1개(재진단→새 큐 생성 시 구 큐 대체 또는 보관 — 착수 시 확정, 계약엔 영향 없음). 재진단은 별개 액션.
- 신규 리포지토리는 **JPA**(api/CLAUDE.md 영속성 규칙 — 신규는 JdbcTemplate 금지).

**익명 엔드포인트 남용 방어:** `preview`·`next` 는 permitAll + TF Serving 실호출(preview) — 무거운 preview 에 IP 단위 rate limit(Redis 카운터, `RedisUtil` 재사용)을 두고, `next` 는 순수 DB/캐시 계산이라 완화. 구체 임계는 착수 시.

### 4.7 피처 플래그

- **`mmt.diagnosis.enabled`** (기본 `false`, 배포 시 `true`) — 신규 `/api/v1/diagnosis/*`·`/learning-queues/*` 활성화 게이트. 신규 영역 `mmt.diagnosis.*` 는 api/CLAUDE.md 컨벤션대로 **ADR 기록 후 섹션 추가**(self-report→DKT 매핑 ADR 과 겸용, §6).
- **롤백 = `mmt.diagnosis.enabled=false`** (+ 구 Vue 프론트). S2=C 로 신규 테이블·컬럼만 additive 이고 구 `answers`·채점 경로(`/weakness-diagnosis`·`TestController`·`findAIInput`/`findBefore`)는 미변경이라 **"구 경로 무변경 = 롤백 대상"이 실제로 성립**(신·구 병행, 피처 플래그 정책 충족). 구 채점 진단은 "제2 진단 모드"로 되살릴 여지 보존.
- self-report 경로 degrade: TF Serving 실패 시에도 문답·"몰라요" 목록까지는 성립(시급도 등급만 결측) — fail-soft 응답 형태는 착수 시 확정.

---

## 5. 왜 이 순서 (spec-01 먼저)

- 본 spec 이 **프론트(spec-02)가 소비할 전 계약**(진입·문답·결과·큐)을 정의 — 계약이 굳어야 React 화면이 헛돌지 않는다.
- 큐 위상정렬·재진입 스키마는 spec-03(경로 노출)의 데이터 기반 — spec-03 은 본 spec 의 `links: []`·큐 계약 위에 얹힌다.

---

## 6. Analyze-Before-Change 예고 (합의 후 착수 시 수행)

- **참조 지점:** `AIController`·`ProbabilityService`(createAndPredict/create/getPrediction/findResults)·`AnswerService`(findAIInput/create/findIds)·`AnswerConverter`·`JdbcTemplateAnswerRepository`·`JdbcTemplateProbabilityRepository`·`UserTestService`/`JdbcTemplateUserTestRepository`·`SecurityConfig` permitAll 목록·`ItemService`(probabilities 소비 유지 확인) + 신규 DDL 2건(`self_report_answers`·`probabilities.user_test_id` additive).
- **실측 확인 3건(추측 금지):** ① `users_tests` PK 컬럼명·타입(§4.4 FK 가정 검증) ② `concepts.concept_id` 타입(INT 가정 — `self_report_answers.concept_id` 정렬) ③ 결과 조회가 answer_id 경유에서 `user_test_id` 스코프로 전환 가능한지(신규 경로 결과 조회가 세션 스코프로 도는지).
- **신규 리포지토리는 JPA** (api/CLAUDE.md 영속성 규칙 — 신규 JdbcTemplate 금지): `self_report_answers`·`learning_queues` 계열.
- **영향 테스트:** 진단/AI 경로, `*N1Test`, `ConceptServiceFeatureFlagTest`, probabilities/personal 관련. 신규: 순회 결정론·위상정렬·등급 컷 단위 테스트(§8).
- **롤백:** `mmt.diagnosis.enabled=false` + 구 경로 무변경 유지. 신규 테이블·컬럼은 전부 additive — 구 경로가 미참조하므로 다운 마이그레이션 없이 방치 가능(필요 시 DROP).
- **ADR:** ① self-report→DKT 매핑(D3-R 트레이드오프·R1 감수 근거 — 밀스톤 "필요시 ADR" 항목) + `mmt.diagnosis.*` 영역 등록 ② React 도입 ADR 은 spec-02 소관.

---

## 7. 결정 (사인오프 대기 — S1~S6)

- **S1 — 익명 결과 산출: ▶ 잠정 채택 A(무영속 preview + 로그인 시 재제출 귀속) — 착수 시 재검토.** `users_tests.user_id` FK 무변경, F-2 localStorage 와 정합, 서버에 익명 잔재 없음 / B 게스트 행 영속화 — user_id nullable 마이그레이션 + 고아 행 정리 배치 필요.
- **S2 — self-report 답안 저장 스키마: ✅ C 확정(2026-07-13 리뷰).** **신규 `self_report_answers` 테이블(§4.4 DDL) + `probabilities.user_test_id` additive.** 구 A안(`answers.item_id` NOT NULL 완화)은 리뷰 **R1(공유 answers 테이블 오염)** 확정으로 기각 — 롤백용으로 살아있는 `findBefore` 가 NULL item_id 행을 전체 이력으로 긁어 items→concepts 조인이 깨짐. 신규 경로는 구 `answers` 를 **읽지도 쓰지도 않음** → R1 소멸. 유저 0명이라 마이그레이션·하위호환 부담 없음, 구 채점 진단은 "제2 진단 모드"로 되살릴 여지 보존. **임시답/가짜 item 매핑 금지** — 조인을 성공시켜 오염을 '조용한 오염'으로 은폐. 세션 단위는 기존 `users_tests` 재사용(별도 세션 테이블 신설 안 함). **단, 결과조회 배선(신규 `user_test_id` 직조회 쿼리)은 §6 실측 확인 ③ 통과 전제** — 실측에서 걸리면 S2 재오픈이 아니라 조회 쿼리 설계만 조정.
- **S3 — DKT 입력 구성: ✅ A 확정(2026-07-13 리뷰 — R2 가드 전제).** 현재 세션 answered-map 만 ×10 — **preview 와 귀속 결과의 결정론 동치(F-1 "본 결과를 저장") 보장**, 스냅샷 설명가능. **R2(빈/얇은/단조 입력) 완화 = §4.4 엣지 케이스 3종**(몰라요 0개→DKT 생략 / 대표 4 시나리오 실측 / fail-soft — "최소 크기 3 안정"의 구 근거는 자동 승계 불가로 정정). B(과거 self-report 세션 병합)는 preview·귀속 결과 불일치로 F-1 위반 → 기각.
- **S4 — 등급 컷 출발값: ▶ 잠정 채택(HIGH < 40 ≤ MID < 65 ≤ LOW) — 착수 시 재검토.** 구 ResultView 선례 승계이나 **self-report 매핑 입력의 percent 분포는 실채점과 다를 수 있어 승계가 출발값 이상을 의미하지 않음** — §4.4 R2 대표 시나리오 실측 분포와 함께 컷 재검토·실데이터 보정(백로그).
- **S5 — 큐 스키마: ▶ 잠정 채택(§4.6 2테이블 + 현재 위치 = 파생값) — 착수 시 재검토.** 별도 포인터 컬럼은 불필요한 정합 부담.
- **S6 — 카드 근거 수치: ▶ 잠정 채택(blockedDescendants = 역방향 CTE transitive, depth 3) — 착수 시 재검토.** 직계만은 count 과소로 근거 문구 임팩트 약함 / 무제한 depth 는 상위 개념에서 수백 개로 과장 위험.

---

## 8. 검증 (착수 후)

- **적응형 동작:** "알아요"가 선수 폐쇄를 실제로 skip 해 문답 수가 전 개념 나열 대비 유의미하게 주는지(단위 테스트 + 실시드 subgraph).
- **결정론:** 동일 answered-map → `next` 순서·`preview` 결과·귀속 결과가 항상 동일(S1-A 계약의 핵심 — 스냅샷 테스트).
- **시퀀스 순서 동치:** preview 시퀀스(요청 `answered[]` 순서) == 귀속 재구성 시퀀스(`self_report_answer_id ASC`) — **순서 포함** 동일 property 테스트(§4.4-2 필요조건 증명).
- **DKT 정합:** self-report 매핑 시퀀스로 TF Serving 실응답이 오고 `probabilityList[skillId-1]` 시급도가 나오는지 + 신설 가드 3종(null 응답·skill_id=-1·범위 초과) 동작 + **R2 대표 4 시나리오 실측**(1답만 / 전부 알아요→DKT 생략 확인 / 전부 몰라요 / 혼합)의 확률 분포 기록 — S4 컷 보정의 입력.
- **계단 불변식:** 생성된 큐의 모든 (선수, 후수) 쌍에서 선수 position < 후수 position (위상정렬 property 테스트). 시급도는 진입 순서에만 반영되는지.
- **게이트 배선:** 비로그인으로 문답→preview 완주(무료 충족, §5.1 PRD) / 귀속·큐·done 은 인증+소유권 강제.
- **구 경로 무접촉(R1 소멸 증명):** self-report 경로가 `answers`·`tests_items`·`items`·`findBefore`/`findAIInput` 을 **일절 참조하지 않고 완주**(property 테스트).
