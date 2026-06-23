# spec-01 배포 런북 — refresh HttpOnly 쿠키 전환 (무중단 3단계)

**대상 변경:** spec-01 (OAuth/로그인 refresh → HttpOnly 쿠키). 구현 브랜치 `spec/01-oauth-refresh-httponly-cookie`.
**결정 근거:** ADR 0006 (무엇을·왜). 본 문서는 **어떻게 굴릴지**(절차·트리거·검증·롤백)만 다룬다.
**핵심 제약:** 인증 계약이 백엔드+프론트에 걸쳐 바뀐다. 한 번에 폴백 없이 배포하면 배포 시간차/캐시된 구프론트에서 reissue 가 깨져 **전 사용자 강제 로그아웃**이 난다. 그래서 폴백을 두고 3단계로 점진 전환한다.

> ⚠️ OAuth 실제 왕복(소셜 IdP 콜백)은 로컬 재현 불가 → 각 단계 검증은 staging/prod 에서 수동 확인. `Secure` 쿠키는 https/`localhost`/`127.0.0.1` 에서만 저장되므로 **LAN IP(http) 로는 검증이 헛돈다**(review#5).

---

## 사전 (1차 이전) — 토폴로지·기준 확인

- [ ] 프론트(`www.my-math-teacher.com`)와 API 가 **same-site**(`*.my-math-teacher.com`)인지 재확인. cross-site 면 `SameSite=Strict` 쿠키가 reissue 에 안 실려 스킴이 깨진다(ADR 0006 Negative). → 깨지면 중단.
- [ ] 점검 창(off-peak) 공지: 1차에서 **기존 세션 일괄 만료(재로그인 1회)** 가 발생함을 사용자에게 안내.

---

## 1차 배포 — 백엔드 (쿠키 발급 + body 폴백 유지)

**배포 내용:** Task 4a·1·2·3·4b (커밋 `b70134d`·`a1b4b33`·`34caed3`·`f999904`·`bb9a9e5`).

- refresh 발급이 멀티슬롯 `refresh:{email}:{jti}` 로 전환된다 → **기존 단일슬롯 세션은 reissue 불가 → 재로그인**. (Redis 를 수동으로 비울 필요는 없음. 구 키는 자체 TTL 로 소멸. 즉시 정리하려면 운영자가 구 키 패턴만 SCAN 삭제.)
- reissue 는 `@CookieValue(required=false)` — **쿠키 없으면 body refresh 로 폴백**. 그래서 아직 구프론트(localStorage refresh 를 body 로 전송)도 정상 동작한다.
- ⚠️ **OAuth 경로는 1차에 단독 배포 금지.** OAuth redirect 의 `token` 파라미터가 raw access 로 바뀌어 구프론트 정규식 파서가 못 읽는다 → OAuth 로그인은 **2차(프론트)와 협응 배포**해야 한다. reissue/login(비밀번호) 경로만 1차에 선행 가능.

**검증:**
- [ ] 비밀번호 로그인 응답에 `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/reissue` 존재, body 에 refresh 없음.
- [ ] (구프론트로) reissue 가 body 폴백으로 여전히 성공 — 무중단 확인.
- [ ] 로그아웃 후 reissue → 401 (서버측 전수 폐기 동작).

**롤백:** 백엔드만 직전 빌드로 revert. 쿠키 추가는 무해했으므로 구프론트 영향 없음. (단 멀티슬롯→단일슬롯 복귀 시 또 한 번 세션 만료.)

---

## 2차 배포 — 프론트 + OAuth (쿠키 흐름 전환)

**배포 내용:** Task 5 (커밋 `f4b45fe`). 1차 백엔드의 OAuth 핸들러 변경도 이 시점에 함께 유효해진다(협응).

- 프론트: localStorage refresh 제거, reissue body 에서 refresh 제거, `OauthLogin.vue` 신형식(raw access).
- 이제 정상 사용자는 refresh 를 **쿠키로만** 주고받는다.
- ⚠️ **아직 끝 아님:** 백엔드 body 폴백이 살아있어, 탈취/캐시된 localStorage refresh 를 body 로 보내면 reissue 가 받아준다 = **보안 이득 미실현**(review#3). 3차에서 닫는다.

**검증:**
- [ ] 로그인/재발급/로그아웃 전 과정에서 `localStorage` 에 `refreshToken` 키가 생기지 않음(devtools Application).
- [ ] devtools → Cookies 에 HttpOnly `refreshToken` 쿠키 확인(Path=/api/v1/auth/reissue).
- [ ] OAuth 로그인 왕복 성공(소셜 3종), redirect URL 에 refresh 문자열 없음(access 만).
- [ ] 다탭 동시 새로고침(만료 직전)에서 spurious 401/강제 로그아웃 없음(grace 10초 동작).

**롤백:** 프론트만 직전 빌드로 revert. 백엔드 폴백이 살아있으므로 구프론트가 body refresh 로 즉시 복구된다(이게 폴백을 3차까지 유지하는 이유).

---

## 3차 배포 — 백엔드 (body 폴백 제거, 잠금)  ← 유일한 미구현 단계

**트리거(날짜 아님, 조건):** 구프론트 트래픽이 0 에 수렴. 판단 근거 중 하나:
- 2차 프론트 배포 후 (access TTL + CDN/브라우저 캐시 TTL) 경과, **또는**
- reissue 가 쿠키 없이 body refresh 만으로 처리된 건수(로그/메트릭)가 0 으로 수렴.

**배포 내용(구현 필요):**
- `AuthController.reissue` 의 폴백 분기 제거:
  - 현재: `String refreshToken = StringUtils.hasText(refreshCookie) ? refreshCookie : request.getRefreshToken();`
  - 변경: 쿠키 전용. 쿠키 없으면 401. `@CookieValue(required=true)` 또는 명시적 누락 검사. body 의 `refreshToken` 은 더 이상 읽지 않는다(원하면 `TokenDTO.refreshToken` 자체를 reissue 요청에서 제거).
- 이때 비로소 **localStorage 재생 공격면이 완전히 닫힘** = spec-01 §완료 기준의 마지막 체크박스("전환기 body 폴백 제거(3차 배포) 완료").

**검증:**
- [ ] 쿠키 없이 body refresh 만 담아 reissue 호출 → **401**(폴백 차단 확인).
- [ ] 정상(쿠키 보유) 사용자 reissue 는 계속 성공.

**롤백:** 백엔드만 직전 빌드로 revert 하면 폴백이 되살아난다. 단 롤백은 보안 회귀이므로, 3차에서 깨지면 원인(잔존 구프론트)을 먼저 확인.

---

## 요약 표

| 단계 | 배포 | 폴백 | refresh 출처 | 끝났나 |
|---|---|---|---|---|
| 1차 | 백엔드(쿠키 발급+폴백) | 살아있음 | 쿠키 발급되나 구프론트는 body | 무중단 확보 |
| 2차 | 프론트+OAuth | 살아있음 | 정상=쿠키, 그러나 폴백 잔존 | 보안 이득 **미실현** |
| 3차 | 백엔드(폴백 제거) | 제거 | 쿠키 전용 | **완료** |

## 참조
- 결정: `docs/adr/0006-refresh-token-httponly-cookie-csrf.md`
- spec: `docs/specs/security/spec-01-oauth-refresh-httponly-cookie.md` (§완료 기준·§롤백 시나리오)
- 구현 브랜치: `spec/01-oauth-refresh-httponly-cookie` (커밋 `b70134d`~`f4b45fe`)
