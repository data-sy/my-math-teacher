# Vue→React 이주 타당성 & 견적

> 작성: 시니어 프론트엔드 아키텍트 페르소나 (프롬프트 02 실행 결과)
> 작성일 기준: 2026-06-23 · 분석 대상: `web/` (Vue 3, ~4,745 LOC)
> **이 문서는 코드를 바꾸지 않는다.** 이주 여부 판단 + 리소스 견적 + 단계 계획만 담는다.
> 규모·매핑·의존 사용처는 추측이 아니라 grep/read 실측으로 보강했다 (정정 사항은 §0 참조).

---

## ① 요약

- **이주 권장 여부:** **지금은 잔류(Vue 유지). 이주는 실배포·안정화 이후 별건으로 재검토 — 현 시점 이주는 사용자 가치 0, 리스크·시간만 발생.**
- **견적 한 줄(이주한다고 가정 시):** AI 자율주행 기준 **현실 14~16 세션 / 낙관 10 / 비관 22**, 세션당 사람 개입 2~5회, 벽시계 현실 **3~4주(파트타임)**. 비용의 무게중심은 cytoscape 2개 뷰와 PrimeVue 165+ 인스턴스 교체, 그리고 auth 등가성 검증.

핵심 한 줄 요약: **"할 수 있다. 하지만 launch 직전 1인 프로젝트에서 지금 할 일은 아니다."**

---

## ⓪ 실측 정정 — 프롬프트 매핑표의 빈틈 (먼저 읽어라)

프롬프트 02의 의존성 표는 좋은 출발점이지만, grep/read로 검증하니 **3건이 사실과 다르다.** 견적의 토대라 먼저 못 박는다.

| 항목 | 프롬프트 가정 | 실측 결과 | 견적 영향 |
|---|---|---|---|
| `chart.js` | "진단 차트", `react-chartjs-2`로 이주 | **죽은 의존성.** `main.js`에서 import만 되고 어떤 템플릿에서도 렌더 안 됨. 진단 차트 UI는 실제로 존재하지 않음 | **이주 대상 아님 → 제거 대상.** 견적에서 뺀다 |
| `vue-cookies` | refreshToken 쿠키 처리 → `js-cookie` | **죽은 의존성.** 코드에서 import·`$cookies` 참조 0건. refreshToken은 `withCredentials:true`로 브라우저가 자동 처리 | 이주 대상 아님 → 제거 대상 |
| auth 인터셉터 | "인터셉터 refresh retry" 등 복잡 | **response 인터셉터 없음.** 401 자동 재시도 로직 부재(코드에 `// 다음에 구현하기` TODO). 갱신은 **요청 전 만료 1분 체크(proactive)** 방식, `AuthService.initializeStore`가 앱 부팅 시 1회 | 등가화가 *더 쉬움*. 단 "없는 401 처리"를 이주하며 새로 만들지 말 것(스코프 크리프) |

추가 실측:
- **`vue3-markdown`** — 프롬프트 본문 깊이분석엔 빠졌으나 5개 뷰에서 `<VMarkdownView>` 15회 사용. `react-markdown` 대응, 저난이도지만 5곳.
- **라우터 가드 없음** — 전역 `beforeEach` auth 가드 부재. 보호 로직은 각 뷰가 `localStorage.getItem('accessToken')`을 마운트 시 직접 확인하는 산발 패턴. (이주 시 react-router에 가드를 새로 넣을지 결정 필요.)
- **Vuex 스토어가 사실상 비어 있음** — 34 LOC, state 필드 **단 1개(`accessToken`)**, getter 1·mutation 1·action 2. `mapState/mapGetters` 사용 0건. 즉 "Vuex 재작성"의 부담은 스토어 자체가 아니라, 그것을 구독하는 **6개의 `watch(() => store.state.accessToken)`** 부수효과다.
- **baseURL `http://localhost:8080` 하드코딩** (`api.js:4`) — 이주와 무관하게 갚아야 할 빚. 실배포 전 `.env` 이관 필요(이주하든 안 하든).

---

## ② A. 이주 여부 판단 (trade-off)

### 옵션 1 — 잔류 (Vue 유지) · **권장**

**비용:** 0. 디자인 리뉴얼(프롬프트 01)은 Vue에서 그대로 가능.

