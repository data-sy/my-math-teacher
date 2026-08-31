# 최초배포 런북 (M6 spec-01 §3-B step 5 — 앱 콜드스타트)

**성격:** 👤 사람 트리거 런북 — 앱면 최초 기동(공개 노출·시크릿 주입). 🤖 어시스턴트가 명령을 짜고 무해한 부분은 실행하되, **시크릿 주입·이미지 push·라이브 컷오버는 사람이 트리거/승인**.
**대상:** 갓 apply 된 EC2(`i-0eb170169ac70ee05`)에 MMT 앱 스택을 **처음** 세운다. 이후 정상 배포는 GH Actions→`switch-backend.sh`(blue↔green)가 소유.
**전제:** step 1(apply)·2(RDS 시드)·3(DNS)·4(인증서) 완료. 인증서 = `/etc/letsencrypt/live/www.my-math-teacher.com/{fullchain,privkey}.pem`(2026-10-08 만료).

---

## 왜 런북인가 (switch-backend.sh 로는 부족한 이유)

`deploy/switch-backend.sh` 는 **blue-green *전환*** 스크립트다 — 이미 떠 있는 `mmt-front`·네트워크·(선택)구 백엔드를 전제로, idle 색에 신버전을 올리고 `docker exec mmt-front nginx -s reload` 로 컷오버한다. **최초 콜드스타트엔 두 개의 닭-달걀이 있다:**

1. **front ↔ backend 해석 순환.** front nginx 의 `upstream mmt_backend { include active-backend.conf; }` 는 정적 `server mmt-backend-blue:8080;` 를 **로드 시점에 Docker DNS 로 해석**한다. `mmt-backend-blue` 가 없으면 nginx 가 `host not found in upstream` 으로 **기동 실패**. 그런데 `switch-backend.sh` 는 마지막에 front 를 `docker exec` 하므로 front 가 먼저 있어야 한다.
   → **해소:** 최초 1회만 **backend-blue 를 손으로 먼저 기동**(스크립트 §2 run 블록 미러) → 그 다음 front → 이후부터 `switch-backend.sh` 정상 동작.
2. **구 이미지·구 nginx.conf.** 로컬 `docker-compose.yml` 의 `mmt-backend:1.0.0`(M2 CTE 이전)·`mmt-front:1.0.0`(80-only nginx, cert 없음)은 **재사용 불가**. 백엔드는 현재코드(M2 CTE·M4 graceful) 이미지, front 는 새 443 nginx.conf(커밋 `119c870`)+same-origin api.js(`07254da`)로 **재빌드** 필요.

---

## 온박스 토폴로지 (이 런북이 세우는 것)

한 Docker 네트워크(`mmt-net`) 위에:

| 컨테이너 | 이미지 | 포트 | 비고 |
|---|---|---|---|
| `mmt-front` | web/ 재빌드(`mmt-front:m6`) | **80+443**(호스트 노출) | TLS 종단, SPA 서빙, blue-green 프록시. cert·certbot-webroot·active-backend.conf 마운트 |
| `mmt-backend-blue` | `mymathteacher/mmt-backend:<sha>`(현재코드) | 8080(내부만) | secure 프로파일, CTE=true, Neo4j 잠(dummy GDB) |
| `mmt-redis` | `redis` | 6379(내부만) | `--requirepass`, 백엔드 세션/캐시 |
| `mmt-ai` | `mymathteacher/mmt-ai:serving` | 8501(내부만) | TF Serving. **이름 `mmt-ai` 고정**(ProbabilityService.java:87 하드코딩) |

- **MySQL = 외부 RDS**(`mmt-db…rds.amazonaws.com:3306`, app-SG 한정) — 컨테이너 아님(D3).
- **Neo4j 미구동** — CTE-only(`MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true`), backend 에 dummy GDB env.
- 8080/6379/8501 은 **호스트 미노출**(네트워크 내부 DNS 로만). 외부 진입은 front 80/443 뿐(R2).

---

## 시크릿·env-file (`/home/ec2-user/mmt-backend.env`)

`switch-backend.sh` 와 hand-start 가 `--env-file` 로 읽는다. **출처 = 로컬 gitignored `docker-compose.yml` 의 `mmt-backend` environment 블록**(OAuth·JWT·Redis 비번이 평문으로 이미 존재). ⚠️ 이 파일은 **세션에 출력·커밋 금지** — 로컬 값을 그대로 박스 env-file 로만 옮긴다.

