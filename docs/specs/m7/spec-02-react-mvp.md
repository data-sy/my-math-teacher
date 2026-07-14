# spec-02 · 프론트 — React 새로 짜기 + 모바일 MVP 크리티컬 패스

**상위 마일스톤:** [Milestone 7](../../milestones/milestone-7-product-pivot.md) — 제품 피벗. 밀스톤 D6(React 새로 짜기)·D7(모바일 퍼스트)·PRD §3.2(MVP 6화면)·UX 와이어프레임 8종(`docs/design/m7-*.html`)을 기술 설계로 구체화한다.
**대상:** 프론트엔드(스캐폴딩·크리티컬 패스·auth/API 레이어·그래프 포팅). 백엔드 계약은 [spec-01](spec-01-diagnosis-self-report-dkt.md)(확정), 링크 데이터는 spec-03.
**작업 브랜치:** 착수 시 `feat/m7-spec-02-react-mvp` (본 설계 문서는 `feat/m7-product-pivot` 에 동반)
**상태:** ✅ **spec 확정(2026-07-13)** — §7 결정 T1~T5 **사인오프 완료(전 항목 권장안 채택)**: Vite+React19+TS / TanStack Query+Zustand / Tailwind(+Radix) / React Router v7 / `web-react/` 병행 → 코드 단계 진입 가능
**선행:** spec-01 확정(2026-07-13) — 본 spec 의 화면들이 spec-01 API 계약을 소비. UX SSOT = `m7-flow-map.html`(전이) + 6화면 와이어프레임(③=A안 확정).

---

## 1. 범위

`web/`(Vue 3)를 **마이그레이션 없이 React 로 새로 짠다**(D6). 초기 범위 = **최소 런치 MVP 6화면**(PRD §3.2). 백엔드·배포(blue-green·nginx 정적 서빙)·그래프 데이터 무변경.

### In
1. **스캐폴딩** — 빌드 체인·상태관리·UI 스타일링·라우팅 스택 확정(§7 T1~T4) + 디렉토리 전략(T5).
2. **크리티컬 패스 6화면** — ①홈 → ②영역 진입 → ③문답(A안: 정적 카드+하단 고정 2버튼) → ④결과(무료/게이트 2상태) → ⑤로그인/에러(횡단) → ⑥그래프 탐색(병렬 진입).
3. **auth/API 레이어 재구현** — OAuth 3사 진입·콜백, 토큰 수명주기(reissue), HTTP 래퍼. 현행의 결함 2건(401 무대응·에러 상태 유실)은 **승계하지 않고 개선**(§4.3).
4. **F-2 진척 복구** — 익명 answered-map localStorage 스키마 + 게이트 재제출 배선(spec-01 S1-A 소비).
5. **Cytoscape 그래프 포팅** — `useConceptGraph.js`(klay·GRADE_COLORS·focus/dim·뷰포트 컨트롤)를 React hook 으로. ⑥ 호버 0 → 탭 바텀시트(와이어프레임 확정).
6. **모바일 퍼스트 셸** — 와이어프레임의 모바일 뷰포트 기준. 데스크톱은 반응형 하한만(PRD 비범위).

### Out (이번 범위 밖)
- **부가 화면** — 구 DiagView(학습지 선택)·RecordView(채점)·PersonalView(맞춤 학습지)·UserEditView·SignUpView 상세 폼·이력: 오픈 후 이식(D6). 구 문답 UI 는 ③이 대체.
- **html2pdf 포팅 — 하지 않음.** 사용처가 DiagView·PersonalView(학습지 PDF)뿐인데 둘 다 D4 로 폐기. *(밀스톤 §구성 spec 의 "html2pdf 포팅" 서술은 stale — 본 spec 이 정정.)*
- **Chart.js 포팅 — 대상 자체가 없음.** 현행 코드에 Chart.js 미사용(ResultView 주석 "차트 미사용, 숫자+CSS 막대"·package.json 부재). *(밀스톤·web/CLAUDE.md 서술 stale.)*
- **이메일/비밀번호 로그인·회원가입 폼** — ⑤는 OAuth 3사 동급이 정본(와이어프레임). 이메일 가입은 오픈 후 재검토.
- **다크모드·데스크톱 전용 최적화·정량 계측 파이프라인** — PRD 비범위.
- **spec-03 몫** — 외부 링크 데이터·큐레이션, 학습 경로 그래프 UI 상세.

