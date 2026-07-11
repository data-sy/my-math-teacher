# OAuth 콘솔 등록 + 시크릿 로테이션 런북 (M6 §8 + 🔴 공개 유출 대응)

**성격:** 👤 **사람 주도** 런북 — 콘솔·AWS 접근이 필요한 시크릿 로테이션. 🤖 어시스턴트는 값 무관한 부분(추적 해제·gitignore·이 런북)을 이미 처리했고, 사람이 콘솔/AWS에서 값을 재발급·주입한다.
**대상:** ① M6 §8 OAuth redirect-uri 프로덕션 콘솔 등록(소셜 로그인 활성화) ② 🔴 **공개 리포에 유출된 프로덕션 시크릿 전면 로테이션**.
**우선순위:** **높음(유출은 라이브 서비스)** — 다음 세션 최우선.
**연관:** 백로그 §8·§9(`../../backlog/production-deploy-live-resume-link.md`) · 최초배포 런북(`first-deploy-runbook.md`, 시크릿 주입·재배포 경로).

---

## 0. 🔴 사고 요약 (2026-07-11 발견)

- **유출물:** `api/src/main/resources/application-secure.yml`(프로덕션 프로파일)이 **PUBLIC 리포**(`github.com/data-sy/my-math-teacher`)에 **커밋**됨. `main` 포함, **최초 커밋부터 19개 커밋**에 걸쳐 히스토리에 존재.
- **포함 시크릿:** OAuth **client-secret 3**(Google·Naver·Kakao) · **JWT 시크릿** · **DB/Redis 비밀번호**.
- **원인:** `.gitignore`가 `application-securelocal.yml`(로컬)만 덮고 **프로덕션 `application-secure.yml`을 누락**.
- **영향:** JWT 시크릿 유출 = **JWT 위조로 임의 사용자 로그인·계정 탈취 가능**. 라이브 서비스가 이 값을 사용 중.
- **원칙:** 공개 히스토리는 클론·포크·시크릿 스캐너에 노출됐다고 가정 → **유일한 실질 해결 = 전부 로테이션**(파일/히스토리 삭제로는 안 풀림).

---

## 1. ✅ 이미 처리됨 (🤖 이 세션, 값 무관)

- `.gitignore`에 `api/src/main/resources/application-secure.yml` 추가.
- `git rm --cached`로 **추적 해제**(디스크 파일은 유지, 이후 커밋에 안 들어감). — 커밋: 이 런북과 동반.
- ⚠️ 이건 **"앞으로 안 올라감"만 보장.** 이미 공개된 값은 여전히 히스토리에 있음 → §2 로테이션 필수.

---

## 2. 👤 시크릿 로테이션 (다음 세션 — 콘솔/AWS 접근)

각 항목: **새 값 발급 → 박스의 비커밋 `application-secure.yml`(또는 env-file) 갱신 → 재배포(§4) → 검증(§5).**

### 2-1. OAuth client-secret 재발급 (§8 redirect-uri 등록과 같은 콘솔에서 동시에)

| Provider | 콘솔 | redirect-uri 등록 위치 | secret 재발급 |
|---|---|---|---|
| **Google** | Google Cloud Console → API·서비스 → 사용자 인증 정보 → 해당 OAuth 2.0 클라이언트 | "승인된 리디렉션 URI"에 §3 URI | "클라이언트 보안 비밀 추가" 후 **기존 secret 삭제**(로테이션 지원) |
| **Naver** | Naver Developers → 내 애플리케이션 → 해당 앱 → API 설정 | "Callback URL"에 §3 URI | Client Secret **재발급** |
| **Kakao** | Kakao Developers → 내 애플리케이션 → 해당 앱 → 카카오 로그인 | "Redirect URI"에 §3 URI | 보안 → Client Secret **코드 재발급** |

