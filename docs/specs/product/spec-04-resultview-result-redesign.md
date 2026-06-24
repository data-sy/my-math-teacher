# spec-04 · ResultView 결과 재설계 (헤드라인 요약 → 우선순위 카드 → 근거 강등)

> 트랙: [Design] 실배포 전 리디자인 — 리포트 B 시리즈 화면 재설계 (B-3). 정본 진행상태 = `docs/roadmap.md` + `design-redesign-handoff.md`
> 작업 브랜치: `feat/resultview-result-redesign` · Task 단위 커밋
> 상태: spec 합의 대기 → 합의 후 코드 단계(`/analyze-before-change` → 구현)
> 선행: spec-03(그래프 접근성) 완료 — ResultView 그래프는 `composables/useConceptGraph.js` 공유. 본 spec은 그래프 레이어를 건드리지 않고 **표현·정보위계**만 재설계.

## 1. 배경 · 문제

진단 결과(ResultView)는 MMT의 제품 핵심 가치 — "네 약점은 X, 그래서 Y를 해라" — 를 전달하는 화면이다. 현재 구현이 그 가치를 못 전달한다. 컨설팅 리포트(`docs/consulting/out/design-ux-report.md` A-3 §65~71, 총평 §26, 목표형태 B-3 §176, 로드맵 §232) 결손:

- **P0 · 핵심 보상이 raw 표로 던져진다.** 시급도 상/중/하 뱃지가 붙은 밀도 높은 `DataTable` + 누적 Cytoscape 트리. **헤드라인 요약·"가장 급한 약점 N개"가 없음** → "그래서 뭘 하지"가 비어 있음.
- **P0 · 내부용어·영문 노출.** "선수지식 깊이"(`toConceptDepth`) 같은 내부 모델 용어가 사용자에게 그대로. (현 template 의 컬럼 헤더는 한국어로 확인되나 — 코드 단계에서 잔여 영문/내부용어 전수 점검.)
- **P1 · 시급도 산정 불투명.** 확률 백분위를 3등분해 상/중/하를 매기는데(코드 `setPriority` L164~175) 근거 수치가 사용자에게 안 보임 → AI 결과 신뢰 하락.
- **P1 · 뱃지 색 의미 충돌·결손.** `getPriority`(L177~189): 상=danger / 중=warning / **'new'=success(죽은 분기 — priority 는 상·중·하만 생성됨)**. **'하' 케이스 없음 → severity 미지정 뱃지.** 색 체계가 임시.
- **P2 · 빈 상태가 "라벨만 있는 빈 표".** 개념 상세 패널이 값 없는 4행 리스트로 노출.

### 1.1 데이터 사실확인 (코드 레벨 — 옵션 확정의 근거)

`GET /api/v1/weakness-diagnosis/{userTestId}` 응답(`ResultResponse`, 쿼리 `JdbcTemplateProbabilityRepository.findResults` L50~55):

| 필드 | 의미 |
|---|---|
| `testItemNumber` | 진단 문항 번호 (그룹 키) |
| `conceptId` / `conceptName` | 개념 |
| `toConceptDepth` | 선수지식 깊이. **0 = 대표개념(문항이 직접 묻는 개념), 1·2 = 선수지식.** 쿼리 `to_concept_depth < 3` |
| `probabilityPercent` | 개념별 숙련 확률(DKT 예측). **낮을수록 약점** |
| `level` / `chapter` | 학교-학년-학기 / 대-중-소단원 |

**응답에 없는 것(중요):** 정답/오답 플래그, 전체 문항 수, 점수. → "정답 7/10 게이지"는 이 엔드포인트만으론 불가(별도 채점 엔드포인트 필요). **이번 spec 은 가용 데이터만으로 구성** (D2).

## 2. 범위

### In
1. **헤드라인 요약 한 줄** — 가용 데이터 기반: "분석된 N문항 · 약점 개념 M개 · 가장 급한 약점: X(숙련도 %)".
2. **시급도순 우선순위 약점 카드** — **문항 단위**(D1). 분석된 각 문항 → 카드 1개. 대표개념 + 그 문항의 가장 약한 선수지식 + 숙련도 % + 시급도 + 다음행동 CTA.
3. **progressive disclosure** — 선수지식 그래프 트리·개념 상세를 "근거 더보기"로 강등(접힘). 기능 보존, 재사용. **기존 raw 표(`DataTable`)는 강등이 아니라 삭제** — 카드가 완전 대체(런타임 리뷰 결정, §3 D5).
4. **곁다리 정리(D4)** — 시급도 뱃지 색 3색 확정('new' 죽은 분기 제거 + '하' 케이스 추가) · 빈 상태 안내 · 잔여 내부용어/영문 카피 점검.