---

## 2. 코드 사실확인 (2026-07-13 Explore — 재구현할 계약 vs 버릴 결함)

### 2.1 백엔드 계약 (무변경 — React 가 그대로 소비)

| 접점 | 사실 |
|---|---|
| OAuth 진입 | `GET /oauth2/authorization/{google\|naver\|kakao}` — 앵커 링크(같은 오리진, nginx 가 `/oauth2/`·`/login/oauth2` proxy). 백엔드 무변경 |
| OAuth 콜백 | 백엔드가 `/login?token=<accessToken>` 으로 리다이렉트(쿼리 파라미터) + refreshToken 은 Set-Cookie(HttpOnly). 현행 `OauthLogin.vue` 가 파싱 → localStorage |
| 토큰 갱신 | `POST /api/v1/auth/reissue` body `{grantType:"Bearer", accessToken:<만료 토큰>}` — 쿠키 refreshToken 검증, 새 accessToken 응답 + refresh 로테이트 |
| 로그아웃 | `DELETE /api/v1/auth/authentication` |
| accessToken | `localStorage` key `'accessToken'`, 요청 헤더 `Authorization: Bearer` (요청 인터셉터 주입) |
| baseURL | dev `http://localhost:8080` 하드코딩 / prod same-origin (`import.meta.env.PROD` 분기) — 유일한 env 사용처 |
| 배포 | nginx SPA fallback(`try_files … /index.html`) + `/api/v1/`·`/oauth2/` proxy, 2-stage Dockerfile(node build → nginx:alpine). **React 산출물도 동일 배포 구조를 재사용**(2-stage Dockerfile·nginx 설정 동형) — 구 Vue 이미지를 덮어쓰는 dist 교체가 **아니라 별도 이미지**로 빌드(§7 T5: 공존·이미지 전환 롤백) |
| 신규 소비 | spec-01 계약: `GET /chapters`(기존) · `POST /diagnosis/frontier`·`/next`·`/preview`(익명) · `POST /diagnosis`·`/learning-queues`·`PATCH …/done`(인증) |

### 2.2 승계하지 않는 현행 결함 (신규에서 개선)

- **401 자동 복구 없음** — 응답 인터셉터 부재, reissue 는 앱 부트 시 1회(`AuthService.initializeStore`, 60초 스큐)뿐. 사용 중 만료 = 조용한 실패.
- **에러 상태 유실** — `useApi()` 가 axios 에러를 문자열로 재포장해 status/응답 바디 소실 → 모든 호출자가 console.error 만. 학생 친화 에러(⑤)가 원천적으로 불가능한 구조.
- **로그인 상태 ad-hoc 중복** — 뷰마다 `localStorage.getItem !== null` + watch 를 복제(중앙 getter 없음).
- **Vuex `initializeStore` 액션 = dead code**(시그니처 불일치, mixin 이 서비스 직접 호출).

### 2.3 문서 stale (본 조사로 확인 — 거버넌스 문서라 수정은 별도 승인)

- web/CLAUDE.md: **Chart.js 3.3.2·vue-cookies = 미설치·미사용**(refreshToken 은 HttpOnly 쿠키라 JS 라이브러리 불요), **AppMenu/AppSidebar 부재**(실제 = AppTopbar·AppLearningSteps·AppBottomTabs·AppFooter), html2pdf 서술은 폐기 예정.
- 밀스톤 §구성 spec: "Cytoscape/**Chart.js/html2pdf** 포팅" 중 뒤 2개는 대상 아님(§1 Out).

---

## 3. 스캐폴딩 (결정 T1~T5 — §7)

MVP 규모(6화면·전역 상태 극소)에 맞춰 **가볍게**. 현행 전역 상태가 accessToken 하나뿐이었다는 사실(§2.2)이 규모 산정의 근거.

