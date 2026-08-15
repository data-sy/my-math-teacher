# 가정 로그 — 프론트 제로베이스 빌드

> 형식: 무엇을 가정했나 · 근거 · 뒤집으려면 어디를 고치나.
> 기획물이 침묵하는 지점만 — 기획물과 모순되는 가정 금지.

- **A-1 · 백엔드 미기동 → mock 우선 모드.** 2026-07-18 00시대 `GET http://localhost:8080/api/v1/chapters` 무응답(000) 1회 확인. 실연동 검증은 핸드오프 체크리스트로 이월. — 뒤집기: 서버 기동 후 `web-v2/.env`에서 `VITE_ENABLE_MOCK=false`.
- **A-2 · 응답 JSON의 세부 필드명은 flow-map 표기 그대로.** 표가 shape을 예시(`{next{conceptId,…}, progress{asked, estimatedRemaining}}`)로 적어 이를 문자 그대로 타입화. 에러 바디는 "학생 친화 메시지 동반"만 확정 → `{message: string}`으로 가정. — 뒤집기: 실서버 첫 호출로 확인 후 `web-v2/src/api/types.ts`만 수정.
- **A-3 · accessToken 보관 = localStorage.** 접점 시트가 "보관 방식은 설계 자유" 명시. 새로고침·재방문 유지가 홈 배너(로그인+활성 큐) 요건과 정합. XSS 표면은 MVP 수용(외부 입력 렌더 없음). — 뒤집기: `web-v2/src/auth/AuthContext.tsx`의 storage 어댑터 교체.
- **A-4 · mock 모드의 OAuth = 가짜 콜백 리다이렉트.** OAuth는 페이지 이동이라 MSW로 가로챌 수 없음 + 로컬 실 OAuth 불가(콜백 도메인 하드코딩 — 접점 시트). mock에선 버튼 탭 → 즉시 `/login?token=mock-token-{provider}` 이동으로 콜백 이후 전 플로우(귀속·큐 생성·④-B 전환)를 검증. — 뒤집기: `VITE_ENABLE_MOCK=false`면 실 `location.href` 진입 코드가 그대로 동작(분기 한 곳 — `auth/oauth.ts`).
- **A-5 · 학년×시기→추천 단원 진도표 = 프론트 하드코딩 표.** PRD R6이 "표준 진도표 의존·확률적 guess·soft 제안"으로 확정 — 데이터 소스는 침묵 → mock 그래프 데이터의 단원에 `grade/semester/월 범위`를 달아 프론트에서 추정. — 뒤집기: `web-v2/src/mocks/graph-data.ts`의 표만 교체(실서버 chapters 응답에 시기 필드가 있으면 그걸 사용).
- **A-6 · "한 계단 위" 단원(약점 0 CTA) = chapters 계단순 정렬에서 현재 단원 다음 항목.** 04 ●12 "② 계단순 데이터 재사용" 확정의 구체화. scope=full이거나 다음 단원이 없으면 B′ 폴백("다른 단원 진단하기" → ②). — 뒤집기: `web-v2/src/screens/Result/emptyTarget.ts`.
- **A-7 · 진척 바 최초 표시.** 첫 질문 시점(asked=0) 바 0%에서 시작, `estimatedRemaining`은 서버(mock) 값 그대로. 바 후퇴 금지·undo 예외는 03 ●2 확정 그대로 구현. 침묵 지점인 "첫 화면의 남은 예상 표기"도 동일 규칙으로 통일. — 뒤집기: `ProgressBar.tsx`.
- **A-8 · mock 지식그래프 데이터는 중등 수학 교육과정을 단순화한 가상 DAG.** 실서버 그래프와 다름 — 화면·플로우 검증용이지 콘텐츠 정합성 검증용 아님. — 뒤집기: 실서버 연동 시 자동 대체(mock off).
- **A-9 · `GET /api/v1/concepts?chapterId` 응답에 선수 관계(edges)가 포함된다고 가정.** ⑥이 그래프를 그리려면 노드+엣지가 필요한데 표는 엔드포인트만 명시. `{concepts[], edges[{from,to}]}`로 가정(엣지 방향 = 선수→후수, 06 ●4). — 뒤집기: 실응답 확인 후 `api/types.ts` + `ConceptGraph` 데이터 어댑터.
- **A-10 · `/search`·`/{id}` 응답 shape.** search = 개념 요약 배열, `/{id}` = `{concept, prerequisites[], successors[]}`(⑥ 시트 체인 "먼저/지금/다음"에 필요한 직계 관계). — 뒤집기: A-9와 동일 지점.
- **A-11 · 큐 응답 shape** = `{queueId, items[{itemId, conceptId, conceptName, position, done}]}`, 현재 위치 = 서버 파생값이지만 표시엔 "position순 첫 done=false" 규칙(04 ●10)을 클라 렌더에서 적용. — 뒤집기: `api/types.ts`.
- **A-12 · 401 재로그인 복귀** = `/login?return=<원래 경로>` 쿼리로 구현(05 카탈로그 "보던 화면 복귀" 확정의 구체화). — 뒤집기: `auth/` 한 곳.
- **A-13 · chapters 응답 = 계단순 전체 목록, ±윈도잉은 프론트.** 표는 `?grade&semester` 쿼리만 명시하는데 ±후보가 학기 경계를 넘으므로(이차함수 = 중3·2학기) 전체 목록 수신 후 클라에서 default ±1 윈도를 만든다. — 뒤집기: 실서버가 필터된 목록만 주면 `Entry.tsx` 후보 산출을 서버 응답 그대로로.
- **A-14 · ④ 약점 0 프레임의 "카드 0장 = 정상 결과 (전부 알아요 → 시급도 산출 생략)" 박스는 명세 주석으로 해석, 화면 미구현.** "시급도 산출 생략"은 내부 동작 서술이라 유저 카피로 노출하면 표기 규율(내부 용어 금지)과 충돌. 헤드라인+서브카피+CTA만 구현. — 뒤집기: `Result.tsx` EmptyResult 에 카드 한 장 추가.
- **A-15 · OAuth 콜백 실패 신호 = `/login?error=` 쿼리로 가정.** 접점 시트는 성공 콜백(`?token=`)만 확정 — 실패 시 백엔드가 어디로 보내는지 침묵. ⑤-A 는 `error` 쿼리 존재 시 인라인 실패 카피를 띄운다. — 뒤집기: 실서버 실패 리다이렉트 확인 후 `Login.tsx` 판정 한 줄.
- **A-16 · ④-B 그래프의 노드 탭 정보 = 그래프 아래 한 줄(개념명 — 예시)로 표시.** 04 ●8 "노드 탭 = 선택·개념 정보"의 최소 구현 — ⑥의 풀 시트(체인 포함)는 ⑥ 전용으로 유지(04 는 시트 명세 없음). — 뒤집기: `Result.tsx` SavedResult 에서 ⑥ 노드 시트 컴포넌트 재사용.