**무엇을 잃나:**
- React 생태계/채용 풀/커뮤니티 — *1인 프로젝트라 채용은 현재 무의미, 미래 옵션일 뿐.*
- AI 코딩 친화도 — React가 학습 코퍼스 밀도가 높아 LLM이 신규 React 코드를 더 안정적으로 짠다. **다만 이건 "신규 작성" 얘기지 "기존 자산 이주"가 아니다.** 이주 자체는 등가성 버그가 몰리는 작업이라 코퍼스 밀도의 이점이 상쇄된다.
- PrimeReact/shadcn 등 React 전용 DS 선택지 — 단 PrimeVue도 현역이라 당장의 결핍 아님.

**진단:** 현 코드는 **건강하다.** Options API가 아니라 Composition API(`ref`/`watch`/`onBeforeUnmount`)로 일관, 스토어는 미니멀, axios는 `useApi()` 한 곳에 격리(직접 import 위반 0건). "기술 부채라서 갈아엎어야 한다"는 서사가 성립하지 않는다.

### 옵션 2 — 이주 (React 전환)

**무엇을 얻나:** 위 "잃는 것"의 역. 장기 옵션성(생태계·AI 친화·DS 선택지).

**무엇을 거나:**
- **모든 뷰 10개 리라이트** (~3,602 LOC) + 레이아웃 셸 6개(PrimeVue Sakai 템플릿, Vue 전용).
- **등가성 리스크 3대장:** cytoscape 라이프사이클, auth 플로우, Vue 반응성→React 명시 의존성. (§④)
- **실배포 직전이라는 타이밍** — 지금 이주는 사용자에게 보이는 가치가 0인데 회귀 리스크는 실서비스로 직결.

### 옵션 3 — 부분/점진 (스트랭글러)

**현실성: 낮음.** 이유:
- 단일 SPA + 단일 Vuex 스토어 + 공유 레이아웃 셸 구조 → Vue와 React 두 런타임을 동시 마운트하고 스토어를 브리지하는 비용이, 작은 앱(~4.7k LOC)을 통째 옮기는 비용을 능가한다.
- "신규 화면만 React" 전략은 **신규 화면이 없다.** 리뉴얼은 기존 화면의 재디자인이지 신규 추가가 아님.
- 1인 개발에서 두 프레임워크 멘탈모델을 동시 유지하는 인지비용이 가장 비싸다.

→ 만약 이주한다면 스트랭글러가 아니라 **브랜치 위 빅뱅 + 배포 레벨 병행**(§⑤)이 맞다.

### 권장안 (결정은 사용자 몫)

> **실배포까지 Vue 유지. 디자인 리뉴얼도 Vue에서 수행.** 이주는 launch 후, "React 전용 라이브러리가 정말 필요" 또는 "협업자 합류로 채용 풀이 변수"가 되는 시점에 *그때* 별도 마일스톤으로. 지금 이주의 ROI는 음수다 — 비용은 14~16 세션, 사용자 편익은 0, 리스크는 실서비스 auth/그래프 회귀.

이 권장은 "이 제품(소규모 교육 SPA)·이 개발자(1인)·이 시점(실배포 직전)" 3중 맥락의 산물이다. 셋 중 하나라도 바뀌면(예: 팀 합류, launch 완료, React 라이브러리 의존 발생) 재평가 대상이다.

---

## ③ B. AI 자율주행 견적 (이주한다고 가정 시)

### 견적 단위 정의
- **세션** = Claude Code 컨텍스트 1회에 담길 작업 단위.
- **사람 개입** = 세션당 검수/결정/막힘 해소 횟수.
- 가정: chart.js·vue-cookies는 **제거**(이주 안 함). DS는 §⑥에서 갈림 — 아래 표는 **PrimeReact 유지(부품명 패리티 최대)** 가정. Tailwind/shadcn 전환 시 뷰당 +30~50% 가산.

### 난이도 등급별 인벤토리

