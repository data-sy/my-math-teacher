# OAuth 콘솔 등록 + 시크릿 로테이션 런북 (M6 §8 + 🔴 공개 유출 대응)

**성격:** 👤 **사람 주도** 런북 — 콘솔·AWS 접근이 필요한 시크릿 로테이션. 🤖 어시스턴트는 값 무관한 부분(추적 해제·gitignore·이 런북)을 이미 처리했고, 사람이 콘솔/AWS에서 값을 재발급·주입한다.
**대상:** ① M6 §8 OAuth redirect-uri 프로덕션 콘솔 등록(소셜 로그인 활성화) ② 🔴 **공개 리포에 유출된 프로덕션 시크릿 전면 로테이션**.
**우선순위:** **높음(유출은 라이브 서비스)** — 다음 세션 최우선.
**연관:** 백로그 §8·§9(`../../backlog/production-deploy-live-resume-link.md`) · 최초배포 런북(`first-deploy-runbook.md`, 시크릿 주입·재배포 경로).

---

## 0. 🔴 사고 요약 (2026-07-11 발견)

> 🟡 **정정 (2026-07-11, 로테이션 착수 전 추가검증).** 아래 최초 진단은 **과대평가로 판명**. 3중 검증:
> - ① 추적되던 `application-secure.yml`의 시크릿 키는 전부 `${ENV}` **플레이스홀더**(리터럴 값 아님).
> - ② 라이브 시크릿 6종 해시 vs 히스토리 리터럴 3종 해시 = **교집합 0**.
> - ③ 전체 히스토리 피카axe(`git log --all -S`): 라이브 값 6종 모두 **0커밋**. `docker-compose.yml`도 커밋 이력 0.
>
> → **현재 라이브 시크릿은 공개 히스토리에 유출된 적 없음.** 아래 JWT 위조·계정탈취 위험은 **현재 값 기준 성립 안 함**. 공개된 건 초기 커밋의 옛 리터럴 3종(예: 구 Naver client-secret)뿐이며 **현재 라이브 값과 불일치 → 이미 대체됨**.
>
> **결정(2026-07-11):** 그럼에도 **방어심화로 전면 로테이션 진행**. 긴급도 = "라이브 사고" → "위생"으로 하향.
> **메커니즘 정정:** 운영 시크릿 소스는 `application-secure.yml`(플레이스홀더)이 **아니라** 박스 `~/mmt-backend.env` + 로컬 `docker-compose.yml` env 블록. §2·§4의 "`application-secure.yml` 갱신"은 이 둘 갱신으로 대체해 읽을 것 — 로테이션 = 두 곳 값 갱신 후 백엔드 재기동.

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

## 1-B. 실행 진행상태 (2026-07-11 세션) — resume 정본

**방침:** 라이브 secret은 공개 히스토리에 없음(§0-A 3중 검증) → **긴급 아님, 방어심화(위생)로 전면 로테이션**.

**운영 메커니즘(중요):** 라이브 앱은 `application-secure.yml`(=`${ENV}` 플레이스홀더)이 아니라 **박스 `~/mmt-backend.env`** 를 읽는다(`docker run --env-file`, `SPRING_PROFILES_ACTIVE=secure`). 로테이션 = **박스 env-file + 로컬 `docker-compose.yml`(compose env 블록=source-of-truth) 양쪽 갱신 → 백엔드 컨테이너 재생성(⚠️`restart` 아님 — `docker rm -f`+`docker run --env-file`) → 검증.**

| 단위 | 상태 | 메모 |
|---|---|---|
| **Google** (2-1) | ✅ **완료 2026-07-11** | **재발급 아니라 새 계정+프로젝트+클라이언트** 신규. env-file/compose 새 client-id·secret 반영 → 재생성 → 헬스 200 → 브라우저 로그인 성립. **옛 클라이언트 삭제 미완(비긴급).** |
| **Naver** (2-1) | ✅ **완료 2026-07-11** | 콘솔 Client Secret 재발급(ID 불변). 박스 env-file+로컬 compose 갱신(해시 `658aaa13` 동기 확인, 옛값 `10310f0a` 불일치) → 재생성 → 헬스 OK → 브라우저 Naver 로그인 성립. ⚠️Naver 재발급은 옛 secret 즉시 무효라 재발급~재생성 창 최소화 원칙 확인됨. |
| **Kakao** (2-1) | ✅ **완료 2026-07-11** | 보안→Client Secret 코드 재발급+활성화 ON(OIDC는 미사용·불건드림). 박스+로컬 갱신(해시 `0504e4c7` 동기, 옛값 `8bafdcbdba12` 불일치) → 재생성 → 헬스 OK → 브라우저 Kakao 로그인 성립. |
| **JWT** (2-2) | ⬜ 대기 | 박스 `openssl rand -base64 64`. 재생성 시 전원 재로그인 1회 |
| **Redis** (2-4) | ⬜ 대기 | `requirepass`+env-file **동시** 변경 |
| **RDS** (2-3) | ⬜ 대기 | AWS 비번 변경+env-file. 다운타임 여지(가장 조심) |

**재생성 명령(이번에 검증됨):**
```bash
IMG=$(docker inspect mmt-backend-blue --format '{{.Config.Image}}')
docker rm -f mmt-backend-blue
docker run -d --name mmt-backend-blue --network mmt-net --restart unless-stopped \
  --memory 350m --env-file ~/mmt-backend.env \
  -e SPRING_PROFILES_ACTIVE=secure -e MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true \
  -e GDB_URL=localhost -e GDB_PORT=7687 -e GDB_USERNAME=neo4j -e GDB_PASSWORD=dummy \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70" "$IMG"
# 헬스(부팅 ~17s; curl connrefused 재시도엔 --retry-connrefused 필요)
docker run --rm --network mmt-net curlimages/curl:8.11.0 -fsS --max-time 5 \
  http://mmt-backend-blue:8080/api/v1/health
```
- **SSH:** `ssh -i ~/.ssh/mmt-ec2 ec2-user@$(dig +short www.my-math-teacher.com | tail -1)`
- **env-file 값 검증은 해시로만:** `grep '^KEY=' ~/mmt-backend.env | cut -d= -f2- | sha256sum | cut -c1-12` (로컬 compose와 대조해 동기화·오타 확인). client-id는 비밀 아님(콘솔 대조 가능).
- ⚠️ **권한:** 프로덕션 호스트 SSH **재배포**(`docker rm -f`+run)는 auto-mode 분류기가 차단 → **사람이 박스에서 직접 실행**하거나 명시 승인 필요. 읽기 전용 SSH 진단(ps·logs·hash)은 통과.

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
