# 백엔드 접점 시트 — 환경 사실 (설계 아님)

프론트가 소비할 백엔드는 **이미 존재하고 변경되지 않는다.** 이 문서는 그 서버의 접점 사실만 기록한다. 화면↔엔드포인트 매핑의 정본은 `docs/design/v2/00-flow-map.html` 의 "화면 ↔ 백엔드 계약 매핑" 표 — 여기엔 표에 없는 환경 사실만 보탠다.

## 서버 위치

- **dev**: `http://localhost:8080` — 기동은 사용자 담당(안 떠 있으면 사용자에게 요청).
- **prod**: same-origin — nginx 가 `/api/v1/`·`/oauth2/` 를 백엔드로 proxy 하고, 프론트 정적 파일은 SPA fallback(`try_files … /index.html`)으로 서빙. 프론트 빌드 산출물은 정적 dist 면 됨.
- **CORS(dev)**: `http://localhost:5173`·`http://localhost:8000` 허용(credentials 포함) — **dev 서버 포트는 5173 권장.** 다른 포트는 CORS 에 막힌다.

## 인증 역학 (기존 백엔드 확정 사실)

- **OAuth 진입** = `GET /oauth2/authorization/{google|naver|kakao}` — XHR 이 아니라 **페이지 이동**(앵커/location 이동).
- **성공 콜백** = 백엔드가 프론트 `/login?token=<accessToken>` 으로 리다이렉트 + refreshToken 은 **HttpOnly Set-Cookie**. 프론트는 쿼리의 토큰을 파싱해 보관한다(보관 방식은 설계 자유이나, 콜백이 `?token=` 쿼리로 온다는 사실은 고정).
- ⚠️ **콜백 복귀 URL 은 백엔드에 운영 도메인으로 하드코딩** — 로컬에서 실제 OAuth 를 완주하려면 백엔드 수정이 필요(사용자 담당). 로컬 개발 중 인증 상태는 **accessToken 을 수동 주입**해 시뮬레이션한다(사용자에게 토큰 요청).
- **API 호출 인증** = `Authorization: Bearer <accessToken>` 헤더.
- **토큰 갱신** = `POST /api/v1/auth/reissue` body `{grantType:"Bearer", accessToken:<만료된 토큰>}` + 쿠키의 refreshToken(**withCredentials 필수**) → 새 accessToken 응답, refreshToken 은 로테이트되어 다시 Set-Cookie.
- **로그아웃** = `DELETE /api/v1/auth/authentication`.

## 기타

- **신규 진단 경로**(`/api/v1/diagnosis/*`, `/api/v1/learning-queues/*`)는 백엔드 피처 플래그로 게이트됨 — 로컬에서 404/비활성이면 사용자에게 플래그 활성화를 요청.
- **AI 시급도 계산기(TF Serving)가 로컬에 없으면** `preview` 가 시급도 등급 결측(fail-soft)으로 응답할 수 있음 — 기획물 ④ 의 fail-soft 상태로 처리하면 된다.
- 에러 응답(400/401/403/429)은 학생 친화 메시지 바디를 동반한다 — 정확한 JSON shape 는 실호출로 확인.