| 변수 | 값 출처 | 프로덕션 조정 |
|---|---|---|
| `RDS_HOST` | 신규 RDS 엔드포인트 | `mmt-db.c7qu444ug8bf.ap-northeast-2.rds.amazonaws.com` |
| `RDS_PORT`/`RDS_NAME` | — | `3306` / `mmt` |
| `RDS_USERNAME`/`RDS_PASSWORD` | 시드 런북·`terraform.tfvars` | `mmtadmin` / tfvars `db_password` (⚠️ 로컬 `docker-compose.yml` 의 MySQL 자격증명과 **다른 값이다** — 그대로 쓰지 말 것) |
| `REDIS_URL`/`REDIS_PORT`/`REDIS_PASSWORD` | compose | `mmt-redis` / `6379` / compose 값 재사용 or 신규(아래 redis `--requirepass` 와 일치시킬 것) |
| `GOOGLE/NAVER/KAKAO_CLIENT_ID·SECRET` | compose(로컬) | 그대로(같은 OAuth 앱, redirect-uri 는 `application-secure.yml` 이 이미 `https://www.my-math-teacher.com/...` 로 고정) |
| `JWT_SECRET` | compose(로컬) | 그대로 |
| `EC2_DOMAIN_NAME1`/`2` | 신규 | `www.my-math-teacher.com` / `my-math-teacher.com` (CORS origins; same-origin 이라 실질 영향 적음) |
| `GDB_*` | — | env-file 에 넣지 않아도 됨 — `switch-backend.sh`·hand-start 가 `-e GDB_URL=localhost … GDB_PASSWORD=dummy` 로 주입 |

> **닭-달걀 재확인:** OAuth 로그인이 진짜로 돌려면 각 provider 콘솔(Google/Naver/Kakao)에 **redirect-uri `https://www.my-math-teacher.com/login/oauth2/code/<provider>` 가 등록**돼 있어야 한다(👤 사람 확인). v1 프로드 도메인과 다르면 등록 추가 필요 → spec-02 핸드오프 항목.

---

## 최초 기동 순서

### Phase 0 — 박스 준비 (🤖 실행 가능, 시크릿 파일만 👤)

```bash
# (로컬) 접속: ssh -i ~/.ssh/mmt-ec2 ec2-user@54.116.29.102
# 0a. deploy/ 자산을 박스로 (repo 얕은 클론 or scp deploy/ + web/)
#     front 빌드엔 web/ 소스가, switch 엔 deploy/ 가 필요.
git clone --depth 1 <repo-url> ~/mmt   # 또는 scp -r deploy web ec2-user@EIP:~/
# 0b. 네트워크
docker network create mmt-net || true
# 0c. certbot webroot(갱신 경로)
sudo mkdir -p /var/www/certbot
# 0d. 👤 env-file 작성 — 로컬 docker-compose.yml 값 참조, 위 표대로 프로덕션 조정.
#     RDS_PASSWORD=tfvars db_password, RDS_USERNAME=mmtadmin, RDS_HOST=신규 RDS.
nano ~/mmt-backend.env        # chmod 600 ~/mmt-backend.env
```

### Phase 1 — 인프라 컨테이너 (🤖)

```bash
# redis (비번은 env-file 의 REDIS_PASSWORD 와 반드시 일치)
docker run -d --name mmt-redis --network mmt-net --restart unless-stopped \
  redis redis-server --requirepass "<REDIS_PASSWORD>"
# TF Serving — 이름 mmt-ai 고정
docker run -d --name mmt-ai --network mmt-net --restart unless-stopped \
  mymathteacher/mmt-ai:serving
docker ps  # redis·mmt-ai Up 확인
```

### Phase 2 — 백엔드 이미지 확보 (👤 트리거)

정상 경로 = GH Actions `Backend CI CD with EC2` → **Run workflow**(`skip_tests=true`) → `build-and-push` 가 `mymathteacher/mmt-backend:<github.sha>` 를 push.
⚠️ 같은 워크플로의 `deploy` 잡이 SSM 으로 `switch-backend.sh` 를 부르지만, **아직 front 부재라 무해하게 실패**(nginx -t 단계에서 blue 자기정리 후 exit 1 — 부수효과 0). 목적은 **이미지 push 뿐**. push 된 `<sha>` 를 기록.
- 사전 👤: 레포 변수 `COMPOSE_NET=mmt-net` 설정(미설정 시 GH Actions 기본 `ec2-user_default` 로 어긋남). Docker Hub `mymathteacher/mmt-backend` 가 private 면 박스 `docker login` 필요.