> **참고:** v1 프로드 도메인이 `www.my-math-teacher.com`으로 동일하면 redirect-uri는 **이미 등록돼 있을 수 있음 → 확인만**. 단 **client-secret은 유출됐으니 재발급은 무조건.**

### 2-2. JWT 시크릿

- 박스에서 새 값 생성(세션/로그에 남기지 말 것): `openssl rand -base64 64`.
- `application-secure.yml`의 jwt secret 갱신.
- ⚠️ **부작용:** 기존 발급 토큰 전부 무효화 → 모든 사용자 재로그인. 런치 전·소규모라 수용.

### 2-3. RDS(DB) 비밀번호

- RDS 콘솔(또는 `aws rds modify-db-instance --master-user-password`)로 마스터/앱 유저 비번 변경.
- `application-secure.yml`의 DataSource 비번 갱신. (앱-SG 한정이라 외부 직결은 막혀 있으나 노출값이므로 교체.)

### 2-4. Redis 비밀번호

- 박스 Redis `--requirepass` 새 값 + `application-secure.yml` 갱신.

---

## 3. redirect-uri 값 (코드 확인 — `application-secure.yml` registration)

Spring 콜백 패턴 `/login/oauth2/code/{provider}`:

```
https://www.my-math-teacher.com/login/oauth2/code/google
https://www.my-math-teacher.com/login/oauth2/code/naver
https://www.my-math-teacher.com/login/oauth2/code/kakao
```

로그인 개시 경로(참고): `/oauth2/authorization/{google|naver|kakao}`.

---

## 4. 👤 재배포 (시크릿 반영)

- 시크릿 주입 경로·이미지·컷오버는 **최초배포 런북(`first-deploy-runbook.md`)** 절차 재사용. 시크릿은 **박스의 비커밋 파일/env-file**로만 주입(HCL·state·커밋에 평문 금지).
- 정상 배포는 `switch-backend.sh`(blue↔green) — 단 §9 "CI 정합"(COMPOSE_NET·스크립트 배치·IMAGE_REPO) 선행 필요. 로테이션만이면 백엔드 재기동으로 충분.

---

## 5. 👤 검증 (로테이션 후 — §9 진단 검증과 통합)

1. **소셜 로그인 1회** — Google·Naver·Kakao 각 버튼 → 콜백 성공(redirect_uri mismatch 없음)·신규 로그인 성립.
2. **JWT 재로그인** — 기존 토큰 무효·새 로그인 정상.
3. **TF Serving 진단 end-to-end**(§9 항목) — 로그인 상태로 진단 1회 → `mmt-ai:8501` 왕복·확률 결과 확인.

---

## 6. 히스토리 스크럽 (선택 — 이번 세션 유보)

- 사용자 결정(2026-07-11): **로테이션 우선, 공개 히스토리 force-push는 안 함.** 로테이션하면 유출값이 무의미해지고, 공개 히스토리 재작성·포크된 사본엔 무효.
- 필요 시 방어심화로 BFG/`git-filter-repo` + `main` force-push 재검토(로테이션 **선행** 전제).

---

## 7. 후속 (governance — 승인 후)

- **루트/`api/CLAUDE.md` 보안절**이 `application-securelocal.yml`만 gitignored로 기술 → **`application-secure.yml`도 gitignore 대상**임을 명시하도록 갱신 제안(거버넌스 문서라 자동수정 금지, 승인 후).
- 재발 방지: 신규 시크릿-보유 프로파일 파일은 **`*-secure*`·`*-securelocal*` 패턴**으로 일괄 ignore 검토.

---

## 교훈

gitignore가 **로컬 변형(`*-securelocal`)만** 덮고 **프로덕션 변형(`*-secure`)을 누락**하는 게 이 사고의 뿌리. 시크릿 파일은 변형별로 전수 확인. 공개 리포 유출은 삭제가 아니라 **로테이션**이 해결. ([[feedback_public_repo_secret_leak_rotate]])