| 작업 단위 | LOC | 난이도 | 세션 | 사람 개입/세션 | 비고 |
|---|---:|---|---:|---:|---|
| 스캐폴드 (Vite+plugin-react, eslint/prettier, 엔트리, 라우터 골격, baseURL→.env) | — | 기계적 | 1 | 1~2 | 빌드체인은 Vite 유지라 거의 그대로 |
| **State + Auth 코어** (Vuex→Zustand/Context, `api.js` 인터셉터, `AuthService.reissue`, `OauthLogin` 콜백, `initializeStore`) | ~190 | **고위험** | 1.5~2 | 4~5 | 등가성 검증이 핵심. §④-2 |
| 레이아웃 셸 6종 (AppLayout/Topbar/Sidebar/Menu/MenuItem/Footer, Sakai) | ~590 | 보통 | 1~1.5 | 2~3 | Sakai는 Vue 전용 → PrimeReact Sakai 포트 참조 |
| OauthLogin(24)·ErrorView(30)·HomeView(101) | ~155 | 기계적 | 1 | 1~2 | OauthLogin은 auth 세션과 함께 |
| SignUpView(283) | 283 | 보통 | 1 | 2~3 | 폼·Password·Calendar·검증 watch |
| UserEditView(396) | 396 | 보통 | 1 | 2~3 | 폼·Calendar·계정삭제·store commit |
| RecordView(435) | 435 | 보통 | 1 | 2 | Listbox·markdown·store watch ×2 |
| DiagView(509) | 509 | 보통 | 1~1.5 | 2~3 | + html2pdf(§④-5) |
| PersonalView(479) | 479 | 보통 | 1 | 2~3 | + html2pdf |
| **ConceptView(613) · cytoscape** | 613 | **고위험** | 1.5~2 | 4~5 | §④-1 |
| **ResultView(732) · cytoscape + 누적뷰** | 732 | **고위험** | 2 | 5 | 가장 큰 단일 단위. 누적 토글 취약 |
| 횡단 마무리 (vue-gtag→react-ga4, markdown 폴리시, 죽은 의존 제거, 401 처리 정책 결정, e2e auth 패리티 점검) | — | 보통 | 1 | 3 | |
| 안정화/회귀 버퍼 | — | — | 1~2 | — | cytoscape·auth 토끼굴 대비 |

### 3점 견적 (세션)

| | 세션 수 | 가정 |
|---|---:|---|
| **낙관** | ~10 | cytoscape가 1회에 패리티, auth 등가성 즉시 통과, DS는 PrimeReact 유지, StrictMode 이슈 선제 회피 |
| **현실** | **14~16** | cytoscape 2뷰 각 1.5~2세션, auth 검증 반복 1~2회, 폼/리스트 뷰 순항 |
| **비관** | ~22 | StrictMode 이중 init·klay 재레이아웃·누적뷰 노드중복 디버깅, auth 토큰 회전 미묘버그, Sakai 포트 스타일 어긋남 |

### 벽시계 추정
가정: 하루 1~2세션 + 검수 동반(파트타임).
- 낙관 ~1.5주 / **현실 3~4주** / 비관 6주+.

### 자동화 vs 사람 판단 비율
- **AI 자율 ~60~70%:** 라우팅, PrimeVue→PrimeReact 부품 교체(165+ 인스턴스지만 기계적), markdown, 쉬운 뷰, gtag.
- **사람 게이트 ~30~40%:** auth 등가성 판정, cytoscape 라이프사이클 검수, store 파생상태 누락 점검, DS 방향 결정, 누적뷰 동작 패리티. (§④의 항목들이 곧 게이트 지점.)

### 토큰/비용 감각 (자릿수만)
뷰 세션당 대략 100~300k 토큰. 전체 이주 **단순 백만 단위(수 M)** 토큰 규모. 정밀 견적 아님 — 의사결정엔 "세션 수×검수 시간"이 토큰보다 지배적 비용.

---

## ④ C. 고위험 구역 + 완화책

### C-1. Cytoscape 지식 그래프 라이프사이클 — **최상위 리스크**

**실측 현황 (ConceptView ~232 LOC / ResultView ~294 LOC, 합 ~526):**
- `cytoscape.use(klay)`가 **모듈 로드 시점**에 실행 (`ConceptView.vue:127`, `ResultView.vue:20`).
- 인스턴스가 **모듈 레벨 `let cy = null`** + `cyElement` 템플릿 ref (`:128-129`, `:21-22`).
- init이 `onMounted`가 아니라 **async 함수 안**에서(`showKnowledgeSpace()` `:299`, `showTree()` `:364`) 버튼 클릭 트리거로 발생.
- 정리는 `clearCy()`가 `cy.destroy()` 호출(`:404`, `:440`), `onBeforeUnmount`에 바인딩(`:375`, `:444`).
- **캐스케이드:** `cy.on('tap','node', …)`가 `clickedNodeId.value`를 변이(`:348`, `:412`) → 그 ref의 `watch`가 API 호출/스크롤 부수효과(`:381-391`, `:451-480`).
- ResultView는 **누적뷰**(토글 플래그로 여러 개념 트리를 replace 아닌 accumulate, `:449`).