```bash
# (박스) 이미지 pull
docker pull mymathteacher/mmt-backend:<sha>
```

### Phase 3 — backend-blue 손으로 기동 (🤖, switch-backend.sh §2 run 블록 미러)

```bash
docker run -d --name mmt-backend-blue --network mmt-net --restart unless-stopped \
  --memory 350m \
  --env-file ~/mmt-backend.env \
  -e SPRING_PROFILES_ACTIVE=secure \
  -e MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true \
  -e GDB_URL=localhost -e GDB_PORT=7687 -e GDB_USERNAME=neo4j -e GDB_PASSWORD=dummy \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70" \
  mymathteacher/mmt-backend:<sha>

# 헬스(내부 네트워크에서) — 200 대기
docker run --rm --network mmt-net curlimages/curl:8.11.0 \
  -fsS --max-time 3 --retry 30 --retry-delay 5 \
  http://mmt-backend-blue:8080/api/v1/health
# 데이터경로 smoke — non-empty 기대(시드+CTE+Redis 왕복 실증)
docker run --rm --network mmt-net curlimages/curl:8.11.0 \
  -fsS --max-time 10 http://mmt-backend-blue:8080/api/v1/concepts/nodes/7925
```
RED(헬스/smoke 실패)면 `docker logs mmt-backend-blue` — 대개 RDS 접속(SG/자격)·Redis 비번 불일치·시드 미적재. front 올리기 전에 여기서 잡는다.

### Phase 4 — front 빌드 + 기동 (🤖 빌드, 443 노출은 라이브)

```bash
# active-backend.conf 는 이미 blue 기본값(deploy/active-backend.conf: server mmt-backend-blue:8080;)
cd ~/mmt/web && docker build -t mmt-front:m6 .
docker run -d --name mmt-front --network mmt-net --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  -v /var/www/certbot:/var/www/certbot \
  -v ~/mmt/deploy/active-backend.conf:/etc/nginx/active-backend.conf:ro \
  mmt-front:m6
docker exec mmt-front nginx -t   # 통과해야 함(blue 해석됨 + cert 존재)
```
> `active-backend.conf` 를 `:ro` 로 마운트하면 이후 `switch-backend.sh` 의 제자리 truncate(`>`)가 막힌다 — **rw 로 마운트**하거나, 최초엔 ro 로 두되 정상 배포 전환 시 rw 로 재마운트. 권장: 처음부터 rw(`:ro` 제거).

### Phase 5 — 검증 (§4, 사람 시각검증 동반)

```bash
# 외부에서(로컬)
curl -I https://www.my-math-teacher.com/                       # 200 + 유효 인증서
curl -sI http://www.my-math-teacher.com/ | grep -i location    # 301 → https
curl -s https://www.my-math-teacher.com/api/v1/concepts/nodes/7925 | head -c 200  # CTE 그래프 non-empty
dig +short www.my-math-teacher.com                             # 54.116.29.102
```
- 🤖 어시스턴트가 사이트를 브라우저로 띄우고 체크리스트 제시(개념 그래프 탐색=CTE·진단 결과=TF Serving 확률·OAuth 로그인 1종). 진단 플로우가 TF Serving(`mmt-ai`)까지 실제로 도는지 = D1(실서빙 유지)의 값어치 실증.
- ⚠️ concept 설명 **수식(LaTeX) 잔여 흠**은 시드 단계 기지(백로그 §7) — 렌더 깨짐 보이면 신규 회귀 아님, 그 백로그로.

---

## 갱신 (R5, 90일)

certbot standalone 은 최초 부트스트랩용. **갱신은 front 80 이 살아있는 webroot 경로**로:
```bash
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot --quiet \
  && docker exec mmt-front nginx -s reload
```
→ systemd timer/cron(주 1회)로 등록. front nginx.conf 의 `/.well-known/acme-challenge/ { root /var/www/certbot; }` 가 이 경로를 서빙(리다이렉트 예외)하므로 무중단 갱신.

## 롤백

- front/backend 개별: `docker stop/rm` 후 직전 이미지로 재기동. blue/green 이미 떠 있으면 `switch-backend.sh` flip-back(1분).
- TLS 실패: 80-only 임시로 서비스 유지(443 컨테이너만 내림).
- 전면: 인스턴스 유지(RDS·EIP destroy=링크 사망 — 금지).

## 이후 정상 배포 (이 런북 1회 후)