| # | 항목 | 권장 | 요지 |
|---|---|---|---|
| T1 | 빌드·언어 | **Vite + React 19 + TypeScript** | 커리어 수요(D6 근거)·1인 개발 회귀 안전망. 빌드 산출물 = 정적 dist(배포 무변경) |
| T2 | 상태관리 | **TanStack Query(서버 상태) + Zustand(클라 상태 2조각: auth·quiz 진행)** | Redux 급 전역 스토어는 과대(§2.2). Query 가 로딩/에러/캐시 표준화 → 에러 상태 유실 결함의 구조적 해결과 결합 |
| T3 | 스타일링 | **Tailwind CSS(+ 필요 시 Radix headless)** | 모바일 퍼스트 유틸리티·와이어프레임이 로우파이 커스텀이라 디자인 자유도 필요. PrimeReact 는 구 PrimeVue 톤 승계 위험 + 90개 전역 등록 반복 회피 |
| T4 | 라우팅 | **React Router v7** | 표준. 게이트는 라우트 가드가 아니라 **F-1 결과-시점 트리거**(§4.4) — 구조 단순 |
| T5 | 디렉토리 | **신규 `web-react/` 병행 → 런치 시 스왑** | **spec-01 §4.7 롤백이 "구 Vue 프론트"를 전제** → 구 `web/` 은 런치까지 무변경 생존 필수. 스왑·정리는 런치 후 별도 Task |

폰트 Pretendard 유지. GA(vue-gtag G-SELPGNP1WK)는 gtag 직접 또는 react-ga4 로 승계(라우터 연동, `anonymize_ip` 유지).

---

## 4. 크리티컬 패스 설계

### 4.1 라우트 맵 (플로우 맵 SSOT 반영)

| 라우트 | 화면 | 접근 | spec-01 소비 |
|---|---|---|---|
| `/` | ① 홈 — 단일 1차 CTA "무료 진단 시작", 그래프 히어로(미끼), 둘러보기 = 조용한 텍스트 링크(F-4) | 익명 | — |
| `/diagnosis/entry` | ② 영역 진입 — 스마트 default + ±후보 pick-list + escape 두 갈래(F-3) | 익명 | `GET /chapters`, `POST /diagnosis/frontier` |
| `/diagnosis/quiz` | ③ 문답 — A안(정적 카드+하단 고정 알아요/몰라요 2버튼), 진척 표시, D5 예시 | 익명 | `POST /diagnosis/next` |
| `/diagnosis/result` | ④ 결과 — 무료 존(헤드라인+카드 top-N+외부링크) / 게이트 뒤(학습 큐) 2상태 | 익명→게이트 | `POST /diagnosis/preview` → (로그인 후) `POST /diagnosis`·`/learning-queues` |
| `/login` | ⑤ OAuth 콜백 수신(`?token=`) + 게이트 보상 리마인드 화면 | 익명 | — |
| `/error` | ⑤ 에러 — 학생 친화 카피(무엇이·어떻게) | 익명 | — |
| `/concepts` | ⑥ 그래프 탐색 — 검색+상시 그래프+탭 바텀시트, 상시 "내 약점 진단하기" CTA(루프 회수) | 익명 | 기존 concepts nodes/edges/search/detail |
| `/queue` | 학습 큐 — 계단 리스트·이어서·self-mark(F-4 재진입, ④ 게이트 뒤 상태와 공유 컴포넌트) | 인증 | `GET /learning-queues/me`, `PATCH …/done` |

- 스마트 default(학년→시기 추정 단원)는 **프론트 로직 + 정적 진도표 데이터**(R6 — soft 제안일 뿐이므로 데이터 품질에 관대). 진도표는 `assets` 정적 JSON 으로 시작.
- 라우팅에 auth 가드 최소화 — 인증 필요 화면은 `/queue` 뿐이고, 게이트는 ④ 안의 CTA 트리거(§4.4).

### 4.2 F-2 — 익명 진척 복구 (localStorage 계약)