**React 이주 시 터지는 곳 + 완화:**

| 위험 | 증상 | 완화책 |
|---|---|---|
| **React 18 StrictMode 이중 effect** | dev에서 effect가 2번 호출 → cy 2번 init → 유령 캔버스/리스너 중복/메모리 누수 | cy를 `useRef`에 보관(절대 state 아님). init을 effect에 넣는다면 cleanup에서 반드시 `cy.destroy()`; 또는 현행처럼 **명시적 핸들러 트리거 유지**(자동 init 회피). dev에서 이중마운트 의식적으로 테스트 |
| `cytoscape.use(klay)` 모듈 로드 실행 | HMR/재마운트 시 중복 등록 경고 | 모듈 최상단 1회 + 등록 멱등 가드. 또는 앱 부트 시 1회 |
| 모듈 레벨 `let cy` | 인스턴스가 컴포넌트 인스턴스 간 누수 | `useRef(null)`로 컴포넌트 스코프화 |
| 이벤트→ref→watch 캐스케이드 | watch를 `useEffect`로 옮길 때 의존성 누락(stale) 또는 과다(루프) | 핸들러는 `setClickedNodeId`만, fetch는 `useEffect([clickedNodeId])`로 분리. `exhaustive-deps` 린트 강제 |
| klay 재레이아웃 | 데이터 변경 시 레이아웃 재실행 타이밍 | 재init 전 `destroy()` 보장(현행 `clearCy` 패턴 유지). 컨테이너 ref 마운트 확인 후 init |
| ResultView 누적 토글 | 노드 중복/순서 의존 깨짐 | 누적 로직 포팅 후 노드 de-dup·순서 패리티를 수동 검증 항목으로 명시 |

> **참고(좋은 소식):** cytoscape 자체는 프레임워크 무관 라이브러리라 그래프 *로직*(스타일·focus·dim 헬퍼 ~117 LOC)은 거의 그대로 재사용 가능. 위험은 전부 **라이프사이클 경계**에 있다. `react-cytoscapejs` 래퍼보다 직접 `useRef`+effect 제어를 권장(래퍼가 누적뷰·커스텀 이벤트와 충돌할 여지).

### C-2. 인증 플로우

**실측 현황:**
- `api.js`: baseURL 하드코딩, `withCredentials:true`, **request 인터셉터만**(localStorage→`Bearer` 주입). response 인터셉터/401 재시도 **없음**.
- `AuthService`: `isExpired`(만료 1분 전 판정) → `reissue`(POST `/api/v1/auth/reissue`, body에 만료 access[CSRF 2차방어], refresh는 Set-Cookie 회전). `initializeStore`가 앱 부팅 시 1회 만료체크·갱신.
- `OauthLogin.vue`: URL `?token=` 쿼리의 access를 그대로 store에 commit 후 `/`로 리다이렉트.
- accessToken = localStorage(정본) + Vuex 이중. **인터셉터는 store가 아니라 localStorage를 직접 읽음** → 동기화 지점 주의.

**위험 + 완화:**
- **등가성 깨지면 보안/세션 버그.** 완화: 인터셉터(Bearer 주입)·`withCredentials`·reissue POST를 1:1 포팅. localStorage를 정본으로 유지(현행 그대로).
- **스코프 크리프 함정:** 401 응답 처리가 *원래 없다*(TODO). 이주하며 "이왕이면 401 재시도 추가"를 끼우면 동작 변경 + 회귀 면적 확대. **별 이슈로 분리**, 이주는 현행 동작 보존만.
- **OAuth2 리다이렉트:** react-router에서 콜백 라우트가 쿼리 파싱→토큰 저장→리다이렉트하는 타이밍(마운트 1회 effect, 중복 실행 가드).
- **검증 방법(등가성 매트릭스):** ① 로그인→localStorage 토큰 ② 인증 요청 Bearer 부착 ③ 만료 1분 전 부팅→reissue 성공→새 access ④ reissue 실패→토큰 클리어·로그아웃 ⑤ OAuth 콜백→store→리다이렉트 ⑥ 로그아웃→localStorage·헤더 제거. 6케이스 수동 골든패스.

