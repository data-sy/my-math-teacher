# MMT 프론트 v2 — 얇은 프론트 스펙

> 정본 기획물(플로우 맵 + 와이어프레임 6종 + PRD + 접점 시트)에 **없는 기술 결정만** 기록한다.
> 기획물 재서술 금지 — 화면·인터랙션·API 계약의 정본은 `docs/design/v2/` 그대로.
> 작성 2026-07-18 · 가정 로그 = `docs/assumptions.md`.

## 1. 스택 선정과 근거

| 결정 | 선택 | 근거 (6화면 MVP 무게 기준) |
|---|---|---|
| 프레임워크 | **React 19** | React 자체는 제품 결정(고정) — 버전은 현행 안정판(19) 채택. |
| 빌드체인 | **Vite** | 정적 dist 산출(접점 시트: prod = nginx SPA fallback 정적 서빙에 부합). dev 서버 기본 포트 5173 = CORS 허용 포트와 일치. 6화면 규모에 CRA/Next는 과체중(SSR 불요 — permitAll 콘텐츠 + SPA fallback 전제). |
| 언어 | **TypeScript** | API 계약(flow-map 표)의 응답 shape를 타입으로 고정해 mock↔실서버 전환 시 어긋남을 컴파일 타임에 잡는다. |
| 라우팅 | **react-router v6** (`createBrowserRouter`) | SPA fallback 전제와 일치하는 history 라우팅. 화면 6개 + 횡단 2개 규모에 표준 선택. |
| 서버 상태 | **TanStack Query v5** | chapters·concepts·queue 캐싱/재시도/로딩 상태 일원화. 진단 문답(next/preview)은 순수 mutation이라 Query 캐시 대상 아님 — mutation으로만 사용. |
| 클라 상태 | **React Context + localStorage (라이브러리 없음)** | 전역 상태 = 인증 토큰 + 진단 세션(answered-map)뿐. zustand/redux는 이 규모에 과체중. localStorage 스키마는 §3. |
| 스타일링 | **CSS Modules (plain CSS)** | 로우파이 그레이스케일의 충실 재현이 목표(비주얼 폴리싱 아님) — 디자인 토큰은 `:root` CSS 변수 한 벌이면 충분. Tailwind/CSS-in-JS는 의존성 무게 대비 이득 없음. |
| 그래프 렌더 | **Cytoscape.js** | 백지 선정 아님 — 와이어프레임 ⑥·④-B에 "Cytoscape 자리"·"기존 Cytoscape 자산 재사용 전제"로 명시된 기획 결정을 따름. 탭 선택·핀치 줌·팬·depth 기반 선명/흐림을 기본 제공. |
| mock | **MSW (Mock Service Worker)** | fetch 레이어를 그대로 두고 네트워크 경계에서 가로챔 → mock↔실서버 전환이 코드 무변경(핸들러 on/off). `VITE_ENABLE_MOCK=false`면 실서버로 통과. 개발 기본값 = mock on(백엔드 미기동 환경). |
| 테스트 | **Vitest + Testing Library** (핵심 로직 단위) + **Playwright** (390px 플로우 스모크) | 완주 조건 ④ "핵심 플로우 확인(mock 가능)"의 자동화 수단. 전 화면 E2E 매트릭스는 MVP 과체중 — 핵심 플로우(①→②→③→④, 게이트 전환, ⑥ 탐색)만. |

## 2. 라우팅 구조

| 경로 | 화면 | 비고 |
|---|---|---|
| `/` | ① 홈 | 루트. |
| `/entry` | ② 영역 진입 | 진입 경로 무관 동일 화면(파라미터 없음 — 02 규칙). |
| `/quiz` | ③ 문답 | 세션(entry+answered)은 localStorage — URL 파라미터 없음. 세션 없이 직접 방문 시 `/entry`로 replace. |
| `/result` | ④ 결과 | 같은 화면 2상태 + 약점 0 — 상태는 컴포넌트 내부(§4). 재열람 진입 = `/result?view=saved`(홈 배너용). |
| `/login` | ⑤-A 로그인 **+ OAuth 콜백 수신** | 접점 시트 확정: 콜백 = `/login?token=<accessToken>` — 같은 라우트가 `token` 쿼리 존재 시 콜백 처리로 분기. `?from=home` = 홈 진입 변형(귀속 생략). |
| `/graph` | ⑥ 그래프 탐색 | `?conceptId=` 초기 선택(④ 약점 0 링크·검색 결과 공유용). |
| `*` | ⑤-B 풀스크린 에러 | 라우팅 404. 403 소유권도 이 화면 재사용(존재 숨김 톤). |

