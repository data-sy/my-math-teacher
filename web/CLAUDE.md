# MMT Web (Vue) — 은퇴한 워크스페이스

루트 규칙은 @/CLAUDE.md 참조.

> ## ⚠️ 프로덕션에서 내려갔다 (2026-08-06)
> 현행 프론트 = **[`web-v2/`](../web-v2/CLAUDE.md) (React)**, 프로덕션은 `mmt-front:2.0.2` 로 서빙 중이다.
> 여기 구 Vue 는 **롤백 자산으로만 보존**한다 — **신규 개발·리팩토링 금지.**
> 사용자의 명시적 지시 없이 수정하지 말 것. 근거 = [ADR-0011](../docs/adr/0011-react-web-v2-and-front-image-swap.md).
> 디렉토리 삭제 시점은 미정(ADR-0011 Consequences).

아래는 **보존된 코드를 읽어야 할 때만** 필요한 최소 정보다. 작업 규칙은 `web-v2/CLAUDE.md` 를 본다.

## 무엇으로 만들어졌나

Vue 3 + Vite 4 · Vuex 4(Pinia 아님) · vue-router 4 · PrimeVue 3 + PrimeFlex ·
**Cytoscape**(+`cytoscape-klay`, 지식 그래프) · html2pdf.js(학습지 PDF) · axios.
테스트 프레임워크는 도입된 적 없다.

## 어디에 무엇이 있나

- `views/` — 라우트 단위 페이지 (`HomeView` `ConceptView` `DiagView` `ResultView` `PersonalView` `RecordView` `SignUpView` `UserEditView` `OauthLogin` `ErrorView`)
- `layout/` — 공통 셸 (`AppLayout` `AppTopbar` `AppLearningSteps` `AppBottomTabs` `AppFooter` + `composables/layout.js`)
- `composables/` — `api.js`(HTTP 래퍼 `useApi()`) · `useConceptGraph.js`(Cytoscape 렌더링) · `htmlToPdf.js` · `useLoginDialog.js` · `useUserForm.js`
- `store/`(Vuex) · `router/` · `service/`(`AuthService` `TitleService`) · `constants/contact.js`
- 경로 alias `@` → `./src`

## 백엔드 연동 (읽을 때 주의)

- HTTP 호출은 전부 `composables/api.js` 의 `useApi()` 경유. baseURL 은 `http://localhost:8080` **하드코딩**
- accessToken = `localStorage`, 요청 interceptor 가 `Authorization: Bearer` 주입 / refreshToken = HttpOnly 쿠키(`withCredentials`)
- 빌드 산출물 `dist/` → `web/Dockerfile` + `web/nginx.conf` 로 이미지화 (현재 미사용)