front·blue 가 상주하면 GH Actions **Run workflow** → OIDC → SSM → `runuser -l ec2-user … switch-backend.sh <sha>` 가 green 기동→헬스/smoke 게이트→`nginx -s reload` 컷오버→blue 드레인. 무중단(M4 §4 재현). 이 런북은 재실행 불요.

## 미해결·주의

- **securelocal include:** base `application.yml` 의 `spring.profiles.include: securelocal` 는 prod 에서도 활성화되나, `application-securelocal.yml` 이 gitignore 라 백엔드 이미지에 **없어** 무시됨(로컬값 유출 0). 이미지 빌드 컨텍스트에 securelocal 이 섞이지 않았는지 1회 확인.
- **REDIS_PASSWORD 일치:** redis `--requirepass` 와 env-file 값이 어긋나면 백엔드 부팅 시 인증 실패. 로테이션하려면 양쪽 동시.
- **active-backend.conf 마운트 rw:** 위 Phase 4 주석 — switch-backend.sh 의 제자리 truncate 를 위해 rw 필요.
- **COMPOSE_NET 레포 변수:** GH Actions 기본 `ec2-user_default` ≠ `mmt-net`. 정상 배포 전 레포 변수 설정 필수(안 하면 switch 가 딴 네트워크로 backend 기동 → front 가 못 봄).

---

## 실행 결과 (2026-07-11, 어시스턴트가 SSH 경유로 실행) — ✅ 라이브

**순서대로 완주:** env-file(로컬 gitignored `docker-compose.yml`·`tfvars` 에서 값 추출, 세션·커밋 미노출) → network `mmt-net` → redis(auth PONG) → mmt-ai(TF Serving) → backend-blue → front.

- ✅ **외부 검증 전 항목 통과:** `https://www.my-math-teacher.com` HTTP 200·TLS verify 0(OK)·HTTP→301·same-origin `/api/v1/concepts/nodes/7925` 실개념 반환(CTE+RDS시드+Redis)·SPA `<title>my math teacher</title>`·cert CN=www…(Let's Encrypt, ~2026-10-08). 백엔드 `Started in 15.5s`, health 200, smoke non-empty.
- ✅ **cert 자동갱신(R5):** systemd `certbot-renew.timer`(주간 Mon 03:30 + 지연) 등록, webroot dry-run "all simulated renewals succeeded" 실증.

**실제 편차(재현 시 주의):**
1. **백엔드 이미지 = `mmt2024/mmt-backend:47063986d7fb…`** — CI(`api-ci-cd`, skip_tests) build-and-push 산물. ⚠️ `DOCKERHUB_USERNAME`=**`mmt2024`**(개인 계정)이지 `mymathteacher`(org, 여긴 `1.0.0` 구태그만 public) 아님. 과거 sha 태그(`fe8064dc` 등)가 `mymathteacher/` 경로에서 "manifest unknown" 이었던 이유. CI deploy 잡은 박스에 `deploy/` 미배치라 SSM 무해 실패(부수효과 0).
2. **front 는 로컬 빌드로 우회** — 박스 `docker build`(web/Dockerfile, node:14 `npm install`)가 **vue3-markdown 최신본의 `./dist/style.css` export 제거**로 `vite build` 실패(`Missing "./dist/style.css" specifier`). lockfile 은 1.1.9 를 고정하나 `npm install` 이 무시. → **로컬(node20)에서 `npm run build` → `dist/`+`nginx.conf` 만 박스로 → 최소 `FROM nginx:1.21.4-alpine`(COPY dist+conf) 이미지 `mmt-front:m6`** 로 조립. *정식 수정: web/Dockerfile 을 `npm ci`(lockfile 준수)로, 또는 front CI 로 빌드.*
3. **네트워크 `mmt-net`(≠ repo var `COMPOSE_NET=ec2-user_default`)** — 최초는 손기동이라 무관하나, **정상 CI 무중단 배포(switch-backend.sh) 쓰기 전 레포 변수 `COMPOSE_NET=mmt-net` 로 맞춰야** green 이 같은 네트워크에 뜬다. + 박스에 `deploy/switch-backend.sh`·`active-backend.conf`(rw) 배치 필요.

**잔여(비차단):** step6 `run-log` 상시측정 · step7 AWS Budgets(R1·R6) · 진단(TF Serving) 플로우 사람 시각검증(로그인 경유) · OAuth 콘솔 등록([백로그 §8](../../backlog/production-deploy-live-resume-link.md)) · 위 편차 3(CI 정상배포 정합).