### C-3. Vuex → React 상태

**실측:** 스토어는 1필드(`accessToken`)라 *그 자체*는 사소. 진짜 작업은 **구독 부수효과**다.
- **6개 컴포넌트가 `watch(() => store.state.accessToken, …)`** (AppTopbar/DiagView/RecordView/ResultView/PersonalView/UserEditView) — 토큰 변화에 로그인상태/데이터 리로드 반응.
- 전체 `watch` 26개, `computed` 17개. computed 대부분은 **자산 경로(상수)** → React에선 그냥 const, 사소. UI 파생(`layout.js`의 `isSidebarActive`)만 `useMemo`.

**위험 + 완화:**
- watch→`useEffect` 변환에서 **의존성 배열 실수**(누락=stale, 과다=무한루프). 특히 Diag/Record/Personal/Result의 "accessToken 변하면 API 재호출" watch.
- 완화: Zustand selector(또는 Context+custom hook)로 토큰 구독을 한 곳에 모으고, `exhaustive-deps` 린트로 의존성 검증. getter 미사용이라 직접 state 접근 → 매핑은 단순하나 리팩토링 시 산발 접근점 추적 필요.

### C-4. 반응성 패러다임 차이 (횡단)

Vue 자동 추적 vs React 명시 의존성. AI가 가장 흘리기 쉬운 곳. 완화: `eslint-plugin-react-hooks` exhaustive-deps **에러 레벨**로, 모든 `useEffect`/`useMemo`를 세션 종료 전 사람 검수 게이트.

### C-5. PDF 생성 (html2pdf)

**실측:** `htmlToPdf.js` composable이 DiagView·PersonalView 2곳에서 호출. DOM 대상 요소 마운트 의존.

**위험 + 완화:** React에서 print 대상 ref가 **렌더 완료 후** 존재해야 함. 완화: effect 경쟁이 아니라 **이벤트 핸들러(버튼 클릭) 시점**에 호출(대상이 이미 마운트된 상태). 조건부 렌더 대상이면 ref null 가드.

---

## ⑤ D. 단계 계획 (이주 시)

### 스트랭글러 vs 빅뱅 → **브랜치 위 빅뱅 + 배포 레벨 병행**

근거: §②-옵션3. 단일 SPA·단일 스토어·공유 셸에서 듀얼 런타임 스트랭글러는 비용 역전. 대신:
- **병행/롤백 단위 = 인앱 피처 플래그가 아니라 빌드/배포 레벨.** 기존 `web/`(Vue)를 손대지 않고 `web-react/`(가칭)를 나란히 구축, **React 빌드가 패리티 통과 전까지 nginx/배포는 Vue 빌드를 서빙.** 즉시 롤백 = Vue 빌드로 되돌리기(브랜치/배포 토글). 이게 레포의 "즉시 롤백 가능한 배포 단위" 요구를 프론트 빅뱅 맥락에서 만족하는 형태다.
- 레포 규율 준수: **auth·state·cytoscape를 건드리는 세션 진입 전 `/analyze-before-change`** 필수(참조점·영향 테스트·롤백 시나리오 선조사 후 착수).

### 세션 단위 실행 순서 + 각 단계 done 정의

