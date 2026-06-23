# ADR 0006: refresh 토큰을 HttpOnly 쿠키로 전송하고, reissue CSRF 는 SameSite=Strict + 만료 access 동반으로 방어

## Status

Accepted

## Context

보안 코드리뷰(2026-06-22) 후속으로 인증 토큰 처리의 잔여 결함을 정리하면서(`docs/specs/security/spec-01-oauth-refresh-httponly-cookie.md`), refresh 토큰의 저장·전송·CSRF 방어 방식을 결정해야 했다.

기존 상태의 문제:

- **#1 OAuth URL 누출** — OAuth 성공 핸들러가 `JwtToken`(@Data) 전체를 redirect URL 의 `token` 쿼리파라미터에 실어, toString 으로 access+refresh 가 URL 에 박혔다. URL 은 브라우저 히스토리·access 로그·Referer 로 새므로 refresh 가 노출됐다. 프론트도 모든 경로에서 refresh 를 `localStorage` 에 저장해 XSS 시 탈취 가능했다.
- **#6 refresh 설계 약함** — refresh 토큰에 subject·jti 가 없어(만료값만) 토큰 자체로 주체 식별이 불가했고, Redis 가 `username → refresh` 단일 슬롯이라 다기기/회전/개별 폐기를 추적할 수 없었다.
- refresh 를 쿠키로 옮기면 reissue 가 **쿠키 자동전송 + 상태변경**이 되어 **CSRF 표면이 새로 생긴다**. 전역 CSRF 는 disabled 상태였다(나머지 API 는 Authorization 헤더 기반 stateless).

결정에 영향을 준 확정 사실:

- **배포 토폴로지(확정):** 프론트와 API 를 **동일 Nginx(동일 호스트)** 에서 서빙한다 — `web/nginx.conf` 가 `/` 는 정적 프론트로, `/api/v1/` 는 백엔드로 리버스프록시(OAuth 콜백 `/oauth2/`·`/login/oauth2` 도 동일 백엔드). 따라서 프론트와 reissue 요청이 **same-origin**(따라서 당연히 same-site)이라 `SameSite=Strict` 쿠키가 reissue 에 정상 첨부된다. cross-site 분리 배포로 바뀌면 본 스킴이 깨지므로(아래 Negative) 그때 ADR 재검토.

## Decision

### 1. refresh 토큰은 HttpOnly 쿠키로만 전송한다

- 쿠키 속성: `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/reissue`, `Max-Age = refresh 만료초`.
- `Path` 를 reissue 엔드포인트로 좁혀 전송 표면을 최소화한다. 발급(OAuth 성공·비밀번호 로그인)·회전(reissue)·클리어(logout)가 **동일 속성 세트**를 쓰도록 단일 출처(`RefreshCookieFactory`)에서 만든다 — 속성이 일부라도 다르면 브라우저가 별도 쿠키를 만들거나 구쿠키가 잔존한다.
- access 토큰은 현행대로 URL(OAuth) / body·Authorization 헤더로 전달하고 프론트 localStorage 에 둔다(소셜 로그인 표준 관행상 수용, 추가 강화는 후속).

### 2. refresh 토큰 하드닝 + 멀티슬롯 키 스킴

- refresh 에 `subject`(주체 email) + `jti`(UUID) 부여.
- Redis 키를 **멀티슬롯 `refresh:{email}:{jti}`** 로 둔다(다기기/개별 회전·폐기 지원).
- **로그아웃 = 해당 사용자 refresh 전수 폐기**(전 기기). `deleteByPrefix("refresh:{email}:")`. per-device 폐기(access↔refresh jti 링크)는 후속 backlog — 멀티슬롯이 데이터 레벨에선 이미 지원하므로 실제 요구 시 access 에 jti 링크만 추가하면 된다.
- 로그아웃은 SecurityContext 가 아니라 **access 토큰에서 직접 subject 를 확정**한다(만료 access 도 `parseClaims` 가 claims 반환). idle 후 만료 access 로 로그아웃하는 가장 흔한 케이스에서 SecurityContext 기반은 주체를 못 잡아 폐기가 no-op 이 되는 보안 회귀를 막기 위함.

### 3. 회전(rotation)과 grace window

- reissue 마다 refresh 회전(새 jti 발급). 직전 jti 는 **즉시 무효화하지 않고 grace window N = 10초** 동안만 유효(지연 무효화).
- 근거: HttpOnly 라 JS 가 cross-tab 으로 refresh 를 직렬화할 수 없어, 다탭이 같은 직전 refresh 를 동시에 보내면 엄격 즉시-회전에서는 한 요청만 성공하고 나머지는 spurious 401(강제 재로그인)을 겪는다. grace 가 이 경쟁을 흡수한다.
- **회전 원자성(수용):** 서버 회전이 check-then-act 라 두 동시 reissue 가 둘 다 직전 슬롯을 통과해 각자 새 슬롯을 만들 수 있다. Redis Lua/CAS 로 원자화하지 않고, **"grace window 안에서 직전 1개 허용"을 명시적 불변식으로 수용**한다. (탈취 토큰 재사용 창은 N=10초로 제한.)
- 회전 시 블랙리스트에 올리는 직전 access 의 TTL = 해당 토큰 잔여 만료초(Redis 무한 증가 방지).

