# ADR 0009: 프로덕션 빌드에서 web API baseURL 을 same-origin 으로 전환

## Status
Accepted

## Context
`web/src/composables/api.js` 의 `useApi()` 는 axios baseURL 을 `http://localhost:8080`
으로 **하드코딩**하고 있었다(web/CLAUDE.md 에 "환경변수 이관은 향후 개선 항목, 이관 시 ADR" 로 명시됨).

M6(상시 프로덕션 배포, `docs/specs/m6/spec-01`)에서 이 하드코딩이 라이브 성공기준을 직접 막는
블로커로 드러났다:
- 빌드된 SPA 가 `https://www.my-math-teacher.com` 에서 서빙되면, 브라우저 JS 는
  `http://localhost:8080`(=사용자 자기 PC)을 호출한다. https 페이지의 http 요청이라
  **mixed-content 로 브라우저가 차단**한다. → §4 "실브라우저 핵심 플로우" 100% 실패.
- 반면 프론트의 XHR 엔드포인트는 전부 상대경로(`/api/v1/...`)이고, nginx 가
  `/api/v1/`·`/oauth2/`·`/login/oauth2` 를 백엔드로 프록시한다(`web/nginx.conf`).
  즉 baseURL 만 same-origin 으로 바꾸면 프록시 경유로 성립한다. 백엔드는 이미 prod 정렬됨
  (`application-secure.yml` redirect-uri·OAuth success handler 모두 prod 도메인).

제약: 로컬 dev 는 vite(프론트 5173)와 백엔드(8080)가 **다른 오리진**이라 dev 에서는
`localhost:8080` 이 유지돼야 한다. 또 호출처 일부(`api/v1/auth/authentication`,
`api/v1/weakness-diagnosis`)는 leading slash 가 없어, 빈 문자열 baseURL 로 두면
axios 가 페이지-상대경로로 해석해 깨진다 → baseURL 은 **비어있지 않은 오리진**이어야 안전하다.

## Decision
빌드 모드로 분기한다:

```js
const baseURL = import.meta.env.PROD ? window.location.origin : 'http://localhost:8080';
```

- 프로덕션 빌드(`npm run build`, `import.meta.env.PROD===true`) → `window.location.origin`
  = 서빙된 도메인(same-origin). 도메인을 코드에 하드코딩하지 않는다(EIP-only·도메인 변경에도 무탈).
- dev(`npm run dev`) → 기존 `http://localhost:8080` 유지.

`.env` 파일·`VITE_*` 변수 도입은 하지 않았다 — vite 내장 `import.meta.env.PROD` 로 충분하고
추가 설정 파일 없이 same-origin 이 달성된다.

## Consequences

### Positive
- https 프로덕션에서 mixed-content 차단 해소, refreshToken 쿠키가 same-origin(withCredentials
  cross-origin 이슈 제거).
- 도메인 하드코딩 0 → EIP/도메인 변경에도 프론트 코드 무변경.
- 로컬 dev 워크플로우 무변경.

### Negative
- prod 빌드가 nginx same-origin 서빙에 **의존**한다(SPA·API 를 같은 오리진에서 서빙해야 함).
  현재 아키텍처가 정확히 그 구조(단일 nginx)라 전제 성립.

### Neutral
- 향후 프론트를 API 와 다른 오리진(CDN 등)에서 서빙하려면 `VITE_API_BASE_URL` 류 명시 설정으로
  재이관 필요 — 그때 별도 ADR.

## Alternatives Considered
1. `baseURL = ''`(빈 문자열 same-origin) — 기각. leading slash 없는 호출처가 페이지-상대경로로
   깨지고, dev(5173→8080)도 함께 깨진다.
2. `.env.production` 에 `VITE_API_BASE_URL=https://www.my-math-teacher.com` — 도메인 하드코딩·
   추가 설정 파일. same-origin 이면 `window.location.origin` 으로 충분해 과함.
3. 프론트 코드 무변경 + nginx 에서 localhost 재작성 — 불가(브라우저 JS 가 이미 절대 URL 로 발사).

## References
- 관련 spec: `docs/specs/m6/spec-01-always-on-production-deploy.md` (§8 분석·블로커①)
- 관련 ADR: ADR-0007(blue-green), ADR-0008(SSM 배포 채널)