### Out (이번 범위 밖)
- **차트(chart.js) 신규 도입** — 미도입 결정(D3). 숫자·텍스트 + CSS 막대로 충분. `chart.js@3.3.2` 의존성·`main.js` 전역등록은 손대지 않음.
- **정답률/점수 표시** — 채점 데이터 미가용(§1.1). 별도 엔드포인트 연동 + ADR 필요 → 후속.
- **개념 단위 약점 집계**(across-문항 중복제거 랭킹) — 변환 로직 비용 ↑. 문항 단위로 출발, 집계는 후속 P2.
- **그래프 렌더/색/인터랙션** — spec-03 산출(`useConceptGraph.js`)로 이미 처리. 본 spec 은 그래프를 "근거 더보기" 안으로 옮길 뿐 동작 불변.
- **per-개념 맞춤 학습지** — 백엔드 `goToNextPage`/personal 흐름은 `userTestId` 단위만 지원(개념 단위 출제 미지원). 카드의 학습 CTA 는 "선수지식 트리 보기"로 한정, 맞춤 학습지는 화면 단위 단일 CTA 유지(§4.5).
- **선수지식 트리 "누적해서 보기" 명시적 affordance** — 표 삭제로 기존 그룹헤더의 "누적해서 보기" 버튼도 사라짐. 카드의 "선수지식 트리 보기"가 `showTree`(=`knowledgeSpace` 누적 push)라 동작상 누적은 유지되나, "여러 약점을 한 트리에 누적해서 본다"는 의도를 명시적 UI 로 살리는 건 별도 설계 → **로드맵 Later 백로그**(런타임 리뷰 결정).
- 셸/토큰/타이포 전역 정리(C안) → Later.

## 3. 결정 (사용자 승인 2026-06-24)

- **D1 · 약점 카드 = 문항 단위.** 분석된 각 문항 → 카드 1개. 현 그룹(`testItemNumber`) 구조와 1:1 → 데이터 그대로, 변환·회귀 표면 최소, "왜? N번 문항" 근거 표현 가능. (개념 단위 집계는 후속.)
- **D2 · 헤드라인 = 가용 데이터만.** mastery 기반 요약("분석된 N문항 · 약점 M개 · 가장 급한 약점 X"). 정답률/점수는 데이터 확보 후 후속. 백엔드 무변경.
- **D3 · 차트 미도입.** 숫자·텍스트 + CSS 막대(숙련도 %). 정답률 데이터가 없어 도넛 근거가 약하고, mastery 는 숫자/막대로 충분. 후속 데이터 확보 시 재검토.
- **D4 · 곁다리 정리 = 같은 spec 의 별도 Task.** 뱃지 색·빈 상태·잔여 카피. 핸드오프 "같이 or 별도 Task" 중 별도 Task 로.
- **D5 · raw 표 = 강등이 아니라 삭제 (런타임 리뷰 결정, 2026-06-24).** 초안은 표를 "근거 더보기"로 강등(보존)했으나, 런타임 확인 결과 카드와 그래프 사이에 표가 끼어 **노이즈**였고 카드가 표 정보를 완전 대체함 → 표 삭제. 부수: 학습지 목록(Listbox)을 좌측 고정·진단 결과를 우측(원 레이아웃 복원), "진단 결과 요약" 헤딩 → "진단 결과", "누적해서 보기"는 백로그(§2 Out).

## 4. 설계

### 4.1 카드 모델 (문항 그룹 → 카드, 순수 가공)

분석 응답을 `testItemNumber` 로 그룹화한 뒤 그룹당 카드 1개를 만든다. (현 `sortProbaGroupByTestItemId`/`representative` 가공 L77~86·139~162 를 카드 모델로 확장·대체.)

