# spec-03 · 그래프 응급 접근성 (ConceptView · ResultView 공통)

> 트랙: [Design] 실배포 전 리디자인 — 이월 UI 마지막 항목 (정본 진행상태 = `docs/roadmap.md` + `design-redesign-handoff.md`)
> 작업 브랜치: `feat/pre-launch-redesign` · Task 단위 커밋
> 상태: spec 합의 대기 → 합의 후 코드 단계(`/analyze-before-change` → 구현)

## 1. 배경 · 문제

개념 지식 그래프(Cytoscape+klay)는 MMT의 핵심 차별점인데, 현재 구현이 접근성 응급 상태다. 컨설팅 리포트(`docs/consulting/out/design-ux-report.md` A-2 §53~60, §126)에서 확인된 결손:

- **P0/P1 · 노드·폰트 7px** (`nodeSize=7, fontSize=7`) — 극단적으로 작아 가독 불가.
- **P1 · 색 대비 실패** — 학년 12색을 CSS raw 색명(`yellow`, `springGreen`, `lightpink`…)으로. **노란색 노드는 흰 카드 배경에서 사실상 안 보임**(WCAG 1.4.11 비텍스트 대비 3:1 미달). 12색은 색맹·저시력 구분 불가 + 과다.
- **P1 · 호버 전용 하이라이트** — `mouseover`로 focus, `mouseout`으로 리셋 → **선택 상태가 유지되지 않음**. 모바일/태블릿은 호버가 없어 핵심 인터랙션(선수지식 강조 보기)이 사실상 불가.

**코드 레벨 확인:** 위 로직은 `ConceptView.vue`와 `ResultView.vue`에 **동일하게 복붙**되어 있다 (`getNodeColor`, `setFocus`/`setResetFocus`, `nodeMyColor`, `mouseover/mouseout`, `nodeSize=7`). 리포트 §71 "P2 · 그래프 영역은 ConceptView와 동일한 접근성 문제 반복"이 사실.
- `ConceptView.vue`: 그래프 로직 L126~406
- `ResultView.vue`: 그래프 로직 L195~427 (누적 선수지식 트리)

## 2. 범위

### In
1. **공통 컴포저블 추출** — 두 화면의 동일 Cytoscape 스타일·인터랙션·색 로직을 `web/src/composables/useConceptGraph.js`로 추출, 양쪽이 동일 소스 사용 (결정 D1, 사용자 승인 2026-06-24).
2. **노드/폰트 크기 상향** — 가독 가능한 기본 크기로.
3. **호버→클릭 선택 + 선택 유지** — 모바일 동작 복구. focus가 다음 선택/명시적 해제까지 유지.
4. **색 체계 12색 → 학교급 3색 + 명도 3단계** — 노란색 퇴출, 흰 배경 비텍스트 대비 ≥3:1 보장. 범례(template `<ul>`)도 동일 색으로 동기화.

### Out (이번 범위 밖 — 재확인됨)
- B-2 "단일 캔버스(검색/필터 한 줄 + 그래프 상시 표시)" 진입 재설계 → 별도 로드맵 항목(`docs/roadmap.md` Later, 리포트 §234). 4단계 선행 입력 UI는 **이번에 건드리지 않는다.**
- 줌 컨트롤·미니맵·검색·"현재 보고 있는 개념" 표식 (리포트 §60) → Later.
- 색 토큰을 전역 `tokens.css`로 끌어올리는 디자인시스템 작업(C안) → Later. 이번엔 컴포저블 내부 상수로 1곳 집약까지만(토큰화의 발판).
- ResultView의 표/요약 서사 재설계(B-3) → 별도.

## 3. 결정

- **D1 · 추출 방식 = 공통 컴포저블 1곳 수정.** (사용자 승인) 동일 복붙 코드를 `useConceptGraph.js`로 합치고 두 뷰가 호출. 근거: web/CLAUDE.md "페이지별 중복 구성 금지", 향후 색 토큰화 단일 지점, 수정·회귀 표면 최소화.
- **D2 · 색 체계 = 학교급 3색 + 명도 3단계 (12색 폐기, 노란색 퇴출).** 핸드오프 확정 범위. 교육과정 12색의 정합성보다 가독·접근성 우선(리포트 §251 트레이드오프, 핸드오프에서 후자로 결정됨). 학년 단위 구분은 명도로 보존.
- **D3 · 인터랙션 = 클릭(tap) 토글 선택 + 선택 유지.** 호버는 데스크톱 보조로만(선택을 덮어쓰지 않음). 빈 배경 tap 시 선택 해제. 모바일 우선.

## 4. 설계

### 4.1 색 체계 (D2)