```
key: mmt.diagnosis.v1
val: { entry: {chapterId | scope:"full", schoolLevel?},
       answered: [{conceptId, known}], updatedAt }
```

- ③ 매 응답 후 저장. 재방문 시 존재하면 "이어서 하기" 제안(① 또는 ② 진입 시).
- **버전 키(v1)** — 스키마 변경 시 신 키로 마이그레이션 or 폐기(조용한 파손 방지).
- **answered[] 는 답변 순서 보존 계약(append-only)** — 재정렬·dedup·재직렬화로 순서를 흐트리지 않고, preview·재제출 페이로드도 이 순서 그대로 전달. spec-01 §4.4-2 시퀀스 순서 조건(DKT 순서 민감)의 프론트 측 필요조건.
- 게이트 통과(로그인) 후 `POST /diagnosis` 재제출 성공 시 **삭제**(정본이 서버로 이동). preview 와 귀속 결과의 동일성(**순서 포함**)은 spec-01 결정론 계약이 보장.

### 4.3 auth/API 레이어 재구현

- **HTTP 래퍼**: 단일 axios(또는 fetch) 인스턴스 + TanStack Query. 요청 인터셉터 = Bearer 주입(현행 계약 승계). **에러는 status·바디 보존한 typed error 로 전파**(§2.2 결함 개선) → Query 의 error 상태로 ⑤ 학생 친화 카피 매핑.
- **401 응답 인터셉터 신설**: 401 → `POST /auth/reissue`(만료 accessToken 동봉, 쿠키 refresh) → 성공 시 원 요청 1회 재시도, 실패 시 로그아웃 상태 전환(+ 진행 중 문답은 F-2 로 이미 안전). 부트 시 1회 갱신(현행)도 유지하되 인터셉터가 주 방어선. **동시 401 다중 재발급 방지**(재발급 promise 공유) 포함.
- **OAuth**: 진입 = 같은 앵커(`/oauth2/authorization/{provider}`) — 백엔드·nginx 무변경. 콜백 `/login?token=` 파싱 → 저장 → **returnTo 복원**: 게이트에서 출발했으면 ④로 복귀해 재제출·큐 생성 이어감(sessionStorage `returnTo`). 토큰 없는 콜백 = ⑤ 에러 화면(현행은 console.error 후 방치).
- **auth 상태 = Zustand 단일 스토어**(토큰·파생 isAuthenticated) — 뷰별 ad-hoc 복제(§2.2) 제거.

### 4.4 F-1 게이트 배선 (④ 결과 2상태)

```
[익명]  preview 결과 렌더 (헤드라인+카드+무료링크)
        └ 단일 1차 CTA "저장하고 학습 경로 시작하기" 탭
          → returnTo 기록 → ⑤ 로그인(게이트 보상 리마인드)
[인증]  ④ 복귀 → POST /diagnosis (localStorage answered-map 재제출)
        → POST /learning-queues → 게이트 뒤 상태(학습 큐) 렌더 → localStorage 삭제
```

- 카드별 "학습 경로 보기"는 2차 강등(단일 1차 규율). 이미 로그인 상태로 진단 완주 시 CTA 탭 → 게이트 화면 없이 곧장 귀속+큐.
- 재제출 실패(TF Serving 장애 등) = fail-soft 결과(spec-01 §4.7) 렌더 — 게이트에서 학생을 막다른 골목에 두지 않음.

### 4.5 Cytoscape 그래프 포팅

- `useConceptGraph.js` 는 **프레임워크 무관 로직**(klay 레이아웃·`GRADE_COLORS` WCAG 팔레트·focus/dim 선수(파랑)/후수(빨강) 하이라이트·zoomIn/Out/fit/reset) — React hook `useConceptGraph()` 으로 이식: `useEffect` 로 init/`cy.destroy()` 정렬(메모리 누수 규칙 승계), 컨테이너 ref + 렌더 후 초기화(`nextTick` 대응 = layout effect).
- **호버 코드는 이식하지 않는다** — ⑥ 확정안이 호버 0·탭 바텀시트. tap 핸들러 → 바텀시트(개념 상세 + "내 약점 진단하기" CTA).
- 노드/edge 조립·id dedup·고아 edge 필터는 현행 뷰 로직 승계(데이터 소스 4 endpoint 동일). ④ 게이트 뒤 학습 경로 그래프 표시의 상세는 spec-03(같은 hook 재사용 전제로 계약만 예약).

