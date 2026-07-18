# spec-02 · 프론트 — React 새로 짜기 + 모바일 MVP 크리티컬 패스 (v2 재설계 기반 재작성)

**상위 마일스톤:** [Milestone 7](../../milestones/milestone-7-product-pivot.md) — 제품 피벗. 밀스톤 D6(React 새로 짜기)·D7(모바일 퍼스트)·PRD §3.2(MVP 6화면)·**v2 와이어프레임**(`docs/design/v2/` — 전 화면 검수 사인오프 2026-07-17)을 기술 설계로 구체화한다.
**대상:** 프론트엔드(스캐폴딩·크리티컬 패스·auth/API 레이어·그래프 포팅). 백엔드 계약은 [spec-01](spec-01-diagnosis-self-report-dkt.md)(확정), 링크 데이터·경로 강조는 [spec-03](spec-03-learning-path-links.md)(확정 — 본 spec 의 hook·카드 계약 위에 얹힘).
**작업 브랜치:** 착수 시 `feat/m7-spec-02-react-mvp` (본 설계 문서는 `feat/m7-ux-redesign` 에 동반)
**상태:** ✅ **T1~T5 재확정 완료(2026-07-18 — 제로베이스 실빌드 + 사용자 확정, 🤖 빌드 문서 "재론 금지 결정").** 확정 결과가 §7 권장과 다른 3건: **T2 = TanStack Query + React Context**(Zustand 아님) · **T3 = CSS Modules**(Tailwind 아님) · **T5 = `web-v2/`**(web-react 아님). 라우트도 플랫 경로(`/entry`·`/quiz`·`/result`·`/graph`)로 확정. **구현 결정 정본 = [`frontend-spec.md`](frontend-spec.md)** — 본 문서는 백엔드 접점 사실(§2)·에러 카탈로그(§4.6)·검증 항목(§6) 참조용으로 유지. UX SSOT = `docs/design/v2/00-flow-map.html`(전이·API 매핑) + `01-home`~`06-graph`(화면별 검수 확정).
**선행:** spec-01 확정(2026-07-13, 구현 머지 PR #48) · spec-03 확정(2026-07-13) · v2 와이어프레임 전 화면 사인오프(2026-07-17).
**Supersedes:** 구 spec-02(2026-07-13 사인오프, **2026-07-16 `693d6fe` 로 삭제 — git 히스토리 보존**). 구 문서는 구 와이어프레임(`docs/design/m7-*`, read 전용 아카이브)을 UX SSOT 로 전제했으므로 v2 재설계에 맞춰 전면 재작성한다. 구 문서에서 시간을 안 타는 부분(백엔드 접점 사실확인 §2, 스택 결정 논거 §3, 결함 비승계 원칙 §2.2)은 재검토 후 본문에 흡수 — **본 문서만으로 자기완결.**

---

## 1. 범위

`web/`(Vue 3)를 **마이그레이션 없이 React 로 새로 짠다**(D6). 초기 범위 = **최소 런치 MVP 6화면**(PRD §3.2). 백엔드·배포(blue-green·nginx 정적 서빙)·그래프 데이터 무변경.

### In
1. **스캐폴딩** — 빌드 체인·상태관리·UI 스타일링·라우팅 스택(§7 T1~T4) + 디렉토리 전략(T5).
2. **크리티컬 패스 6화면(v2 확정)** — ①홈(재진입 배너·로그인 링크 포함) → ②영역 진입(진행 중 세션 노트·escape 두 갈래) → ③문답(A안 + undo·토스트·진척 바 규칙) → ④결과(**같은 화면 2상태** + 약점 0 B안) → ⑤로그인/에러(횡단 — 변형 2종·에러 카탈로그) → ⑥그래프 탐색(focus+context·바텀시트 체인).
3. **auth/API 레이어 재구현** — OAuth 3사 진입·콜백, 토큰 수명주기(reissue), HTTP 래퍼. 현행 결함 2건(401 무대응·에러 상태 유실)은 **승계하지 않고 개선**(§4.3).
4. **F-2 진척 복구** — 익명 answered-map localStorage 스키마 **v2**(undo·세션 수명주기 반영) + 게이트 재제출 배선(spec-01 S1-A 소비) (§4.2).
5. **Cytoscape 그래프 포팅** — `useConceptGraph.js`(klay·GRADE_COLORS·focus/dim·뷰포트 컨트롤)를 React hook 으로. ⑥ 검수 확정 반영: 호버 0·탭 바텀시트(3열 스택 체인)·**선택 중심 focus+context(선명 = depth≤2)**. ④-B 학습 경로 그래프와 컴포넌트 공유(`highlightPath` 옵션은 spec-03 계약 예약).
6. **정적 진도표 데이터** — 학년×시기 → 추천 단원 소프트 default(F-3·R6)용 정적 JSON (§4.7).
7. **모바일 퍼스트 셸** — v2 와이어프레임 390px 뷰포트 기준. 데스크톱은 반응형 하한만(PRD 비범위).

### Out (이번 범위 밖)
- **부가 화면** — 구 DiagView(학습지 선택)·RecordView(채점)·PersonalView(맞춤 학습지)·UserEditView·SignUpView 상세 폼·이력: 오픈 후 이식(D6). 구 문답 UI 는 ③이 대체.
- **html2pdf 포팅 — 하지 않음.** 사용처(DiagView·PersonalView)가 전부 D4 로 폐기. Chart.js 는 대상 자체가 없음(현행 미사용 — 2026-07-13 실측, 문서 정정 완료).
- **이메일/비밀번호 로그인·회원가입 폼** — ⑤는 OAuth 3사 동급이 정본. 이메일 가입은 오픈 후 재검토.
- **spec-03 몫** — `cards[].links` 채움·큐 항목 reason/goal·⑥ 경로 강조 모드 데이터 조립. 본 spec 은 부착 지점(컴포넌트·hook 옵션 타입)만 예약.
- **검수 파생 백로그 3건(v2, MVP 미포함 확정)** — ① 완료 배너 탭 → 재진단 직행([backlog](../../backlog/m7-home-completed-banner-rediagnosis-cta.md)) ② 진행 세션 폐기 확인탭([backlog](../../backlog/m7-entry-discard-confirm-dialog.md)) ③ 완주·미저장 세션 재프리뷰([backlog](../../backlog/m7-result-completed-session-repreview.md)).
- **③ 질문 선택 알고리즘 고도화** — 화면 계약과 독립([backlog](../../backlog/m7-adaptive-traversal-question-selection.md)).
- **다크모드·데스크톱 전용 최적화·정량 계측 파이프라인** — PRD 비범위.

---

## 2. 코드 사실확인 (구 spec-02 §2 승계 — 2026-07-13 Explore, 재작성 시점 재검토)

### 2.1 백엔드 계약 (무변경 — React 가 그대로 소비)

| 접점 | 사실 |
|---|---|
| OAuth 진입 | `GET /oauth2/authorization/{google\|naver\|kakao}` — 앵커 링크(같은 오리진, nginx 가 `/oauth2/`·`/login/oauth2` proxy). 백엔드 무변경 |
| OAuth 콜백 | 백엔드가 `/login?token=<accessToken>` 으로 리다이렉트(쿼리 파라미터) + refreshToken 은 Set-Cookie(HttpOnly) |
| 토큰 갱신 | `POST /api/v1/auth/reissue` body `{grantType:"Bearer", accessToken:<만료 토큰>}` — 쿠키 refreshToken 검증, 새 accessToken 응답 + refresh 로테이트 |
| 로그아웃 | `DELETE /api/v1/auth/authentication` |
| accessToken | `localStorage` key `'accessToken'`, 요청 헤더 `Authorization: Bearer`(요청 인터셉터 주입) |
| baseURL | dev `http://localhost:8080` / prod same-origin (`import.meta.env.PROD` 분기) |
| 배포 | nginx SPA fallback(`try_files … /index.html`) + `/api/v1/`·`/oauth2/` proxy, 2-stage Dockerfile(node build → nginx:alpine). React 산출물도 동일 구조 재사용 — 구 Vue 이미지를 덮는 dist 교체가 **아니라 별도 이미지**(§7 T5: 공존·이미지 전환 롤백) |
| 신규 소비(spec-01 구현 완료분) | `GET /chapters`(기존) · `POST /diagnosis/frontier`·`/next`·`/preview`(익명, preview 는 10회/분/IP 429) · `POST /diagnosis`·`GET /diagnosis/{userTestId}`·`POST /learning-queues`·`GET /learning-queues/me`·`PATCH …/items/{id}/done`(인증·소유권 403) · 에러 계약 400/401/403/429(spec-01 §4.6) · 플래그 `mmt.diagnosis.enabled` |
| ⑥ 그래프 | 기존 concepts nodes/edges/search/detail permitAll + `findNodesIdByConceptIdDepth2` 계약(focus+context 의 depth≤2 소스 — 06 검수 확정) |

### 2.2 승계하지 않는 현행 결함 (신규에서 개선)

- **401 자동 복구 없음** — 응답 인터셉터 부재, reissue 는 앱 부트 시 1회뿐. 사용 중 만료 = 조용한 실패.
- **에러 상태 유실** — `useApi()` 가 axios 에러를 문자열로 재포장해 status/바디 소실 → 학생 친화 에러(⑤ 카탈로그)가 원천 불가능한 구조.
- **로그인 상태 ad-hoc 중복** — 뷰마다 `localStorage.getItem !== null` + watch 복제(중앙 getter 없음).

---

## 3. 스캐폴딩 (결정 T1~T5 — §7 재확정 대기)

MVP 규모(6화면·전역 상태 극소)에 맞춰 가볍게. 2026-07-13 사인오프(git `35daa16`)와 동일안 — 재설계는 UX 였고 스택 전제는 불변이므로 **변경 없이 재채택을 권장**, 단 정본 문서가 소멸했으므로 재확정 절차를 밟는다.

| # | 항목 | 권장 | 요지 |
|---|---|---|---|
| T1 | 빌드·언어 | **Vite + React 19 + TypeScript** | 커리어 수요(D6 근거)·1인 개발 회귀 안전망·계약 타입화. 산출물 = 정적 dist(배포 무변경) |
| T2 | 상태관리 | **TanStack Query(서버 상태) + Zustand(클라 상태 2조각: auth·quiz 진행)** | 전역 상태 실측 극소(§2.2). Query 의 로딩/에러 표준화가 에러 유실 결함의 구조적 해결과 결합 |
| T3 | 스타일링 | **Tailwind CSS(+ 필요 시 Radix headless)** | 모바일 퍼스트 유틸리티·v2 커스텀 와이어프레임 자유도. PrimeReact 는 구 톤 승계 위험 |
| T4 | 라우팅 | **React Router v7** | 표준. 게이트는 라우트 가드가 아니라 F-1 결과-시점 트리거(§4.4) |
| T5 | 디렉토리 | **신규 `web-react/` 병행 → 런치 시 스왑** | spec-01 §4.7 롤백이 "구 Vue 프론트"를 전제 → 구 `web/` 은 런치까지 무변경 생존. 롤백 = **이미지 전환**(별도 태그 공존 — 재빌드·git revert 아님) |

폰트 Pretendard 유지. GA(vue-gtag `G-SELPGNP1WK`)는 gtag 직접 또는 react-ga4 로 승계(라우터 연동, `anonymize_ip` 유지).

---

## 4. 크리티컬 패스 설계 (v2 SSOT 반영)

### 4.1 라우트 맵

| 라우트 | 화면 | 접근 | 핵심 계약(v2 확정) |
|---|---|---|---|
| `/` | ① 홈 | 익명 | 1차 CTA "무료 진단 시작" 단독 + 그래프 히어로(탭 무동작 미끼) + "둘러보기" 텍스트 링크. **조건부 재진입 배너**(로그인+활성 큐 시에만 `GET /learning-queues/me` 1회 — 비로그인 무호출): "이어서: {첫 미완료 개념} ›" → ④-B 직행, 전 항목 완료면 **문구만 대체·탭 동일**(A안). **조용한 "로그인" 링크**(비로그인 시에만, 배너와 상호 배타) → ⑤-A 변형(귀속 생략, 성공 시 홈 복귀) |
| `/diagnosis/entry` | ② 영역 진입 | 익명 | 학년·학기 칩(localStorage 기억 — §4.2 `mmt.grade.v1`, 미설정이면 학년 선택 먼저) → `GET /chapters` pick-list(계단순 3~4개, 시기 추정 default 강조) → 원탭 = `POST /diagnosis/frontier` → 즉시 ③. escape (a) 전체 단원 바텀시트 (b) "전체 훑기" scope=full. **진행 중 세션 노트**(미완주 맵 존재 시): 이어서 진행/새로 시작 — 새로 시작·타 단원 원탭 = **확인탭 없이 즉시 폐기**(백로그 ②) |
| `/diagnosis/quiz` | ③ 문답 | 익명 | `POST /diagnosis/next` 반복(카드 내용만 교체, 버튼 위치 불변). **"‹ 이전 답 수정" = 직전 답 1개 undo 만**(연타 시 한 개씩 소급, 답변 0개면 숨김 — 인과 불변식). 토스트는 **"알아요"에만**(1.5초). 진척 바 = asked/(asked+estimatedRemaining), **후퇴 금지(max 유지) — undo 만 예외**. 로드 실패 = 카드 자리 인라인 에러 + 재시도(2택 비활성). done:true → ④ 자동 전환 |
| `/diagnosis/result` | ④ 결과 — **같은 화면 2상태 + 약점 0** | 익명→게이트 | §4.4 |
| `/login` | ⑤-A 로그인 | 익명 | OAuth 3사 동급 + 안심 박스. **변형 2종**: (게이트 경유) returnTo=④ — 콜백 후 귀속+큐 생성 / (홈 로그인 링크) 귀속할 결과 없음 — 일반 카피, 성공 시 홈 복귀. 콜백 실패 = ⑤-A 복귀 + 버튼 아래 인라인 1줄(풀스크린 아님) |
| `/error` | ⑤-B 풀스크린 에러 | 익명 | **화면 컨텍스트가 없는 실패만**(라우팅 404·403 소유권 "찾을 수 없어요" 존재 숨김). 그 외 전 에러는 발생 지점 인라인(§4.6 카탈로그) |
| `/concepts` | ⑥ 그래프 탐색 | 익명 | §4.5. `highlight=queue` 쿼리 파라미터는 spec-03 예약 |

- 구 spec-02 의 `/queue` 라우트는 **삭제** — v2 확정으로 큐 화면 = ④-B(그래프 경로→진단 카드→큐 체크리스트 한 화면, 홈 배너·재열람 진입도 ④-B 도착). 플로우 맵 SSOT 정합.
- 라우트 가드 최소화: 인증 필요 화면이 별도로 없음(④-B 는 상태이지 라우트가 아님). `/diagnosis/*` 딥링크인데 필요한 localStorage 없으면 ②로 유도.

### 4.2 F-2 — localStorage 계약 v2

```
key: mmt.diagnosis.v1
val: { entry: {chapterId | scope:"full", schoolLevel?},
       answered: [{conceptId, known}],       // append-only, 답변 순서 보존
       done: boolean,                        // done:true 수신 시 마킹
       updatedAt }

key: mmt.grade.v1
val: { schoolLevel, gradeLevel, semester }   // ② 학년 칩 기억(최초 방문 선택) · ⑥ 초기 스코프 재사용
```

- **`answered[]` 순서 보존 계약(append-only)** — 재정렬·dedup·재직렬화로 순서를 흐트리지 않고, preview·재제출 페이로드도 이 순서 그대로. spec-01 §4.4-2 시퀀스 순서 조건(DKT 순서 민감 → preview==귀속 결정론)의 프론트 측 필요조건.
- **undo 정합**: "‹ 이전 답 수정" = **말단 pop 만**(직전 1개) — append-only 불변식과 정합. 임의 인덱스 제거 UI 금지(v2 확정 — 이후 답 전부 무효화되므로).
- **세션 수명주기(v2 확정)**: ② 이어가기 대상 = **done=false(미완주·미저장) + 같은 단원**만. done:true 후 미저장 이탈 = **소실 허용**(재프리뷰 배선 여부는 백로그 ③). 새 진단 = 새 세션(기존 맵 즉시 폐기). 귀속(`POST /diagnosis`) 성공 시 **삭제**(정본이 서버로 이동).
- **버전 키(v1)** — 스키마 변경 시 신 키로 마이그레이션 or 폐기(조용한 파손 방지).

### 4.3 auth/API 레이어 재구현

- **HTTP 래퍼**: 단일 인스턴스 + TanStack Query. 요청 인터셉터 = Bearer 주입(현행 계약 승계). **에러는 status·바디 보존한 typed error 로 전파**(§2.2 개선) → Query error 상태로 §4.6 카탈로그 카피 매핑.
- **401 응답 인터셉터 신설**: 401 → `POST /auth/reissue` → 성공 시 원 요청 1회 재시도, 실패 시 로그아웃 상태 전환. **동시 401 다중 재발급 방지**(재발급 promise 공유). 재로그인이 필요해지면 **⑤-A 경유 후 보던 화면 복귀**(05 검수 확정 — 홈으로 튕기지 않음): returnTo 를 게이트 전용이 아니라 일반 복귀 메커니즘으로(sessionStorage).
- **OAuth**: 진입 = 앵커(`/oauth2/authorization/{provider}`) — 백엔드·nginx 무변경. 콜백 `/login?token=` 파싱 → 저장 → **returnTo 분기**: ④ 출발(게이트)이면 ④ 복귀→귀속+큐 / 홈 로그인 링크 출발이면 홈 복귀(배너 노출). 토큰 없는 콜백 = ⑤-A 인라인 에러(현행 console.error 방치를 승계하지 않음).
- **auth 상태 = Zustand 단일 스토어**(토큰·파생 isAuthenticated) — 뷰별 ad-hoc 복제(§2.2) 제거.

### 4.4 ④ 결과 — 같은 화면 2상태 + 약점 0 (F-1 게이트 배선)

```
[④-A 무료]  done:true 도착 → POST /diagnosis/preview 렌더
            헤드라인 + 정직성 한 줄("자가응답 기반 — 시험 점수가 아니에요", v2 확정 위치)
            + 카드 top-N(배지 상/중/하·blockedDescendants 사람 언어·links) + "더 보기" 인라인 토글
            └ 단일 1차 CTA "저장하고 학습 경로 시작하기"(안내문 = 무료 먼저 카피)
              → 비로그인: returnTo 기록 → ⑤-A / 로그인: 즉시 귀속
[전환]      POST /diagnosis (localStorage 맵 재제출) → POST /learning-queues
            → 같은 화면이 ④-B 상태로 전환(라우트 이동 없음) → localStorage 삭제
[④-B 게이트] 순서(v2 확정) = ① 그래프 학습 경로(공유 그래프 컴포넌트 + 경로 하이라이트)
            → ② 진단 카드 재열람("학습 경로 보기" 2차 버튼만 제거) → ③ 큐 체크리스트
            (항목 탭 = done 토글 PATCH, "여기부터" = 서버 파생값 그대로 렌더 — 클라 계산 금지)
            + 보조 "다시 진단하기" → ② (탭 시점 폐기 없음 — 새 큐 생성 시 대체)
[재열람]    홈 배너 → /diagnosis/result 직행: GET /learning-queues/me → GET /diagnosis/{userTestId}
            → 처음부터 ④-B 상태로 렌더 (익명 preview 데이터 불요)
[약점 0]    cards 0장 = 정상 결과(B안): 1차 CTA "한 계단 위 '{단원}' 진단하기"(§4.7 계단 데이터
            재사용, 탭 → 그 단원 새 세션 ③) + 조용한 링크 "그래프에서 확인하기 ›" → ⑥.
            게이트 CTA·저장 없음(빈 큐 — 저장할 것 없음)
```

- **preview == 귀속 결과**(spec-01 결정론 계약)이므로 전환 시 재렌더 데이터 원천만 교체되고 내용 동일 — "본 결과가 저장됐다"는 약속.
- 재제출 실패(TF Serving 등) = fail-soft — **인라인 스트립 1줄**(모달·토스트 아님, 지속 상태), 배지만 "—" 결측 + 카드 순서는 blocked 수 순 대체(v2 확정). 게이트에서 학생을 막다른 골목에 두지 않음.
- **엣지(구현 시 처리):** scope=full(전체 훑기) 세션의 약점 0 은 "한 계단 위" 타깃 단원이 정의되지 않음 → CTA 를 "다른 단원 진단하기"(② 복귀)로 폴백. v2 미명세분의 보수적 처리 — 하이파이에서 카피만 조정.

### 4.5 Cytoscape 그래프 포팅 (⑥ + ④-B 공유)

- `useConceptGraph.js` 는 프레임워크 무관 로직(klay 레이아웃·`GRADE_COLORS` WCAG 팔레트·focus/dim·zoomIn/Out/fit/reset) — React hook `useConceptGraph()` 로 이식: layout effect 로 init/`cy.destroy()` 정렬(메모리 누수 규칙 승계), 컨테이너 ref.
- **호버 코드는 이식하지 않는다**(v2 확정 — 호버 의존 0). 탭 = 선택 + **바텀시트**: 개념명 + description(③ 카드와 동일 D5 소스) + **3열 스택 체인 "먼저(선수)→지금→다음(후수)"**(직계만, 열은 세로 스택·"+N"·말줄임 허용, pill 탭 = 그 노드로 선택 이동+시트 교체). 닫기 = 빈 곳 탭/끌어내림. 시트가 하단 상시 CTA "내 약점 진단하기"를 가리지 않게.
- **focus+context(v2 확정)**: 선명 = 선택 노드 중심 **depth≤2 이웃**(기존 `findNodesIdByConceptIdDepth2` 계약 재사용), 너머 = 오퍼시티 흐림(숨김 아님). 선택 이동 시 선명 영역 추적 + 부드러운 재포커스. 초기 선택 = 진입 컨텍스트(검색 결과·④ 약점 0 링크의 개념) 있으면 그 노드, 없으면 스코프 대표(단원 내 후수-최상위 — ② 프론티어와 동일 개념).
- **스코프 칩 = 로드 범위**(단원 목록 + "모두 보기") — 판독성은 focus+context 가 담당하므로 "모두 보기"도 성립. 초기 스코프 = `mmt.grade.v1` 기반 시기 추정 단원(② 로직 재사용, 기억값 없으면 대표 단원). 단원 경계 넘기 = 검색 또는 체인 pill(경계 개념이면 스코프 자동 전환).
- **④-B 공유**: 같은 컴포넌트에 경로 하이라이트만 추가 — `highlightPath: {orderedIds, doneIds, currentId}` 옵션 타입을 계약 SSOT 에 예약(데이터 조립·강조 모드 구현은 spec-03).

### 4.6 에러·엣지 — v2 에러 카탈로그가 프론트 계약

원칙: **상황 내 인라인 우선**, 풀스크린(⑤-B)은 컨텍스트 없는 실패만. 전 카피 학생 친화 — 내부 용어(percent·depth·probability) 화면 노출 금지, urgency 등급·사람 언어 근거만(PRD §4.2 규율).

| 상황 | 표시 | 복구 |
|---|---|---|
| ③ next 로드 실패 | 인라인 — 카드 자리 대체 | 재시도(같은 요청) |
| ④ TF Serving fail-soft | 인라인 — 헤드라인 아래 스트립 | 없음(결과 성립 — 배지 결측) |
| 429 preview 한도 | 인라인 — ④ 도착 자리 | 재시도(맵 무사) |
| 400 스테일 맵(중복·미존재) | 인라인 — ③ 카드 자리 | "새로 시작" → ②(맵 폐기) |
| 401 세션 만료(④-B 중) | 인라인 안내 → ⑤-A 재로그인 | 성공 시 보던 화면 복귀 |
| 403 소유권 | 풀스크린 ⑤-B — "찾을 수 없어요"(존재 숨김) | 홈으로 |
| 라우팅 404 | 풀스크린 ⑤-B | 홈으로 |
| OAuth 콜백 실패 | ⑤-A 복귀 + 인라인 1줄 | OAuth 재진입 |

### 4.7 정적 진도표 데이터 (F-3·R6)

- 학년×시기(기기 날짜 기준 월) → 시기 추정 단원 매핑 = **정적 JSON**(`assets`) — 소프트 default 일 뿐이라 데이터 품질에 관대(오추정은 pick-list ±후보와 escape 가 흡수). 계단순 정렬(±후보 구성·④ 약점 0 의 "한 계단 위" 타깃)도 이 데이터가 소스.
- 서버 저장 없음 — 학년 기억은 `mmt.grade.v1`(§4.2). 진도표 갱신 = 재배포(콘텐츠성 소규모 유지보수, R6).

---

## 5. 계약 SSOT (병렬 개발 드리프트 방지 — 구 spec-02 §5 승계)

- spec-01 §4.1/§4.3/§4.4/§4.6 + spec-03 §3 의 요청·응답 shape 가 정본 — 프론트는 **TS 계약 타입 모듈**(예: `src/api/contracts.ts`)로 옮겨 mock 과 실클라이언트가 같은 타입을 참조한다(T1 TypeScript 의 실효 조건). 백엔드는 spec-01 구현이 이미 머지됐으므로(PR #48) mock 이 아니라 **로컬 실서버**(플래그 on, CLAUDE.local.md env 5종)를 1차 개발 타깃으로 쓸 수 있음 — mock 은 TF Serving 부재 시 폴백.
- 드리프트는 결정론 스냅샷(§6)으로 사후 감지. Java DTO↔TS 타입 컴파일 타임 강제(codegen)는 범위 밖 — 백로그 후보(구 spec-02 결론 승계).

---

## 6. 검증 (착수 후)

- **모바일 무결**(PRD §5.1): 6화면(④는 3상태 전부)이 390px 뷰포트에서 깨지지 않음 — dev 서버 기동 + 사람 시각검증 체크리스트 제시(기동까지 어시스턴트 몫).
- **OAuth 3사 실 로그인** 각 1회 + **변형 2종**(게이트 경유 returnTo=④ 복귀·귀속·큐 생성 / 홈 링크 경유 홈 복귀·배너 노출) + 401 인터셉터 재발급·재시도·보던 화면 복귀(만료 토큰 시뮬레이션).
- **F-2**: 문답 중 이탈→② "이어서 진행" 복원 / **undo 말단 pop 후 재개 정합** / done 후 미저장 이탈 = ② 이어가기 미대상 / 새 진단 = 즉시 폐기 / 귀속 후 삭제.
- **게이트 결정론**: preview 결과와 ④-B 전환 후 결과 동일(스냅샷) + 재제출 페이로드 `answered[]` 순서 == localStorage 저장 순서 assert(§4.2 순서 보존 — undo 이후에도).
- **③ 인터랙션 규칙**: 진척 바 후퇴 금지(drill-down 으로 estimatedRemaining 증가 시 max 유지)·undo 시에만 후퇴 / 토스트 "알아요"에만 / 인라인 에러 중 2택 비활성.
- **④ 상태 전환**: 같은 라우트에서 A→B 전환(이동 없음) / 홈 배너 재열람 = B 직행 / 약점 0 B안(빈 큐 — 게이트 CTA 없음) / fail-soft 스트립.
- **그래프**: ⑥ 탭 바텀시트(호버 0)·3열 체인 pill 이동·focus+context depth≤2 선명/흐림·스코프 "모두 보기" 판독성·unmount 시 destroy(누수), ④/⑥ 재진입 반복 시 메모리 안정.
- **빌드·배포 정합**: `npm run build` dist 를 기존 nginx 컨테이너 구조로 서빙(로컬 docker)해 SPA fallback·proxy 동작 확인 — 배포 레이어 무변경 증명.

---

## 7. 결정 (📝 사인오프 대기 — T1~T5 재확정)

전 항목 2026-07-13 사인오프본(git `35daa16`)과 동일 — 재설계로 바뀐 것은 UX(v2)이고 스택 전제(MVP 6화면·전역 상태 극소·배포 무변경)는 불변이므로 **변경 없이 재채택 권장**. 정본 문서가 excise 로 소멸해 재확정 절차만 밟는다.

- **T1 — 빌드·언어:** **Vite + React 19 + TypeScript(권장)** — 계약 타입화(§5)·회귀 안전망 / JS — 드리프트를 런타임에야 발견. 전제 = §8 node 빌드 이미지(실측 완료분 승계).
- **T2 — 상태관리:** **TanStack Query + Zustand(권장)** — 서버 상태 표준화가 에러 개선(§4.3)과 결합 / Redux Toolkit — 과대 / Context 만 — quiz·auth 재렌더 제어 번거로움.
- **T3 — 스타일링:** **Tailwind(+Radix headless)(권장)** — 모바일 퍼스트·로우파이 커스텀 자유도 / PrimeReact — 구 톤 승계·번들 무게 / CSS Modules — 토큰 체계 수작업.
- **T4 — 라우팅:** **React Router v7(권장)** / TanStack Router — 타입 안전하나 생태계 좁음.
- **T5 — 디렉토리:** **A(권장) 신규 `web-react/` 병행, 런치 시 스왑** — spec-01 롤백 전제(구 Vue 생존) 충족, 롤백 = 이미지 전환 / B `web/` 직접 재작성 — 롤백이 git revert 뿐이라 blue-green 이미지 롤백과 어긋남. 스왑·구 web 정리는 런치 후 별도 Task(+ 밀스톤 R5 서사 보존).

---

## 8. 선결 확인 + Analyze-Before-Change 예고

### 선결 확인 — node 빌드 이미지 (2026-07-13 실측분 승계, `web/` 무변경 보존이라 유효)

- **(a) ADR 요부 실측 완료:** 루트 CLAUDE.md 금지 규칙은 "compose 의 서비스 구성" 한정 — `mmt-front` 는 이미지 참조뿐(빌드 지시 없음)이라 Dockerfile 빌드 스테이지 교체 자체는 ADR 불요. 단 **런치 스왑 시 compose 이미지 참조 변경은 ADR 판단**(React 도입 ADR 에 포함 가능).
- **(b) 런타임 무영향 실측 완료:** `web/Dockerfile` 2-stage — 빌드 스테이지는 dist 생성에만 관여, 런타임 nginx·blue-green 롤백 이미지와 무관. (T5 가 별도 이미지 전략이므로 구 Dockerfile 은 아예 무접촉 — 신규 `web-react/Dockerfile`.)
- **(잔여 — 착수 시 실측):** 프론트 이미지(태그) 전환이 M6 배포 스크립트에서 실행되는 구체 경로 — 롤백 실행 절차 확정.

### Analyze-Before-Change (사인오프 후 착수 시)

- 신규 디렉토리(`web-react/`) 생성이라 기존 코드 영향 없음 — 분석 대상은 **경계면**: nginx.conf(산출물 경로·캐시 헤더)·GA·OAuth 리다이렉트 URI(콜백 경로 유지로 무변경 예상, 실측)·`mmt.diagnosis.enabled` 플래그 상태.
- **ADR: React 도입** — D6 근거 + T1~T5 확정 스택 + 런치 스왑 compose 변경 판단. 착수 시 `/write-adr`.
- web/CLAUDE.md 전면 재작성·`web-react/CLAUDE.md` 신설은 런치 스왑 시점(거버넌스 문서 — 사용자 승인 경유).
