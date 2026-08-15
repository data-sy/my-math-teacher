# MMT Web v2 (React)

루트 규칙은 @/CLAUDE.md 참조. 이 문서는 `web-v2/` 워크스페이스에만 적용된다.

**현행 프론트다.** M7 제품 피벗에서 구 Vue(`web/`)를 마이그레이션이 아니라 **새로 짜서** 대체했고, 2026-08-06 프로덕션 스왑 완료(`mmt-front:2.0.2`). 구 `web/` 은 롤백 자산으로만 보존한다. 근거 = [ADR-0011](../docs/adr/0011-react-web-v2-and-front-image-swap.md).

## 기술 스택

- React 19 + TypeScript, Vite 8
- react-router-dom 7 (`createBrowserRouter`)
- TanStack Query 5 — 서버 상태
- Cytoscape — 지식그래프 시각화
- MSW — mock 모드 (개발·e2e)
- Playwright — e2e / 캡처
- oxlint — 린트 (ESLint 아님)

## 개발 명령

```bash
npm run dev                                              # mock 모드 (기본)
VITE_ENABLE_MOCK=false VITE_API_BASE=http://localhost:8080 npm run dev   # 실서버 연동
npm run dev -- --host                                    # 실기기 확인 (같은 Wi-Fi 폰에서 :5173)
npm run build                                            # tsc -b && vite build
npm run lint                                             # oxlint
npx playwright test                                      # e2e (mock)
SHOT_DIR=<경로> npx playwright test e2e/capture.spec.ts   # 390px 화면 캡처 14장
```

환경변수는 `VITE_ENABLE_MOCK` · `VITE_API_BASE` 둘뿐이다. 백엔드 진단 경로를 쓰려면 API 쪽에 `MMT_DIAGNOSIS_ENABLED=true` 가 필요하다(@/docs/DEVELOPMENT.md).

## 디렉토리 구조

- `src/App.tsx` — 라우트 정의 (`/` `/entry` `/quiz` `/result` `/login` `/graph` `*`)
- `src/screens/` — 화면 단위 (`Home` `Entry` `Quiz` `Result` `Login` `GraphExplore` `NotFound`)
- `src/api/` — `client.ts`(HTTP 래퍼) · `endpoints.ts` · `types.ts`
- `src/auth/` — `AuthContext.tsx` · `oauth.ts` · `tokenStore.ts`
- `src/session/diagSession.ts` — 익명 진단 진척(localStorage)
- `src/components/` — `ConceptGraph.tsx`(Cytoscape) · `BottomSheet.tsx`
- `src/mocks/` — MSW 핸들러 · 그래프 목데이터 · 목 순회
- `src/lib/curriculum.ts` — 학년·학기·단원 매핑
- `e2e/` — mock 스펙 + `prod-*.spec.ts`(라이브 스모크) + `capture.spec.ts`

## 설계·구현 정본

코드를 바꾸기 전에 아래를 먼저 읽는다. **화면 거동의 정본은 코드가 아니라 이 문서들이다.**

- 구현 스펙: `docs/specs/m7/frontend-spec.md` · 전제 목록 `docs/specs/m7/assumptions.md`
- 화면 계약: `docs/design/v2/` — `00-flow-map.html`(화면 전이 SSOT) + `01-home`~`06-graph`(화면별 ●N 계약)
- 제품 정의: `docs/prd/m7-prd.md`
- ⚠️ `docs/design/m7-*` 는 read 전용 아카이브(구 산출물) — 참조 금지

## 작업 규칙

- **mock 과 실서버는 계약이 갈릴 수 있다.** 정본은 **실서버 모드**다 — mock 순회는 백엔드 KST 코어와 다르게 동작하는 알려진 divergence가 있다(`docs/backlog/m7-web-v2-mock-traversal-kst-divergence.md`). mock e2e green 을 실서버 정합의 근거로 쓰지 말 것
- **모바일 퍼스트** — 기준 폭 390px. 데스크톱은 그 위에 얹는다
- HTTP 호출은 `src/api/` 경유. 화면에서 `fetch` 직접 호출 금지
- Cytoscape 인스턴스 생성/파괴는 컴포넌트 라이프사이클과 정렬 (메모리 누수 주의)
- 화면 거동을 바꾸면 `docs/design/v2/` 의 해당 화면 계약(●N)도 함께 갱신 — 코드만 바꾸면 다음 세션이 옛 계약을 정본으로 읽는다
- 린트는 `npm run lint`(oxlint). 변경 파일만 보려면 `npx oxlint <파일>`
- 배포 산출물 = `dist/` → `Dockerfile`·`nginx.conf` 로 `mmt-front` 이미지. SPA fallback 과 `/api` 프록시가 nginx 쪽에 있다

## 알려진 미해결

`docs/backlog/` 의 `m7-*` 항목들이 이 워크스페이스 소관이다 — 그래프 요약 칩·스테일 큐 첫 탭 403·완주 세션 재프리뷰·홈 완료 배너 재진단·카피 방향(열린 결정, 현행 유지) 등. 신규 발견은 백로그에 파일로 남긴다.
