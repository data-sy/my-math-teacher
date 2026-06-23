# spec-01 배포 런북 — refresh HttpOnly 쿠키 전환

**대상 변경:** spec-01 (OAuth/로그인 refresh → HttpOnly 쿠키). 구현 브랜치 `spec/01-oauth-refresh-httponly-cookie`.
**결정 근거:** ADR 0006 (무엇을·왜). 본 문서는 **어떻게 굴릴지**(절차·트리거·검증·롤백)만 다룬다.

> **현재 코드 상태(중요):** reissue 는 **refresh 쿠키 전용**이다 — 전환기 body 폴백을 **제거 완료**(쿠키 없으면 401). 즉 코드는 이미 보안 최종 상태이며, **단일 다운타임 배포**(아래 A)를 1순위로 가정한다. 무중단 롤링(B)을 하려면 폴백을 일시적으로 되살려야 한다.

> ⚠️ OAuth 실제 왕복(소셜 IdP 콜백)은 로컬 재현 불가 → 검증은 staging/prod 에서 수동. `Secure` 쿠키는 https/`localhost`/`127.0.0.1` 에서만 저장되므로 **LAN IP(http) 로는 검증이 헛돈다**(review#5).

---

## 공통 — 어떤 경로든 반드시 일어나는 일

- **기존 로그인 사용자 전원 강제 재로그인 1회.** Redis refresh 키 스킴이 단일슬롯→멀티슬롯(`refresh:{email}:{jti}`)으로 바뀌어 기존 세션은 reissue 불가. 단계를 합치든 나누든 동일하며 피할 수 없다. (구 키는 자체 TTL 로 소멸. 즉시 정리하려면 운영자가 구 키 패턴만 SCAN 삭제.)
- **토폴로지 전제:** 프론트(`www.my-math-teacher.com`)와 API 가 same-site(`*.my-math-teacher.com`). cross-site 면 `SameSite=Strict` 쿠키가 reissue 에 안 실려 스킴이 깨진다(ADR 0006). → 깨지면 중단.

---

## A. 단일 다운타임 배포 (현재 코드 기준 1순위)

**언제 OK:** 점검창(짧은 다운타임)을 받아들이거나 활성 세션이 사실상 0 인 저트래픽 상황. 1인 운영 서비스의 기본 경로.

**전제 조건 3가지:**
1. **다운타임 수용** 또는 활성 세션 0 — 배포 시간차에 reissue 가 깨질 세션이 없음.
2. **백엔드+프론트 원자적(동시) 배포** — 시간차 자체가 없으니 폴백 불필요.
3. **정적 자산 캐시버스팅 정상** — Vite 가 파일명에 해시(`index-xxxx.js`)를 박으므로, `index.html` 만 장기 캐시되지 않으면 returning 사용자가 새 번들을 받는다. Service Worker 없음 가정.

**절차:**
1. (선택) 점검 공지.
2. 백엔드 + 프론트 동시 배포(브랜치 `spec/01-...` 전체 = Task 4a·1·2·3·5 + 폴백 제거).
3. 스모크 검증(아래).

**검증:**
- [ ] 비밀번호 로그인 → 응답 `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/reissue`, body 에 refresh 없음.
- [ ] OAuth 로그인 왕복 성공(소셜 3종), redirect URL 에 refresh 문자열 없음(access 만).
- [ ] access 만료 후 reissue → 쿠키만으로 갱신 성공 + 새 Set-Cookie 회전.
- [ ] **쿠키 없이 reissue 호출 → 401**(폴백 차단 확인).
- [ ] 로그아웃 → reissue 401(서버측 전수 폐기) + refresh 쿠키 클리어.
- [ ] devtools: `localStorage` 에 `refreshToken` 키 안 생김 / Cookies 에 HttpOnly refresh 존재.

**잔여 엣지(수용 또는 캐시 헤더로 완화):** 공격적으로 캐시된 옛 SPA 번들을 가진 returning 사용자는 옛 JS(정규식 파서/없어진 body 폴백)로 OAuth 파싱 실패·reissue 401 을 겪다가 **하드 리프레시(새 번들 로드)** 시 정상화. `index.html` 캐시를 짧게(또는 no-store) 두면 거의 안 생긴다.

**롤백:** 백엔드+프론트를 직전 빌드로 동시 revert. 또 한 번 세션 만료(재로그인) 감수.

---

## B. 무중단 롤링 (라이브 트래픽을 받으며 0 다운타임이 필요할 때만)

**핵심:** 백엔드를 프론트보다 먼저 띄우는 시간차에 reissue 가 깨지지 않도록 **body 폴백을 일시 복원**해야 한다. 현재 코드는 폴백을 제거했으므로, 이 경로를 택하면 reissue 의 쿠키 전용 변경을 **한시적으로 되돌린다**(쿠키 present→쿠키, 없으면 body refresh 폴백). 전환 완료 후 다시 제거(=현재 코드로 복귀).

**1차 — 백엔드(쿠키 발급 + body 폴백 복원).** reissue/login(비밀번호) 만 선행 가능. 구프론트는 body 폴백으로 정상 동작. ⚠️ OAuth 경로는 URL 형식이 바뀌어 구프론트 정규식 파서와 호환 안 됨 → **2차와 협응 배포**.

**2차 — 프론트 + OAuth.** localStorage refresh 제거·쿠키 흐름. 정상 사용자는 쿠키로만. 단 백엔드 폴백이 살아있어 탈취/캐시된 localStorage refresh 가 body 로 재생 가능 → **보안 이득 미실현**.

**3차 — 백엔드(폴백 재제거 = 현재 코드로 복귀).** 트리거(날짜 아님): 구프론트 reissue(body-refresh) 사용이 0 으로 수렴 — 2차 후 (access TTL + 캐시 TTL) 경과 또는 body-refresh 사용 로그 0. 이때 localStorage 재생면이 닫힘 = 완료.

| 단계 | 폴백 | refresh 출처 | 보안 |
|---|---|---|---|
| 1차 | 복원 | 구프론트=body | 미실현 |
| 2차 | 복원 | 정상=쿠키, 폴백 잔존 | 미실현 |
| 3차 | 제거 | 쿠키 전용 | **완료** |

---

## 참조
- 결정: `docs/adr/0006-refresh-token-httponly-cookie-csrf.md`
- spec: `docs/specs/security/spec-01-oauth-refresh-httponly-cookie.md`
- 구현 브랜치: `spec/01-oauth-refresh-httponly-cookie`