## 3. 상태·데이터 흐름

### 3.1 localStorage 스키마 (prefix `mmt.`)

| 키 | 값 | 근거 |
|---|---|---|
| `mmt.accessToken` | string | 접점 시트: 콜백 `?token=` 파싱 보관. 보관 방식은 설계 자유 → localStorage 채택(§6 가정 A-3). |
| `mmt.gradeSemester` | `{grade, semester}` | 02 검수 확정: 학년 소스 = 최초 방문 선택 → localStorage 기억. 서버 저장 없음. |
| `mmt.diagSession` | `{entry, chapterId, chapterName, answered: [{conceptId, know}], done: boolean, savedAt}` | F-2 answered-map. 매 답변 직후 저장. `done=false`만 ② 이어가기 대상(S1-A). `done=true`는 ④ 재프리뷰용 완주 맵(●1: 이탈 시 소실 허용 — ④ 체류 중 새로고침 복원용으로만 유지). |
| `mmt.pendingAttribution` | `"1"` | 게이트 CTA → OAuth 리다이렉트 → 콜백 복귀 후 "귀속+큐 생성을 이어서 하라"는 1비트 플래그. |

- `entry` = `{scope: 'chapter', chapterId}` 또는 `{scope: 'full', schoolLevel}` (② escape (b)).
- 서버 상태(chapters·concepts·queue·재열람 진단)는 전부 TanStack Query 캐시 — localStorage 복제 금지.

### 3.2 인증 흐름 (접점 시트 사실의 구현)

- OAuth 진입 = `location.href = {API_BASE}/oauth2/authorization/{provider}` (XHR 아님 — 페이지 이동).
- `/login?token=` 수신 → 토큰 저장 → `pendingAttribution` 있으면 귀속(`POST /diagnosis`)→큐 생성(`POST /learning-queues`)→`/result`(④-B), 없으면(홈 변형) `/`로.
- API 클라이언트: `Authorization: Bearer` 헤더 자동 부착. 401 응답 시 1회 `POST /auth/reissue`(`credentials: 'include'` — 쿠키 refreshToken) 후 원요청 재시도, 재실패 시 로그인 필요 상태로 전파(⑤ 카탈로그 401 행: 인라인 안내 → 재로그인 → 보던 화면 복귀 = `/login?return=<path>`).
- dev에서 실 OAuth 완주 불가(콜백 도메인 하드코딩 — 접점 시트) → mock 모드에선 OAuth 진입을 가짜 콜백 리다이렉트(`/login?token=mock-…`)로 시뮬레이션(A-4).

### 3.3 mock 레이어

- MSW 핸들러가 flow-map API 표의 전 엔드포인트를 구현. mock 지식그래프(중학 수학 DAG, 단원 6개·개념 ~30개)를 단일 모듈로 두고 frontier/next/preview/queue가 **같은 데이터에서 결정론적으로** 파생 — preview == 귀속 결과(결정론 계약)를 mock에서도 보존.
- 적응형 순회 mock 규칙: "알아요" = 선수 폐쇄 제거, "몰라요" = 직계 선수 push. 서버 몫 로직이지만 mock이 흉내 내야 ③의 "짧아지는 체감"·④ 결과가 성립.
- 에러 시뮬레이션: URL 쿼리 `?mockError=429|400|failsoft` 등 개발용 스위치(핸들러에서 해석) — 에러 카탈로그 전 행을 수동 확인 가능하게.

## 4. 컴포넌트 구조

