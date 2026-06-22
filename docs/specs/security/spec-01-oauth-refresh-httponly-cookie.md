# Spec 01: OAuth/로그인 refresh 토큰 → HttpOnly 쿠키 재설계

**출처:** 보안 코드리뷰 (2026-06-22) 핫픽스 후속. 핫픽스 브랜치 `fix/security-auth-hotfix` 에서 분리.
**대상 결함:** #1(OAuth **URL** refresh 누출, 미해결분) + #6(refresh 토큰 설계 약함)
**예상 소요:** 1.5~2일 (백엔드 + 프론트 + CSRF/검증)
**선행:** 본 핫픽스 5커밋 반영 완료 — `c24c8a7`(#2·#3·#4), `7e126be`(#1-헤더·#8), `2db7ff6`(#5), `1e49c16`(#9), `1d375ca`(#7)
**실행:** **다른 세션에서 실행** (본 세션은 spec 작성까지). 실행 시 `/analyze-before-change` 로 시작.

> ⚠️ 본 변경은 **백엔드+프론트에 걸친 인증 계약 변경**이며 reissue 가 상태변경이라 **CSRF 표면이 새로 생긴다**. 로컬에서 OAuth 실제 왕복을 재현하기 어려우므로(소셜 IdP 콜백 필요) 단계별 롤백 가능한 단위로 쪼개고, 각 단계마다 수동 검증 절차를 둔다.

---

## 배경 (현재 상태 — Analyze-Before-Change 사전 조사)

핫픽스 이후에도 남은 누출 경로와 약점:

1. **OAuth URL refresh 누출** — `OAuth2AuthenticationSuccessHandler.determineTargetUrl()` (`api/.../oauth2/OAuth2AuthenticationSuccessHandler.java:50-52`)
   ```java
   return UriComponentsBuilder.fromUriString(targetUrl)
           .queryParam("token", token)   // token == JwtToken(@Data) → toString 으로 access+refresh 가 URL 에 박힘
           .build().toUriString();
   ```
   프론트 `web/src/views/OauthLogin.vue:14-25` 가 이 toString 을 **정규식**으로 파싱해 두 토큰을 꺼내 localStorage 에 저장.
   → URL 은 브라우저 히스토리·access 로그·Referer 로 유출되므로 **refresh 토큰을 URL 에 절대 두면 안 됨**.

2. **refresh 토큰 설계 약함 (#6)** — `TokenProvider.generateToken()` (`api/.../jwt/TokenProvider.java`)
   ```java
   String refreshToken = Jwts.builder()
           .signWith(key, SignatureAlgorithm.HS256)
           .setExpiration(new Date(now + refreshTokenValidityInMilliseconds))
           .compact();                                   // subject·claims·jti 전무 (만료값만)
   redisUtil.set(authentication.getName(), refreshToken, getExpiration(refreshToken)); // username 키, 단일 슬롯
   ```
   - refresh 토큰에 subject 가 없어 **토큰 자체로 주체 식별 불가** → reissue 가 별도로 accessToken 에서 email 을 꺼내 의존 (`AuthService.reissue`, `api/.../service/user/AuthService.java:44-67`).
   - Redis 가 username→refresh 단일 슬롯이라 다중 기기/회전 추적 불가, jti 부재로 개별 무효화 불가.

3. **저장 위치** — 프론트는 모든 경로에서 refresh 를 **localStorage** 저장 (`web/src/store/index.js:28-40`, 쿠키 코드는 주석). XSS 시 탈취 가능. `composables/api.js:11` 은 이미 `withCredentials: true`.

4. **연관 호출 지점:**
   - `AuthController.reissue` (`api/.../controller/AuthController.java:67-76`): body `TokenDTO` 의 accessToken+refreshToken 을 받아 `authService.reissue` 호출.
   - 프론트 reissue: `web/src/service/AuthService.js:24-56` 가 localStorage refreshToken 을 body 로 전송.
   - 비밀번호 로그인: `AuthController.login` (`:52-62`) → body `TokenDTO` 로 refresh 반환 → `SignUpView.vue:155-156`, `AppTopbar.vue:93-94` 가 localStorage 저장.
   - CORS: `WebConfig` `allowCredentials(true)` (핫픽스에서 origin 병합 완료) — 쿠키 송수신 전제 충족.
   - 보안: `SecurityConfig` CSRF **disabled** — 쿠키 기반 reissue 도입 시 CSRF 재검토 필요(Task 4).

---

## 범위

- **포함:** refresh 토큰을 `HttpOnly; Secure; SameSite=Strict` 쿠키로 전환(OAuth + 비밀번호 로그인 양 경로 통일), URL 에서 refresh 제거(access 만 잔류), reissue 를 쿠키 기반으로 변경, refresh 토큰 하드닝(subject+jti+회전), CSRF 대응.
- **비포함:** access 토큰의 URL 잔류 제거(소셜 로그인 표준 관행상 수용; 추가 강화는 후속), access 토큰 저장소를 메모리 전용으로 옮기는 것(별도 검토).
  - ℹ️ #7(프로덕션 로깅)은 본 spec 작성 시점엔 후속 대상이었으나 이후 `1d375ca` 로 **완료**됨(`application-secure.yml` 에서 INFO/WARN·show_sql:false 오버라이드). 더는 본 spec 의 후속 범위 아님.

---

## Task 1 — 백엔드: OAuth 성공 핸들러에서 refresh 를 쿠키로, URL 은 access 만

**파일:** `api/.../oauth2/OAuth2AuthenticationSuccessHandler.java`

- `onAuthenticationSuccess` 에 `HttpServletResponse` 가 이미 주입됨 → 거기에 refresh 쿠키 set.
- `determineTargetUrl` 의 `.queryParam("token", token)` → `.queryParam("token", token.getAccessToken())` 로 변경(access 문자열만).
- 쿠키 스펙: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth/reissue`(전송 범위 최소화), `Max-Age=refresh 만료초`.

```java
// 의사코드
ResponseCookie cookie = ResponseCookie.from("refreshToken", token.getRefreshToken())
        .httpOnly(true).secure(true).sameSite("Strict")
        .path("/api/v1/auth/reissue")
        .maxAge(Duration.ofSeconds(refreshTokenValiditySeconds))
        .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
```

> ⚠️ `Path=/api/v1/auth/reissue` 로 잡으면 reissue 요청에만 쿠키가 실려 표면이 좁아진다. 단 reissue 엔드포인트 경로와 정확히 일치해야 하므로 컨트롤러 매핑과 동기화할 것.

**검증:** OAuth 콜백 후 리다이렉트 URL 에 `accessToken` 만 있고 `refreshToken` 문자열이 없음. 응답에 `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict` 존재. ⚠️ **(review #5)** `Secure` 쿠키는 secure context 에서만 저장된다 — **검증은 `localhost`/`127.0.0.1`(예외 허용) 또는 https 에서만 유효**하고, LAN IP(http) 로 띄우면 쿠키가 저장 안 돼 검증이 헛돈다.

---

## Task 2 — 백엔드: reissue 를 쿠키 기반으로

**파일:** `AuthController.reissue` (`:67-76`), `AuthService.reissue`

- reissue 가 refresh 를 **쿠키에서** 읽도록 변경: `@CookieValue(name = "refreshToken", required = false) String refreshToken`.
- 만료된 access 토큰은 계속 body 로 받음(주체 email 추출용) — 단 access 가 만료되어도 `parseClaims` 가 `ExpiredJwtException` 에서 claims 반환하므로 email 추출 가능(현행 동작 유지, `TokenProvider.parseClaims`).
- 새 토큰 발급 시 새 refresh 를 **다시 쿠키로** 내려줌(회전). 응답 body `TokenDTO` 에서는 refreshToken 필드를 비우거나 제거.
  - ⚠️ **(review #4)** 회전 재발급 `Set-Cookie` 는 Task 1 과 **동일한 속성 세트**(name·Path·Domain·Secure·HttpOnly·SameSite)를 그대로 박을 것 — 일부만 다르면 브라우저가 별도 쿠키를 만들거나 구쿠키가 잔존한다.
- ⚠️ **(review #2) 동시·다탭 reissue 경쟁:** HttpOnly 라 JS 가 refresh 를 직렬화/조정할 수 없어 다탭이 같은 RT0 를 동시에 보낸다. 엄격 회전이면 한 요청만 RT1 로 회전하고 나머지는 `RT0 != 저장된 RT1` 로 **spurious 401 → 강제 재로그인**(쿠키 전환이 localStorage 대비 클라이언트측 조정을 새로 차단). 둘 중 하나를 **명세 확정**: (a) reuse-detection grace window(직전 jti 단기 허용), 또는 (b) 프론트 reissue single-flight(동시 1건으로 직렬화, `AuthService.js`).
- 하위호환: 전환 기간 동안 `required=false` 로 두고 쿠키 없으면 기존 body 경로 폴백. ⚠️ **(review #3)** 폴백이 살아있는 동안엔 **보안 이득 미실현**(localStorage refresh 가 body 로 여전히 재생 가능) → 폴백 제거(프론트 배포 후 3차 배포)를 완료기준의 명시 단계로 둔다.

**검증:** access 만료 상태에서 reissue 호출 시 쿠키만으로 갱신 성공, 새 `Set-Cookie` 로 refresh 회전, body 에 refresh 노출 없음.

---

## Task 3 — 백엔드: 비밀번호 로그인도 동일하게 쿠키로 통일

**파일:** `AuthController.login` (`:52-62`)

- 일관성을 위해 비밀번호 로그인도 refresh 를 쿠키로 발급, body `TokenDTO.refreshToken` 제거(access 만 body).
- 이 단계는 #1(OAuth URL) 자체와는 독립이나, 두 경로가 다른 저장방식이면 reissue/프론트 분기가 복잡해지므로 **함께 통일** 권장. 분리 실행도 가능(그 경우 Task 5 프론트 작업이 경로별로 갈림).

---

## Task 4 — refresh 토큰 하드닝 (#6) + CSRF 대응

### 4a. 토큰 하드닝
**파일:** `TokenProvider.generateToken`, `AuthService.reissue`, **`AuthService.logout`** (review #1)

- refresh 토큰에 `setSubject(authentication.getName())` + `setId(jti)`(UUID) 부여.
- Redis 키를 `username` 단일 슬롯에서 `refresh:{username}:{jti}` 또는 화이트리스트 set 으로 확장(다기기/명시적 회전·폐기 지원). 최소안: jti 만 추가하고 회전 시 이전 jti 블랙리스트. **(이 선택이 logout 동작과 직결 — 아래 🚫 항목)**
- reissue 시 **이전 refresh 를 무효화**(회전): 현재도 access 를 블랙리스트에 넣음 — refresh 도 jti 기준 무효화 추가.
- 🚫 **(review #1, Blocker) logout 의 서버측 refresh 폐기를 키 스킴과 반드시 동기화한다.** 현재 `AuthService.logout` 은 `redisUtil.delete(userEmail)`(단일 키, `AuthService.java:78`)로 지운다. **다중슬롯(`refresh:{username}:{jti}`)으로 바꾸면 이 단일키 delete 는 no-op** → 로그아웃 후에도 포획된 refresh 쿠키 + (만료/블랙리스트된) body access 로 reissue 가 성공(`parseClaims` 가 만료 무관하게 email 추출) = **로그아웃이 세션을 못 끊는 보안 회귀**. 스킴 채택과 무관하게 "logout = 해당 사용자 refresh 전수 폐기"가 성립해야 한다:
  - 다중슬롯이면 `redisUtil.deleteByPrefix("refresh:" + userEmail + ":")` (프리미티브 이미 존재, `RedisUtil.java:43`).
  - 단일슬롯 유지면 현 `delete(userEmail)` 그대로 유효 → Blocker 회피.
  - 부수 주의: 현 logout 은 `SecurityUtil.getCurrentUserEmail()` 의존 — access 가 이미 만료/무효라 컨텍스트가 비면 `email=""` 로 폐기가 no-op. 쿠키 전환 시 logout 은 만료 access 라도 주체를 확정해 폐기하도록 함께 점검.

### 4b. CSRF
- reissue 는 상태변경 + 쿠키 자동전송 → CSRF 가능. 1차 방어는 **`SameSite=Strict`**(크로스사이트 요청에 쿠키 미전송). SPA 가 동일 사이트이므로 정상 동작.
- 추가로 reissue 는 만료 access 토큰(body)도 요구하므로 공격자가 피해자의 access 를 모르면 성공 불가 — 사실상 2차 방어. 이 점을 ADR 에 근거로 기록.
- `SecurityConfig` 의 전역 CSRF disable 은 유지(나머지는 Authorization 헤더 기반 stateless 라 CSRF 비대상). reissue 만 위 두 장치로 보호한다는 결정을 ADR 로 남길 것.

> **ADR 작성 대상:** "refresh 토큰 전송을 HttpOnly 쿠키로, CSRF 는 SameSite=Strict + 만료 access 동반으로 방어" — `docs/adr/` 에 신규 ADR.

---

## Task 5 — 프론트: localStorage refresh 제거, 쿠키 흐름으로

**파일:** `web/src/views/OauthLogin.vue`, `web/src/store/index.js`, `web/src/service/AuthService.js`, `web/src/layout/AppTopbar.vue:93-94,129-132`, `web/src/views/SignUpView.vue:155-156`, `web/src/views/UserEditView.vue:176-179`

- `OauthLogin.vue`: 정규식 파싱 제거 → `token` 쿼리파라미터를 **access 토큰 그대로** 사용, `setAccessToken` 만 호출.
- `store/index.js`: `setRefreshToken`/`getRefreshToken`/localStorage refresh 제거. access 만 관리(또는 메모리). 주석 처리된 쿠키 코드도 정리.
- `AuthService.js` reissue: body 에서 refreshToken 제거(쿠키 자동전송), `withCredentials` 이미 true. 응답에서 refresh 읽던 부분 제거.
- 로그아웃: refresh 쿠키 만료(서버가 `Set-Cookie Max-Age=0`)로 처리하도록 백엔드 로그아웃에 쿠키 클리어 추가 검토(`AuthController.logout :81-89`).
  - ⚠️ **Task 1 ↔ 여기 자기정합성:** Task 1 이 refresh 쿠키를 `Path=/api/v1/auth/reissue` 로 좁히므로, (a) 로그아웃은 `DELETE /api/v1/auth/authentication` 이라 브라우저가 그 경로엔 refresh 쿠키를 **애초에 보내지 않고**, (b) 쿠키 삭제용 `Set-Cookie Max-Age=0` 도 **반드시 동일한 `Path=/api/v1/auth/reissue` 속성을 명시**해야 브라우저가 해당 쿠키를 지운다(다른 Path 의 Max-Age=0 은 무시됨). → 로그아웃 응답의 클리어 쿠키에 같은 Path 를 박거나, refresh 쿠키 Path 스코프를 재검토할 것.

**검증:** 로그인/재발급/로그아웃 전 과정에서 localStorage 에 refreshToken 키가 생기지 않음. devtools Application→Cookies 에 HttpOnly refresh 쿠키 확인.

---

## 영향받는 테스트

- 현재 인증 관련 단위/통합 테스트는 최소(`ApiApplicationTests`, `RedisUtilTest`). 본 spec 실행 시 신규 추가 권장:
  - `OAuth2AuthenticationSuccessHandlerTest`: URL 에 refresh 부재 + Set-Cookie 존재.
  - `AuthControllerReissueTest`(`@WebMvcTest`): 쿠키 기반 reissue 성공/쿠키 부재 시 401, 회전 확인.
  - `TokenProviderTest`: refresh subject/jti 부여, 회전 시 이전 jti 무효화.
  - `LogoutRevocationTest`(review #1): 로그아웃 후 직전 발급 refresh 쿠키로 reissue → **401**(서버측 폐기 성립). 다중슬롯 채택 시 `deleteByPrefix` 동작 포함.

---

## 완료 기준

- [ ] OAuth 콜백 리다이렉트 URL 에 refresh 토큰 문자열이 없다(access 만).
- [ ] refresh 가 `HttpOnly; Secure; SameSite=Strict` 쿠키로만 전달된다(OAuth+비번 로그인).
- [ ] reissue 가 쿠키 기반으로 동작하고 호출 시 refresh 가 회전된다.
- [ ] 프론트 localStorage 에 refreshToken 이 저장되지 않는다.
- [ ] refresh 토큰에 subject+jti 존재, 회전 시 이전 토큰 무효화.
- [ ] **(review #1) 로그아웃이 채택한 키 스킴에서 해당 사용자 refresh 를 서버측에서 실제로 폐기한다** — 로그아웃 후 포획 refresh 쿠키로 reissue 시 401.
- [ ] **(review #2) 동시·다탭 reissue 정책(grace window 또는 프론트 single-flight)이 명세·구현되어 정상 사용자가 spurious 401 을 겪지 않는다.**
- [ ] CSRF 방어(SameSite=Strict + 만료 access 동반) 및 결정 근거 ADR 기록.
- [ ] **(review #3) 전환기 body 폴백 제거(3차 배포) 완료** — 폴백 잔존 시 localStorage refresh 재생 가능하므로 보안 이득 미실현.
- [ ] 신규 인증 테스트 통과.

---

## 롤백 시나리오

- 단계별 독립 커밋(Task 단위). 백엔드 Task 1~2 는 `@CookieValue(required=false)` + body 폴백으로 **프론트보다 먼저 안전 배포** 가능.
- 문제 시: 프론트 미배포 상태면 백엔드는 쿠키+body 양쪽 발급으로 되돌리고(쿠키 추가는 무해), 프론트 배포 후 회귀 시 프론트만 직전 빌드로 revert.
- 토큰 하드닝(Task 4a)은 Redis 키 스킴 변경을 동반하므로 배포 시점에 기존 세션 일괄 만료(재로그인) 감수 — 배포 공지 필요.

---

## 비범위 / 후속

- ~~#7 프로덕션 로깅~~ → **완료** (`1d375ca`): `application-secure.yml` 에서 `com.mmt`/`security`/`data.neo4j` INFO·`hibernate.SQL` WARN·`show_sql:false` 오버라이드. base 의 DEBUG 는 로컬 dev 유지.
- access 토큰의 URL 잔류 제거 / 메모리 전용 저장 → 별도 검토.

---

## 참조

- 보안 코드리뷰 결과: 레포 루트 `SECURITY-REVIEW.md` 는 **휘발성**(커밋 비대상·이미 워킹트리에서 제거됨)이므로 참조용 핵심 결함(#1-URL·#6) 요약은 본 문서 §배경에 인라인되어 있다. 실행 세션은 §배경만으로 컨텍스트가 충족된다.
- 핫픽스 커밋 매핑(브랜치 `fix/security-auth-hotfix`):
  `c24c8a7`=#2·#3·#4(인가 잠금+IDOR), `7e126be`=#1-헤더·#8, `2db7ff6`=#5(CORS), `1e49c16`=#9, `1d375ca`=#7(운영 로깅).
- 루트 CLAUDE.md 피처플래그·마이그레이션·ADR 규칙, `api/CLAUDE.md` 보안 섹션.