그룹 G(한 문항)에 대해:
- `representative` = `toConceptDepth === 0` 행 (문항이 직접 묻는 대표개념). *현 코드의 `representative` 가드 로직 재사용 — depth-0 누락 시 null 가드(PR #27 `6098760` 패턴 유지).*
- `weakest` = G 에서 `probabilityPercent` **최소** 행 (가장 약한 선수지식 = 채워야 할 곳). 카드 **제목**이 됨.
- `mastery` = `weakest.probabilityPercent`.
- `severity`(시급도) = `mastery` 절대 구간(§4.3).
- 카드 부가: 대표개념명, weakest 개념명·학년·단원, 그룹 내 개념 수(`calculateResultTotal` 재사용).

카드 정렬 = `mastery` **오름차순**(가장 약한 카드가 위로 = 가장 급한 것 먼저).

### 4.2 헤드라인 요약 (D2)

- **분석된 N문항** = distinct `testItemNumber` 수.
- **약점 개념 M개** = `severity ∈ {상, 중}` 카드 수(= 약점 임계 미만, §4.3). 0이면 "약점 없음, 잘했어요" 류 긍정 메시지.
- **가장 급한 약점: X(%)** = 최저 `mastery` 카드의 `weakest` 개념명 + 숙련도 %.
- 한 줄 서사 예: *"분석된 8문항 중 3개 약점. 가장 급한 건 인수분해(숙련도 32%)예요."* — 영문/내부용어 없이.

### 4.3 시급도 산정 (P1 불투명성 해소)

현 `setPriority`(그룹 내 thirds, 상대) 는 카드 간 비교가 안 됨(모두 약해도 '하'가 생김). **카드 간 비교 가능한 절대 구간**으로 교체:

| 시급도 | mastery 구간 | 뱃지 색(D4) |
|---|---|---|
| 상 | `< 40%` | danger (빨강) |
| 중 | `40% ~ 65%` | warning (주황) |
| 하 | `≥ 65%` | info / 중립 |

- **임계값(40·65)은 제안 출발값** — 실데이터 mastery 분포 확인 후 코드 단계에서 도메인 보정(spec-03 색 대비와 같은 "제안값→코드단계 확정" 패턴). spec 합의를 막지 않음.
- **불투명성 해소:** 뱃지에만 의존하지 않고 **항상 실제 숙련도 %를 병기**(막대 + 숫자). 색 단독 의존 금지([[feedback]] 접근성 정합).

### 4.4 progressive disclosure (강등) + raw 표 삭제 (D5)

상단(좌 Listbox · 우 진단 결과=요약+카드) 아래, 상세는 **"근거 더보기"** 토글(`v-show`, 기본 접힘) 안으로 이동 — **기능·컴포넌트 보존, 위치만 강등**:
- 선수지식 트리(`useConceptGraph` 그래프 + 범례) — 동작 불변.
- 개념 상세보기 1·2 — 동작 불변(빈 상태만 D4 에서 개선).
- ~~문항별 표(`DataTable`)~~ — **삭제(D5)**. 카드가 대체. 표가 쓰던 `sortProbaGroupByTestItemId`·`setPriority`·`calculateResultTotal`·`sortedResultList`·`item.representative` 가공도 dead-code 로 함께 제거.

카드의 "선수지식 트리 보기" → 기존 `showTree(representative.conceptId)` 호출. 트리는 접힌 `v-show` 안에 있으므로 `showTree` 가 **먼저 `evidenceOpen=true` 로 펼친 뒤 `nextTick` → `initGraph`** 해야 Cytoscape 컨테이너 크기가 잡힘(`v-if` 면 언마운트로 깨짐). 이어 기존 `scrollIntoView` 로 트리까지 스크롤.

### 4.5 CTA · 다음 행동

- **카드 CTA = "선수지식 트리 보기"** (per-문항, `showTree`). per-개념 맞춤 학습지는 백엔드 미지원이라 카드에 두지 않음(Out).
- **화면 단위 CTA = "맞춤 학습지 출제"** 1개 유지(`goToNextPage`, `userTestId` 단위, L341~349). 비로그인/미선택 안내(`confirm`/`confirm2`)도 현행 유지.

### 4.6 빈 상태 (D4)

- 분석 결과 없음(목록 미선택 또는 `sortedResultList` 비어 있음): 요약/카드 영역에 **안내형 빈 상태**("학습지를 선택하면 약점 분석이 나와요" 등) — 빈 표 금지.
- depth-0 누락(로드맵 [Data] 백로그 `f8f6997`)으로 트리/카드가 빌 수 있음 → 가드 + 안내 문구. 카드는 `representative` null 가드 유지.

## 5. 영향 분석 (코드 단계 `/analyze-before-change` 에서 확정)

- **단일 파일:** `web/src/views/ResultView.vue`(546줄). 신규 컴포넌트 분리 여부(예: `ResultSummary.vue`/`WeaknessCard.vue`)는 코드 단계 판단 — 분리 시 web/CLAUDE.md "중복 구성 금지"·PascalCase 준수.
- **데이터 계약 불변:** 백엔드 API·응답 필드·`userTestId` 흐름 변경 0. `weakness-diagnosis` 가공만 프론트에서 재구성.
- **재사용/보존:** `showTree`·`clearCy`·`goToNextPage`·`confirm/confirm2`·`useConceptGraph`·`calculateResultTotal` 그대로. 교체 대상은 `setPriority`(상대 thirds)·`getPriority`(뱃지 색)·상단 표 우선 레이아웃.
- **테스트:** web 테스트 프레임워크 없음 → 빌드/lint + 풀스택 배선(어시스턴트) + 시각 검증(사람). **depth-0 누락 탓에 로컬 샘플로는 카드/트리가 빌 수 있음** → 실유저 데이터(로그인) 또는 depth-0 시드 보강 필요할 수 있음([[project_personalview_local_verification]]).
- **롤백:** 순수 프론트. 피처플래그 불요. 문제 시 커밋 revert 로 즉시 원복. 백엔드 영향 0.

## 6. 검증 계획

1. `npm run build` PASS, `npm run lint` 신규 에러 0.
2. dev 서버 배선 — 학습지 선택 → 헤드라인/카드 렌더, 카드 "선수지식 트리 보기" → 그래프 펼침·스크롤, "근거 더보기" 토글, 맞춤학습지 CTA 동작 (어시스턴트).
3. 시급도 구간 — 실데이터 mastery 분포로 40·65 임계 체감 확인(필요 시 보정).
4. **사람 시각 검증:** 헤드라인 한 줄이 읽히는가 / 카드가 시급도순인가 / % 병기로 근거가 보이는가 / 표·그래프가 접혀 있고 펼치면 동작하는가 / 빈 상태가 안내형인가 / 영문·내부용어 잔존 0. (실유저 데이터 또는 depth-0 시드 전제.)

## 7. Task 분해 (커밋 단위)

- **Task 1 — 카드 모델 가공(순수 로직).** 문항 그룹 → 카드(대표개념·weakest·mastery·severity) + 헤드라인 요약 계산. `setPriority` 상대 thirds → §4.3 절대 구간. UI 미변경(데이터 레이어만). *가공과 화면을 분리해 회귀 표면 격리.*
- **Task 2 — 헤드라인 요약 + 우선순위 카드 UI.** §4.2·§4.1 상단 레이어 신설. 카드 CTA "선수지식 트리 보기"(기존 `showTree` 배선).
- **Task 3 — progressive disclosure.** 그래프·상세를 "근거 더보기"(기본 접힘)로 강등, 동작 보존. 카드→트리 스크롤 연동(`v-show`+nextTick+initGraph).
- **Task 4 — 곁다리(D4).** 뱃지 색 3색('new' 제거·'하' 추가) · 빈 상태 안내 · 잔여 내부용어/영문 카피 점검.
- **Task 5 — docs.** 로드맵 [Design] B-3 항목 처리 표시 + 핸드오프 갱신.
- **Task 6 — 런타임 리뷰 반영(D5).** raw 표 삭제 + 죽은 가공 코드 제거 · Listbox 좌측 복원 · 헤딩 "진단 결과" · 누적보기 백로그 등재. (스펙 초안 후 런타임 확인에서 나온 정정.)

> Task 1(가공)을 먼저 끊는 이유: 데이터 변환과 화면 재배치를 한 커밋에 섞으면 회귀 원인 분리가 어렵다. 가공이 올바른 카드 모델을 내는지 먼저 확인한 뒤 UI 를 얹는다(spec-03 "거동 불변 추출 먼저"와 같은 규율).