### 4.6 에러·엣지 (⑤)

- 에러 카피 원칙(와이어프레임): 무엇이 안 됐고 + 학생이 뭘 하면 되는지. HTTP status→카피 매핑 테이블 1곳(°401 게이트 리마인드 / 5xx·network "잠시 후 다시" / TF Serving fail-soft 는 에러가 아니라 결과 degrade).
- 진단 중 새로고침/이탈 = F-2 복구. `/diagnosis/*` 직접 진입(딥링크)인데 answered-map 없음 → ②로 유도.

---

## 5. 왜 이 순서

- spec-01 계약이 확정됐으므로 화면-계약 매핑(§4.1)이 고정점 — React 작업이 백엔드 구현(spec-01 코드 단계)과 **병렬 가능**(preview·next 는 계약 mock 으로 선개발).
- **계약 SSOT(병렬 개발의 드리프트 방지):** spec-01 §4.1/§4.3/§4.4 의 요청·응답 shape 가 정본 — 프론트는 이를 **TS 계약 타입 모듈**(예: `src/api/contracts.ts`)로 옮겨 **mock 과 실클라이언트가 같은 타입 정의를 참조**한다(T1 TypeScript 의 실효 조건). 백엔드 DTO 와의 드리프트는 §6 게이트 결정론 스냅샷으로 감지. *(한계 인지: 이 SSOT 는 드리프트의 **사후 감지**까지다 — Java DTO↔TS 타입을 컴파일 타임에 강제하는 **예방**은 spec-01 스키마→TS 타입 codegen 이 필요하며 T1 범위 밖 → **백로그 후보**.)*
- spec-03(링크·경로 노출)은 ④ 카드/큐 UI 위에 얹힘 — 본 spec 의 컴포넌트 계약이 선행.

---

## 6. 검증 (착수 후)

- **모바일 무결**(PRD §5.1): 6화면이 모바일 뷰포트에서 깨지지 않음 — dev 서버 기동 + 사람 시각검증 체크리스트 제시(기동까지 어시스턴트 몫).
- **OAuth 3사 실 로그인** 각 1회 + 콜백 returnTo 복원 + 401 인터셉터 재발급·재시도(만료 토큰 시뮬레이션).
- **F-2**: 문답 중 이탈→재방문 "이어서" / 게이트 후 localStorage 삭제 확인.
- **게이트 결정론**: preview 결과와 귀속 후 결과 화면이 동일(spec-01 §8 연동 — 프론트에선 스냅샷 비교) + 재제출 페이로드의 `answered[]` **순서가 localStorage 저장 순서와 동일** assert(§4.2 순서 보존 계약).
- **그래프**: ⑥ 탭 바텀시트(호버 0)·팔레트 범례 일치·unmount 시 destroy(누수), ④/⑥ 재진입 반복 시 메모리 안정.
- **빌드·배포 정합**: `npm run build` dist 를 기존 nginx 컨테이너 구조로 서빙(로컬 docker)해 SPA fallback·proxy 동작 확인 — 배포 레이어 무변경 증명.

---

## 7. 결정 (✅ T1~T5 사인오프 완료, 2026-07-13 — 전 항목 권장안 채택)