학교급별 1 색상(hue) × 명도 3단계. 전부 흰 배경(#ffffff) 대비 비텍스트 대비 ≥3:1 목표. 아래는 **제안 출발값** — 코드 단계에서 대비 수치 검증 후 확정(시각 최종확인=사람).

| 학교급 | 학년(grade) 매핑 | 명도 단계 | 제안 hex |
|---|---|---|---|
| 초등(green) | 초1·2 / 초3·4 / 초5·6 | 밝음→어두움 | `#86c98e?` → `#43a047` → `#1b5e20` |
| 중등(violet) | 중1 / 중2 / 중3 | 밝음→어두움 | `#9575cd` → `#673ab7` → `#311b92` (3.68/7.33/12.34, 명도 간격 확대 — 시각리뷰 반영) |
| 고등(magenta/red) | 수학(상·하) / 수Ⅰ·수Ⅱ / 미적·확통·기하 | 밝음→어두움 | `#f06292` → `#e53935` → `#ad1457` |

설계 제약:
- **밝음 단계도 흰 배경 대비 ≥3:1** (가장 흔한 실패 지점 — `#86c98e?`는 미달 가능, 코드 단계에서 상향 검증).
- focus 시 방향 엣지 색(`fromColor=#ff6348` 후수 주황, `toColor=#1e90ff` 선수 파랑)·선택 노드 색(`#6466f1` 인디고)과 **base hue가 충돌하지 않게** 함 → 초=green/중=violet/고=magenta 로 파랑·주황 회피.
- 색 단독 의존 금지 보강: 명도 + 범례 텍스트로 학교급 구분(색맹 대비). (모양 구분까지는 이번 범위 밖.)
- 매핑 누락(`default`)은 중립 회색 유지.

### 4.2 컴포저블 표면 — `useConceptGraph.js`

추출 대상(현재 두 파일에 중복): 크기/색 상수, `getNodeColor`, `changeNodeColor`, `setDimStyle`, `setFocus`, `setResetFocus`, cytoscape 스타일·layout 정의, tap/hover 핸들러, 인스턴스 init/destroy.

제안 API (정확한 시그니처는 구현 시 확정):
```
useConceptGraph() → {
  initGraph(containerEl, elements, { onNodeSelect }),  // cy 생성 + 스타일 + 핸들러 바인딩
  selectNode(id) / clearSelection(),                   // 클릭 선택유지 모델
  destroy(),                                           // onBeforeUnmount에서 호출 (라이프사이클)
  GRADE_COLORS,                                         // D2 색 상수 (범례도 참조)
}
```
- **Cytoscape 라이프사이클 주의(web/CLAUDE.md):** init/destroy를 컴포저블이 소유하되, 호출은 각 뷰의 `onBeforeUnmount`/재조회 시점에서. 기존 `clearCy()` 역할 보존. 두 뷰의 데이터 적재 흐름(ConceptView=`showKnowledgeSpace`, ResultView=누적 트리)은 **그대로 두고** 그래프 렌더 부분만 컴포저블로 위임.
- 범례 `<ul>`(ConceptView L512~525 / ResultView 대응부)은 `GRADE_COLORS`를 바인딩해 하드코딩 색 제거 → 색 진실원천 1곳.

### 4.3 인터랙션 모델 (D3)

- `cy.on('tap', 'node')`: 해당 노드 선택 → focus 스타일 적용 **+ 유지**. 기존처럼 상세보기 패널에 `clickedNodeId`/`selectedNode` 전달.
- `cy.on('tap', (e)=> e.target===cy && clearSelection())`: 빈 배경 탭 → 선택 해제·리셋.
- 호버(`mouseover/mouseout`): 데스크톱에서 **선택이 없을 때만** 임시 미리보기, 선택이 있으면 덮어쓰지 않음. (호버 전용 의존 제거.)
- 크기: `nodeSize`/`fontSize` 기본값 상향(제안 노드 ~16, 폰트 ~12 — 구현 시 klay spacing과 함께 시각 검증). active 크기는 비례 상향.

## 5. 영향 분석 (코드 단계 `/analyze-before-change` 에서 확정)

- **참조 지점:** `ConceptView.vue`(L126~406, template 범례 L512~525), `ResultView.vue`(L195~427, 대응 범례). 두 파일이 유일 소비처. 신규 `composables/useConceptGraph.js`.
- **데이터 계약 불변:** API 응답(`/concepts/nodes`·`/edges`, `conceptGradeLevel` 값)·`clickedNodeId`/`selectedNode`/상세 패널 watch 흐름은 변경 없음. 그래프 표현 레이어만 교체.
- **테스트:** web 테스트 프레임워크 없음 → 런타임 수동 검증이 안전망. 빌드/lint + 풀스택 배선 확인(어시스턴트) + 클릭·모바일 시각 검증(사람). (참고: [[project_personalview_local_verification]] 로컬 셋업.)
- **롤백:** 컴포저블 도입은 순수 프론트·피처플래그 불요. 문제 시 커밋 revert로 즉시 원복(추출 전 인라인 로직으로 복귀). 데이터·백엔드 영향 0.

## 6. 검증 계획

1. `npm run build` PASS, `npm run lint` 신규 에러 0.
2. dev 서버 배선 — 그래프 렌더·노드 tap·상세 패널 갱신 OK (어시스턴트).
3. 대비 검증 — 제안 색 전 단계 흰 배경 ≥3:1 (코드 단계 수치 확인, 미달 시 상향).
4. **사람 시각 검증:** 데스크톱 클릭 선택 유지 / 빈 배경 해제 / 모바일(호버 없는 환경) 클릭 동작 / 노란색 사라짐 / 두 화면 동일 동작.

## 7. Task 분해 (커밋 단위)

- **Task 1** — `useConceptGraph.js` 추출(동작 동치, 색·크기는 기존값 유지). ConceptView·ResultView가 컴포저블 사용하도록 전환. *순수 리팩토링 커밋(거동 불변)으로 분리 → 회귀 표면 격리.*
- **Task 2** — 색 체계 교체(D2): `GRADE_COLORS` 3색×명도, `getNodeColor` 매핑 갱신, 범례 바인딩, 노란색 퇴출. 대비 검증.
- **Task 3** — 인터랙션(D3) + 크기 상향: 클릭 선택 유지·빈배경 해제·호버 보조화, `nodeSize`/`fontSize` 상향.
- **Task 4** — docs: 로드맵 [Design] 항목 처리 표시 + 핸드오프 갱신.

> Task 1을 "거동 불변 추출"로 먼저 끊는 이유: 추출(구조 변경)과 동작 변경(색·인터랙션)을 한 커밋에 섞으면 회귀 원인 분리가 어렵다. 추출이 동치임을 먼저 확인한 뒤 동작을 바꾼다.