```
web-v2/src/
  api/        client.ts(fetch 래퍼+토큰+reissue) · types.ts(계약 타입) · endpoints.ts · queryKeys.ts
  mocks/      handlers.ts · graph-data.ts(mock DAG) · browser.ts
  auth/       AuthContext.tsx(토큰 보관·login/logout) · useAuth.ts
  session/    diagSession.ts(localStorage CRUD) · useDiagSession.ts
  components/ ConceptGraph.tsx(Cytoscape 래퍼 — ⑥·④-B 공유, pathHighlight prop만 차이)
              InlineError.tsx · Toast.tsx · BottomSheet.tsx · ProgressBar.tsx(후퇴 금지 내장)
  screens/    Home · Entry · Quiz · Result(3상태) · Login · GraphExplore · NotFound(⑤-B)
  styles/     tokens.css(그레이스케일 변수) · 화면별 *.module.css
```

- ④ 3상태는 Result 내부 분기: `empty`(weakCount=0) / `free`(비저장) / `gated`(userTestId 보유·재열람) — "같은 화면 2상태" 확정의 코드 표현.
- ConceptGraph 조작 규약(탭 선택·핀치 줌·호버 0·depth≤2 선명/흐림)은 ⑥ 정본을 컴포넌트에 넣고 ④-B는 하이라이트만 추가(06 규칙 "같은 컴포넌트" 준수).

## 5. 에러 처리 전략

- 카탈로그(05 ●6 표)가 정본 — 구현 원칙만: **에러는 발생 지점의 컴포넌트 상태**(인라인 우선), 풀스크린은 라우팅 404·403뿐. 전역 에러 바운더리는 최후 안전망으로만(⑤-B 재사용).
- fail-soft: preview 응답의 `urgency` 결측(null)을 타입에 반영 — 배지 "—" + 스트립 노출 + blocked 수 순 정렬로 대체(04 ●3).
- 429/400/네트워크: 요청 실패를 화면별 인라인 컴포넌트로 — 답변 맵은 이미 localStorage에 있으므로 복구 액션은 "다시 시도"(동일 요청 재전송)가 기본.

## 6. 이 스펙의 가정

개별 가정은 `docs/assumptions.md`에 기록(무엇을·근거·뒤집는 법). 스펙 레벨 요약: 토큰 보관 = localStorage(A-3), mock OAuth = 가짜 콜백(A-4), 시기 추정 진도표 = mock 데이터 내 하드코딩 표(A-5 — R6 확정대로 soft 제안일 뿐).

## 7. 디자인 토큰 — 하이파이 "틸 그로스" (2026-07-18 확정)

방향 결정 = 블라인드 전문가 평가(격리 세션·순서 카운터밸런스, `docs/design/🤖-하이파이-BC-블라인드-평가-프롬프트.md`) + 사용자 확정. 평가 기준 = 인터넷 공개 시 클릭 유발·유입 확보. 근거 아카이브 = `docs/design/eval-results/`.

- 정본 = `web-v2/src/index.css :root`. 중립색은 녹색 기 그레이 틴트(ink `#14231F` · sub `#5F6E67` · line `#CDD8D2` · paper `#FBFBF7` · soft `#F0F3F0`), 액션·진척·선택은 틸(primary `#0E7A6C` · deep `#0B6459` · accent `#0E9F8C` · accent-soft `#DFF2EC`).
- **시급도 색 문법(정직성 원칙, 시안 불변 조건):** 상 = 빨강(`#BE3A25`/`#FBE7E4`) · 중 = 호박(`#9C6B10`/`#FDF2D9`) · 하·결측 = 회색. 틸로 물들이지 않는다.
- Cytoscape 는 CSS 변수를 못 읽어 `ConceptGraph.tsx` 상단에 동일 값 상수 복제 — 토큰 변경 시 두 곳 동기화.
- 적용 원칙: 이번 단계는 팔레트 스왑만(레이아웃·보더 두께·위계 구조 불변). 형태 폴리싱(③ 버튼 강조 차등·히어로 실렌더·OAuth 브랜드 버튼)은 별도 이월분.
