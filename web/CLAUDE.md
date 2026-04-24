# MMT Web (Vue)

루트 규칙은 @/CLAUDE.md 참조. 이 문서는 `web/` 워크스페이스에만 적용되는 규칙이다.

## 기술 스택

- Vue 3 (`^3.2.41`)
- Vite 4 (`^4.2.1`) — 빌드/개발 서버
- Vuex 4 (`^4.0.2`) — 상태 관리 (Pinia 아님; 전환은 ADR 필요)
- vue-router 4
- PrimeVue 3.39.0 + PrimeFlex + PrimeIcons — UI 컴포넌트
- **Cytoscape** (+ `cytoscape-klay`) — 개념 지식 그래프 시각화 (서비스 핵심 기능)
- Chart.js 3.3.2 — 진단 결과 차트
- html2pdf.js — 맞춤 학습지 PDF 출력
- axios — HTTP 클라이언트
- vue-cookies — refreshToken 쿠키 처리

## 개발 명령

- 개발 서버: `npm run dev` (Vite, 기본 포트 `5173`)
- 프로덕션 빌드: `npm run build`
- 빌드 결과 미리보기: `npm run preview`
- 린트 (자동 수정 포함): `npm run lint`
- **테스트 스크립트는 현재 없음** — 테스트 프레임워크 도입은 별도 마일스톤/ADR에서 결정

## 디렉토리 구조

`src/` 하위 실제 구조:

- `App.vue`, `main.js` — 엔트리
- `assets/` — 정적 리소스
- `views/` — 라우트 단위 페이지 (`HomeView`, `ConceptView`, `DiagView`, `ResultView`, `PersonalView`, `RecordView`, `SignUpView`, `UserEditView`, `OauthLogin`, `ErrorView`)
- `layout/` — 공통 레이아웃 (`AppLayout`, `AppMenu`, `AppSidebar`, `AppTopbar`, `AppFooter`)
- `router/` — vue-router 설정 (`index.js`)
- `store/` — Vuex 스토어 (`index.js`)
- `service/` — 도메인별 서비스 모듈 (`AuthService`, `TitleService`)
- `composables/` — 재사용 가능 훅 (`api.js` HTTP 래퍼, `htmlToPdf.js`)

경로 alias: `@` → `./src` (`vite.config.js`에 설정됨)

## 코딩 컨벤션

- 컴포넌트 파일명: PascalCase (`.vue`)
- Props는 명시적 타입·필수 여부 선언
- 린트·프리티어 준수 — 커밋 전 `npm run lint` 수행 권장
- 공통 레이아웃은 `layout/` 재사용, 페이지별 중복 구성 금지
- Cytoscape 그래프 초기화/파괴는 뷰 라이프사이클과 정렬 (메모리 누수 주의)

## 백엔드 연동

- HTTP 호출은 **`composables/api.js`의 `useApi()` 훅 경유**. view/service에서 `axios`를 직접 import해 호출하지 말 것
- 현재 baseURL은 `composables/api.js`에 **`http://localhost:8080` 하드코딩** 상태. `.env` 환경변수 이관은 향후 개선 항목 (이관 시 ADR 작성)
- 인증:
  - accessToken은 `localStorage`에 저장, 요청 interceptor가 `Authorization: Bearer` 헤더에 자동 주입
  - refreshToken은 HTTP-only 쿠키로 주고받음 (`withCredentials: true`)
- 백엔드 엔드포인트·응답 규약은 @/api/CLAUDE.md의 controller 계층 참조

## 빌드·배포

- 정적 자산 빌드 산출물은 `dist/`
- Nginx 서빙용 설정은 `web/nginx.conf`, 이미지 빌드는 `web/Dockerfile`
- `docker-compose.yml`의 `mmt-front` 서비스가 빌드된 이미지를 사용 (현재 로컬 개발에서는 컨테이너 대신 `npm run dev` 사용)