### 4. CSRF 방어

- **전역 CSRF 는 계속 disabled** 로 둔다. reissue 외 API 는 Authorization 헤더 기반 stateless 라 CSRF 비대상.
- reissue 만 두 장치로 보호한다:
  1. **1차 — `SameSite=Strict`**: 크로스사이트 요청에는 refresh 쿠키가 첨부되지 않는다. 프론트·API 가 동일 Nginx 호스트라 reissue 가 same-origin → 정상 첨부(위 토폴로지 전제).
  2. **2차 — 만료 access 동반 요구**: reissue 는 body 로 (만료된) access 도 받아 주체를 확정한다. 공격자가 피해자의 access 를 모르면 성공 불가. 추가로 **`refresh.subject == access.subject` 교차검증**으로 방어층을 유지한다 — "refresh.subject 만으로 단순화"는 2차 방어층 제거이므로 금지.

### 5. 전환기 폴백

- 전환기에는 reissue 가 쿠키 없으면 body refresh 로 폴백(`required=false`). 쿠키·body 동시 도착 시 **쿠키 present 면 쿠키만** 사용.
- 폴백이 살아있는 동안엔 보안 이득이 미실현(localStorage refresh 가 body 로 재생 가능)이므로, **프론트 배포 후 body 폴백 제거(3차 배포)를 완료 기준의 명시 단계**로 둔다.

## Consequences

### Positive
- refresh 가 URL/히스토리/Referer/localStorage 어디에도 남지 않아 #1·XSS 탈취 경로가 닫힌다.
- jti+멀티슬롯으로 다기기 추적·전수 폐기가 성립하고, 만료 access 로그아웃에서도 세션이 실제로 끊긴다.
- grace window 로 정상 사용자의 다탭 spurious 401 이 사라진다.
- 전역 stateless 구조를 깨지 않고 reissue 한 곳만 좁게 보호한다.

### Negative
- 배포 시 Redis 키 스킴 변경(단일→멀티슬롯)으로 **기존 세션 일괄 만료(재로그인)** 를 감수해야 한다 — 배포 공지 필요.
- check-then-act 수용으로, grace(10초) 동안 탈취된 직전 refresh 의 1회 재사용 창이 존재한다(완전 원자화 대비 잔여 리스크).
- **OAuth 경로 배포 커플링:** OAuth redirect 의 `token` 파라미터가 raw access 로 바뀌어, 정규식 파서를 쓰는 현행 프론트와 호환되지 않는다 → OAuth 경로는 프론트(양형식 허용/쿠키 흐름)와 함께 배포해야 한다. reissue 경로는 `required=false`+body 폴백으로 독립 배포 가능.

### Neutral
- `SameSite=None` 회피책(완전 cross-site 배포)은 CSRF 1차 방어를 무력화하므로 **비채택**. 만약 토폴로지가 cross-site 로 바뀌면 본 스킴이 깨지므로(전 사용자 강제 재로그인) ADR 재검토 대상이다.
- grace N=10초는 운영 관찰 후 조정 가능(본 ADR 의 불변식은 "직전 1개를 짧은 창 동안만 허용").

## Alternatives Considered

1. **refresh 를 계속 body/localStorage 로** — 기각. XSS 탈취·URL 누출(#1)을 못 막는다.
2. **per-device 로그아웃(access↔refresh jti 링크)을 지금 도입** — 기각(후속 backlog). access 토큰 스키마 변경 + 회전 시 jti 동기화 불변식이 새로 생겨 표면·테스트가 늘고, 멀티 기기 유지가 명시된 제품 요구가 아니다. "전 기기 폐기"가 더 보수적이고 안전한 디폴트.
3. **전역 CSRF 토큰(동기화 토큰 패턴) 활성화** — 기각. 나머지 stateless API 까지 영향을 주고, same-site SPA 에서는 `SameSite=Strict`+만료 access 동반으로 reissue 표면을 충분히 좁힐 수 있다.
4. **즉시 회전(grace 없음)** — 기각. HttpOnly 라 cross-tab 직렬화가 불가해 정상 사용자의 다탭에서 spurious 401 이 발생한다.
5. **Redis Lua/CAS 로 회전 원자화** — 현 시점 미채택(추가 복잡도). grace+직전1개 허용 불변식으로 실용적 안전성 확보. 향후 필요 시 도입 가능.

## References

- 적용 spec: `docs/specs/security/spec-01-oauth-refresh-httponly-cookie.md` (Task 1~5, design-review 2라운드 freeze)
- 구현 커밋(브랜치 `spec/01-oauth-refresh-httponly-cookie`): Task 4a(하드닝·logout) / Task 1(OAuth 쿠키) / Task 2(reissue 쿠키·`RefreshCookieFactory`) / Task 3(비밀번호 로그인 통일)
- 핵심 클래스: `jwt/TokenProvider`, `jwt/RefreshCookieFactory`, `service/user/AuthService`, `controller/AuthController`, `oauth2/OAuth2AuthenticationSuccessHandler`
- 관련 규칙: 루트 `CLAUDE.md` 피처플래그·마이그레이션·ADR 규칙, `api/CLAUDE.md` 보안 섹션