- **T1 — 빌드·언어:** **Vite + React 19 + TypeScript(권장)** — 커리어 수요·1인 개발 회귀 안전망·계약(spec-01 DTO) 타입화 / JS — 초기 속도는 빠르나 계약 드리프트를 런타임에야 발견. **사인오프 전제 = §8 선결 확인**(node 빌드 이미지 업그레이드 — React 19 는 node:14 빌드 불가; 실측 (a)(b) 완료, 잔여 1건).
- **T2 — 상태관리:** **TanStack Query + Zustand(권장)** — 서버 상태 표준화가 에러 개선(§4.3)과 결합, 전역 상태 실측 극소(§2.2) / Redux Toolkit — 과대 / Context 만 — quiz 진행·auth 재렌더 제어 번거로움.
- **T3 — 스타일링:** **Tailwind(+Radix headless)(권장)** — 모바일 퍼스트·커스텀 와이어프레임 자유도·커리어 수요 / PrimeReact — 이식감은 빠르나 구 톤 승계·번들 무게 / CSS Modules — 토큰 체계를 손으로 재구축.
- **T4 — 라우팅:** **React Router v7(권장)** / TanStack Router — 타입 안전하나 생태계 좁음.
- **T5 — 디렉토리 전략:** **A(권장) 신규 `web-react/` 병행, 런치 시 스왑** — spec-01 롤백 전제(구 Vue 생존) 충족, 구 web/ 무변경 / B `web/` 내 직접 재작성 — 롤백 = git revert 뿐이라 blue-green 이미지 롤백과 어긋남. **롤백 방식(§2.1 과 정합):** 구 Vue·신 React 는 **별도 이미지(태그)로 공존** — 롤백 = **이미지 전환**(재빌드·git revert 아님), spec-01 §4.7 롤백(`mmt.diagnosis.enabled=false` + 구 Vue 프론트)과 정합. *(스왑·구 web 정리는 런치 후 별도 Task + 밀스톤 R5 서사 보존.)*

---

## 8. 선결 확인 + Analyze-Before-Change 예고

### 선결 확인 — node 빌드 이미지 업그레이드 (T1 사인오프 전, 2026-07-13 실측)

React 19/Vite 최신은 node:14 에서 빌드 불가 → T1 의 전제 조건.

- **(a) 승인 필요 여부 — 실측 완료:** 규칙 **원문**(루트 CLAUDE.md 금지 사항, 2026-07-13 확인) = "`docker-compose.yml`의 서비스 구성은 ADR 없이 변경하지 말 것" — **스코프가 compose 파일의 서비스 구성으로 명시 한정**이며, 루트·api·web CLAUDE.md 에 "빌드/인프라 변경 일반" 류의 더 넓은 규칙은 없음(해석이 아니라 원문). compose `mmt-front` 는 `image: mymathteacher/mmt-front:1.0.0` **이미지 참조뿐**(빌드 지시 없음) — Dockerfile 빌드 스테이지 교체는 compose 무변경이라 **그 자체로는 ADR 불요**. 단 런치 스왑 시 compose 의 이미지 참조를 신규 태그로 바꾸는 건 compose 변경 → 그 시점에 ADR 판단(React 도입 ADR 에 포함 가능).
- **(b) 런타임 무영향 — 실측 완료:** `web/Dockerfile` = 2-stage(`node:14 AS build` → `nginx:1.21.4-alpine` 런타임, dist 만 `COPY --from=build`) 확인 — 빌드 스테이지 교체는 **산출물(dist) 생성에만 관여**, 런타임 서빙 이미지·nginx.conf·blue-green 롤백 이미지(구 Vue 태그)와 무관.
- **(잔여 — 착수 시 실측):** 프론트 **이미지(태그) 전환**(§7 T5 와 동일 용어)이 M6 배포 스크립트에서 실행되는 구체 경로 — 롤백의 실행 절차를 실측 후 확정.

### Analyze-Before-Change (합의 후 착수 시)

- 신규 디렉토리 생성이라 기존 코드 영향은 없음 — 분석 대상은 **경계면**: nginx.conf(빌드 산출물 경로·캐시 헤더)·GA·OAuth 리다이렉트 URI(콜백 경로 유지로 무변경 예상, 실측).
- **ADR: React 도입**(D6 사인오프 근거 정리 + T1~T5 확정 스택 기록 + 런치 스왑 compose 변경 판단) — 착수 시 `/write-adr`.
- web/CLAUDE.md 는 런치 후 스왑 시점에 재작성(§2.3 stale 정정 포함) — 거버넌스 문서라 사용자 승인 경유.