| # | 단계 | done 정의(검증) | 롤백 단위 | A-B-C 게이트 |
|---|---|---|---|---|
| 0 | 죽은 의존 제거(chart.js·vue-cookies) + baseURL→.env (**이주 무관, 선행 가능**) | 빌드 통과, 기능 무변화 | 커밋 | — |
| 1 | 스캐폴드(Vite+React, 라우터 골격) | `npm run dev` 부팅, 빈 라우트 10개 네비 | 브랜치 | — |
| 2 | **State+Auth 코어** | §④-2 6케이스 등가성 매트릭스 PASS | 브랜치 | **/analyze-before-change** |
| 3 | 레이아웃 셸 6종 | 토픽바/사이드바/메뉴 네비·로그인상태 표시 패리티 | 커밋 | — |
| 4 | 쉬운 뷰(Home/Error/Oauth) | 화면 렌더·OAuth 콜백 동작 | 커밋 | — |
| 5 | 폼 뷰(SignUp/UserEdit) | 검증·제출·계정삭제 패리티 | 커밋 | — |
| 6 | 리스트/리포트 뷰(Record/Diag/Personal) | 데이터 로드·markdown·PDF 출력 패리티 | 커밋 | (PDF 시 주의) |
| 7 | **ConceptView (cytoscape)** | 그래프 init/destroy·tap·dim/focus·재진입 누수 없음 | 커밋 | **/analyze-before-change** |
| 8 | **ResultView (cytoscape+누적)** | 누적뷰 노드중복 없음·두 개념 선택 캐스케이드 패리티 | 커밋 | **/analyze-before-change** |
| 9 | 횡단 마무리(gtag, 401 정책 결정, e2e auth) | 전 화면 스모크 + auth 매트릭스 재실행 | 브랜치 | — |
| 10 | 컷오버 | React 빌드 배포 토글, Vue 빌드 롤백 가능 상태 유지 | 배포 토글 | — |

> 피처 플래그/병행: 레포의 백엔드형 `mmt.migration.*` 플래그와 별개로, 프론트 빅뱅의 병행 축은 **두 빌드 산출물(dist) 중 무엇을 서빙하느냐**다. 컷오버 후 일정 기간 Vue 빌드를 롤백 가능 상태로 보존.

---

## ⑥ E. 디자인 리뉴얼(01)과의 교차

**DS 결정이 이주 비용을 가르는 지점:**
- **PrimeReact 유지** → 부품명 패리티가 높아(Button/Dialog/Listbox/DataTable 등 165+ 인스턴스가 거의 1:1) 뷰당 마크업 포팅이 최저난이도. §③ 표가 이 가정.
- **Tailwind/shadcn 전환** → PrimeVue를 어차피 걷어내므로, 165+ 인스턴스를 *전부 재작성*. 뷰당 +30~50% 가산. 단 "어차피 뜯을 거면 한 번에"의 논리 성립.

**리디자인을 이주와 같이 할지 / 따로 할지:**
- **같이(권장 조건부):** *이주를 한다면*, 어차피 모든 뷰를 리라이트하므로 그 손길에 리디자인을 얹는 게 뷰를 두 번 건드리지 않는 길. 단 리스크가 한 곳에 집중(회귀 면적↑) — auth·cytoscape 고위험 세션엔 디자인을 섞지 말고 **기능 패리티 먼저, 스타일 나중** 순서로.
- **따로:** 각 단계 디리스크되지만 모든 뷰를 두 번 churn.
- **그러나 §②의 권장(잔류)을 따른다면:** 리디자인은 **Vue에서 독립 수행**이 정답. 이주 비용 0으로 리뉴얼 가치를 즉시 실현하고, 이주는 미래 옵션으로 남긴다.

---

## ⑦ 사용자가 결정해야 할 열린 질문

1. **이주 자체 — go/no-go.** 본 분석 권장은 "launch까지 잔류". 동의하는가, 아니면 React 전용 요구(특정 라이브러리/협업자 합류)가 이미 있는가?
2. **타이밍.** 이주한다면 launch 전(리스크 실서비스 직결) vs 후(안정화 뒤 별 마일스톤)?
3. **DS 방향(01과 교차).** PrimeReact 유지 vs Tailwind/shadcn 전환 — §③ 견적이 여기에 종속된다.
4. **리디자인×이주 결합 여부.** 함께(뷰 1회 터치, 리스크 집중) vs 분리(2회 churn, 디리스크)?
5. **상태 라이브러리.** 이주 시 Zustand(가벼움, 현 스토어 규모에 적합) vs Context+hook vs Redux Toolkit(오버킬로 보임).
6. **401 처리 정책.** 현재 없는 response 인터셉터/자동 재시도를 이주 *전*에 Vue에서 먼저 갚을지, 이주 시 도입할지, 계속 보류할지. (스코프 분리 권장.)
7. **선행 부채(이주 무관).** `chart.js`·`vue-cookies` 제거와 baseURL `.env` 이관은 이주 결정과 무관하게 실배포 전 처리 — 지금 정리할까?

---

*본 문서는 분석·견적·계획만 담으며 코드를 변경하지 않았다. 규모·매핑은 grep/read 실측으로 보강했고, 프롬프트 매핑표 대비 정정 3건은 §0에 명시했다.*
