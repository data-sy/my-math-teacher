# Spec 01: 무중단 배포(Zero-Downtime Deployment) 기반 구축

**상위 마일스톤:** Milestone 4 (배포 무중단화 — 신규, roadmap 등재 예정)
**대상 Phase:** Phase 1 (기반 구축 — 단일 EC2 blue-green 토대)
**선행 spec:** 없음
**후속:** 자동 롤백·정교한 프로브·의존성 게이팅 등은 **Out of scope** 로 분리 (§6)

> ⚠️ **상태: 설계 제안서(spec) — 구현 미착수.** 본 문서는 현재 상태 분석과 타깃 아키텍처를
> 제시하고, 결정이 갈리는 지점(§7 결정 대기)에 트레이드오프와 추천을 단다. 구현은 §7 사인오프
> 이후 별도 Task 로 진행한다. 본 spec 작성 턴에서는 코드/설정을 변경하지 않는다.

---

## 0. 범위

단일 EC2 위에서 Spring Boot 백엔드를 재배포할 때 발생하는 다운타임(502/요청 유실)을
제거하는 **가장 기본적이고 베스트 프랙티스에 맞는 토대**를 깐다. 쿠버네티스·ECS·ALB 같은
큰 전환 없이, 기존 nginx 리버스 프록시 + 앱 컨테이너 2개(blue/green) 구조로 "새 컨테이너
health 통과 후 upstream 전환 → 기존 컨테이너 정리"를 달성한다.

대상은 **백엔드 컨테이너 한정**이다. 프론트(정적 SPA) 배포 무중단, DB 무중단 마이그레이션,
자동 롤백 등은 §6 Out of scope.

EC2 가 아직 없으므로 **AWS 프리티어 기준 EC2 프로비저닝(§9)도 본 spec 에 포함**한다 — blue-green 의
RAM 전제를 프리티어 1 GiB 에 맞추는 것이 핵심.

---

## 1. 현재 상태 분석 (실제 파일 기준)

> 본 절의 모든 단정은 2026-06-15 기준 레포 실파일을 읽어 확인한 것이다. 사용자 사전 설명과
> 다른 점은 ⚠️ 로 표시한다.

### 1.1 배포 파이프라인 — `.github/workflows/api-ci-cd-with-ec2.yml`

```
test → build-and-push → deploy
```

- ⚠️ **레지스트리는 ECR 이 아니라 Docker Hub** (`docker/login-action`, `secrets.DOCKERHUB_USERNAME`,
  `docker/build-push-action`). ECR 전제는 사실과 다름.
- 이미지 태그가 **`mmt-backend:1.0.0` 으로 고정**(가변 태그). 매 빌드가 같은 태그를 덮어씀
  → 구버전/신버전을 태그로 구분할 수 없음 = blue-green/롤백의 직접적 걸림돌.
- `platforms: linux/amd64,linux/arm64` 멀티아치 빌드.
- 트리거는 `workflow_dispatch` 만 (포트폴리오 컨텍스트로 자동 push 트리거 제거됨). 무중단 검증은
  수동 실행으로 재현 가능.

deploy job 의 EC2 SSH 스크립트(`appleboy/ssh-action`)가 다운타임의 핵심:

```bash
docker rm -f ec2-user-mmt-backend-1          # (A) 유일한 백엔드 컨테이너를 먼저 죽임
docker rmi <user>/mmt-backend:1.0.0          # (B) 이미지 강제 삭제 → 재pull 강제
docker-compose up -d mmt-backend             # (C) 새 컨테이너 기동 (pull + JVM/Spring 부팅)
docker system prune -f
```

### 1.2 다운타임이 발생하는 지점

| 구간 | 원인 | 영향 |
|---|---|---|
| (A) 직후 | 백엔드 컨테이너가 사라짐. nginx `proxy_pass http://mmt-backend:8080` 의 upstream 부재 | 연결 거부 → **502** |
| (B)→(C) | 이미지 재pull (네트워크 시간) | 502 지속 |
| (C) 중 | JVM 기동 + Spring 컨텍스트 로딩(Hibernate, Neo4j Reactive, Security, OAuth2 등) | 포트가 열리고 앱이 요청을 받기 전까지 502/연결 거부 |

**다운타임 총량 ≈ 컨테이너 teardown + 이미지 pull + JVM·Spring 부팅 시간** (수십 초 규모로 추정,
§5 에서 실측). 기존 컨테이너를 **먼저 내리고** 새것을 올리므로 겹침 구간이 0 = 구조적으로
무중단 불가능.

### 1.3 리버스 프록시 — `web/nginx.conf` (프론트 이미지에 내장)

- ⚠️ 별도 nginx 컨테이너가 아니라 **프론트 이미지(`web/Dockerfile`)가 nginx**. 정적 SPA 서빙 +
  API 프록시를 겸한다. `docker-compose.yml` 의 `mmt-front` 가 이 이미지.
- API 프록시가 **단일 upstream 하드코딩**, `upstream` 블록·헬스체크·재시도 없음:
  ```nginx
  location /api/v1/ { proxy_pass http://mmt-backend:8080; }     # 로컬
  location /oauth2/ { proxy_pass http://ec2-user-mmt-backend-1:8080; }   # ⚠️ 대상 불일치
  location /login/oauth2 { proxy_pass http://ec2-user-mmt-backend-1:8080; }
  ```
  → `/api/v1/` 는 `mmt-backend`, OAuth 경로는 `ec2-user-mmt-backend-1` 로 **타깃이 갈려 있음**.
  무중단 전환 시 세 location 모두 동일 upstream 을 바라보도록 정리 필요.
- nginx 설정이 **이미지 빌드 시 COPY 로 baked** (`web/Dockerfile`) → 런타임에 upstream 을
  바꾸려면 설정을 볼륨 마운트로 빼거나 별도 프록시가 필요.

### 1.4 컨테이너 구성 — `docker-compose.yml`

> ⚠️ 이 파일은 `.gitignore` 대상(자격증명 포함)이라 레포에 없다. 본 spec 은 변경점만 기술하며
> **시크릿은 일절 담지 않는다.** 실제 수정은 EC2/로컬의 비커밋 파일에서 수행한다.

- `mmt-backend` 에 `container_name: ec2-user-mmt-backend-1` **고정** → compose 로 같은 서비스를
  2개(blue/green) 동시 기동 불가. 무중단의 직접적 걸림돌.
- 백엔드는 `depends_on: mmt-mysql (service_healthy)` 만 게이팅. 자기 자신의 readiness 신호는 없음.

### 1.5 헬스 신호 — Spring Boot

- ⚠️ **Actuator 미도입** (`api/build.gradle:41` "Actuator 미도입 상태이므로 MeterRegistry 는
  ObservabilityConfig 에서 수동 등록"). `src/main/resources/` 에 `management.*` 설정 전무.
- 즉 **`/actuator/health` 같은 readiness 엔드포인트가 없다.** "health 통과 후 upstream 전환"을
  하려면 최소한의 헬스 신호를 새로 만들어야 한다(§4 D2).

### 1.6 요약 — 무중단을 막는 5가지 구조적 원인

1. 배포가 기존 컨테이너를 **먼저 제거**(겹침 0)
2. 이미지 태그 **고정**(blue/green·롤백용 버전 구분 불가)
3. nginx upstream **하드코딩**(런타임 전환 수단 없음)
4. `container_name` **고정**(2개 병행 불가)
5. **헬스 신호 부재**(전환 게이트로 쓸 신호 없음)

---

## 2. 타깃 아키텍처

### 2.1 한 줄 요약

기존 프론트 nginx 를 **전환 지점**으로 재사용한다. 백엔드를 blue/green 2개 슬롯으로 운용하여,
신버전을 idle 슬롯에 올리고 → 헬스 통과 확인 → nginx upstream 을 신버전으로 graceful reload →
구버전 정리. nginx **graceful reload(`nginx -s reload`)** 가 무중단의 핵심 프리미티브
(기존 연결은 유지, 신규 연결만 새 설정으로 라우팅).

```
                ┌─────────────── EC2 (단일 호스트) ───────────────┐
   사용자 ──▶ nginx(=mmt-front) ──proxy_pass──▶ upstream mmt_backend
                │  (정적 SPA + API 프록시)            │
                │  include active-backend.conf  ◀── 배포 스크립트가 이 파일만 교체 + reload
                └───────────────┬──────────────────┘
                          ┌─────┴─────┐
                    mmt-backend-blue   mmt-backend-green
                      (구버전)            (신버전, health 통과 후 활성)
```

### 2.2 배포 시퀀스 (목표)

1. CI: 이미지를 **immutable 태그(git sha)** 로 빌드·push.
2. EC2: 현재 비활성(idle) 색으로 신버전 컨테이너 기동 (구버전은 계속 트래픽 처리 中).
3. 신버전 헬스 엔드포인트를 폴링 → 200 통과까지 대기(타임아웃·실패 시 중단, 구버전 그대로 유지).
4. nginx upstream include 파일을 신버전 색으로 재작성 → `nginx -s reload` (무중단 전환).
5. **구버전 드레인** 후 stop+rm. 다음 배포의 idle 색이 됨.
   - ⚠️ "짧은 대기"만으로는 부족 — reload 시점에 구버전에 잡혀 있던 in-flight 요청이 대기보다 오래
     살면 stop 순간 502. **백엔드에 `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase`
     설정**으로 진행 중 요청을 흘려보낸 뒤 종료해야 진짜 무중단. 드레인 타임아웃은 이 값 이상으로.
   - stop 은 `docker stop` 의 SIGTERM → graceful shutdown 트리거 → grace period 후 제거.
   - ⚠️ **`docker stop` 기본 grace = 10초.** `timeout-per-shutdown-phase` 를 30초로 잡아도 docker 가
     10초에 SIGKILL 하면 graceful 이 중간에 잘린다 → **`docker stop -t <N ≥ phase 타임아웃>`** 로
     맞춰야 graceful 이 완주. switch-backend.sh 구버전 정리 단계에 명시.

→ 어느 시점에도 **트래픽을 받는 정상 컨테이너가 최소 1개** 존재 = 다운타임 0.

### 2.3 왜 단일 EC2 에 이 구성이 맞는가

- 새 인프라(K8s/ALB/ECS) 도입 0. **이미 있는 nginx** 를 전환 지점으로 재사용 → "과설계 금지"
  요구에 부합.
- nginx graceful reload 는 단일 호스트에서 무중단 전환을 보장하는 표준·검증된 방법.
- JVM/Spring 부팅이 수십 초로 길다 → "먼저 띄우고 health 통과 후 전환"이 부팅 지연을 사용자에게서
  완전히 격리. blue-green 의 이점이 가장 큰 워크로드 유형.

---

## 3. 변경할 파일과 구체적 셋업 단계

> 모든 단계는 §7 결정 사인오프 후 착수. 아래는 **추천안(blue-green + 프론트 nginx 재사용 +
> 커스텀 헬스)** 기준 셋업이다.

### 3.1 백엔드 헬스 엔드포인트 (신규)

- `api/src/main/java/com/mmt/api/controller/HealthController.java` (신규)
  - `GET /api/v1/health` → 컨텍스트 기동 후 `200 OK` 반환(바디 최소). Spring Security 화이트리스트에
    추가(인증 없이 접근 가능하도록). 전환 게이트 + 부하 테스트 타깃으로 사용.
  - 의존성 검사(DB/Redis ping)는 **하지 않는다**(§6 Out of scope) — "앱이 요청을 받을 준비가
    됐다" 수준만 표현.

### 3.2 nginx 설정 — 전환 가능 구조로 (`web/nginx.conf` + 신규 fragment)

- `web/nginx.conf` 수정:
  ```nginx
  upstream mmt_backend { include /etc/nginx/active-backend.conf; }   # ⚠️ conf.d 밖 — 아래 근거
  location /api/v1/      { proxy_pass http://mmt_backend; }
  location /oauth2/      { proxy_pass http://mmt_backend; }   # 타깃 통일
  location /login/oauth2 { proxy_pass http://mmt_backend; }   # 타깃 통일
  ```
- `deploy/active-backend.conf` (신규, 호스트에 위치 — 교체 대상 fragment):
  ```nginx
  server mmt-backend-blue:8080;   # 최초 활성 색(blue). 배포 스크립트가 blue/green 으로 재작성
  ```
- 이 fragment 를 프론트 컨테이너에 **볼륨 마운트**(`./deploy/active-backend.conf` →
  `/etc/nginx/active-backend.conf`)하여 런타임 교체 + reload 가능하게 함.
- ⚠️ **마운트 경로는 반드시 `conf.d` 밖.** `web/Dockerfile` 이 `nginx.conf` 를
  `/etc/nginx/conf.d/default.conf` 로 COPY 하고, 스톡 nginx 메인 설정이 `include /etc/nginx/conf.d/*.conf`
  로 conf.d 의 **모든** `.conf` 를 http 컨텍스트에 로드한다. fragment(`server …:8080;`)를 conf.d 안에
  두면 `upstream{}` 안의 include 와 **별개로** http 컨텍스트에서 한 번 더 로드돼 standalone server
  디렉티브로 파싱 → **`nginx -t` 가 깨진다**. 그래서 conf.d 밖(`/etc/nginx/active-backend.conf`)에
  마운트해 `upstream{}` 안에서만 include 되게 한다. (구현 시 `nginx:1.21.4-alpine` 으로 `nginx -t`
  실측 확인 — 이 경로 함정은 실제 기동해봐야 드러나는 종류라 형태 검증 단계에서 정정.)

### 3.3 docker-compose.yml (gitignored — 변경점 기술만, 시크릿 미포함)

- `mmt-front`: 위 fragment 볼륨 마운트 추가.
- `mmt-backend`: compose 서비스에서 제거하고 **blue/green 은 배포 스크립트 `docker run` 으로 운용**
  (§7 D4 확정). 기존 `container_name: ec2-user-mmt-backend-1` 고정도 함께 제거.
- 프리티어 배치(§9)에 맞춰:
  - `RDS_HOST` = RDS 엔드포인트로 설정(현행 주석 처리된 RDS env 활성화), `mmt-mysql` 컨테이너 미사용.
  - `mmt-neo4j` 컨테이너 미구동 + 백엔드에 `mmt.migration.use-mysql-cte-for-graph=true` 주입.
    - ⚠️ flag ON 이어도 `conceptRepository`(ReactiveNeo4jRepository) **빈은 여전히 주입**되어 Spring
      이 Neo4j Driver 빈을 만든다 → `application-secure.yml` 의 `${GDB_PORT}` 등 플레이스홀더가
      해소돼야 기동. **`GDB_URL`/`GDB_PORT`/`GDB_USERNAME`/`GDB_PASSWORD` 에 더미 값을 그대로 주입**
      (예: `localhost`/`7687`/`neo4j`/`dummy`). 드라이버는 lazy + flag ON 이라 쿼리가 안 나가므로
      더미 자격은 실제로 쓰이지 않는다 = **코드 0 변경.** (상세·폴백은 §9.3·§10-R1)
  - `mmt-redis` 는 로컬 컨테이너 유지.
  - 백엔드 컨테이너에 `mem_limit`/JVM 힙 상한 적용(§9.4).
- ⚠️ 시크릿(RDS 비밀번호 등)은 비커밋 파일/배포 환경변수로만 — 본 spec·커밋에 미포함.

### 3.4 배포 스크립트 (신규)

- `deploy/switch-backend.sh` (신규): blue-green 로직을 캡슐화 — 현재 활성 색 감지 → idle 색에
  신버전 기동(**`docker run --network <compose_default_net>`** 으로 nginx 가 이름 해석 가능하게, R3)
  → 헬스 폴링(타임아웃·재시도 여유, R6) → fragment 재작성 → `docker exec mmt-front nginx -s reload`
  → 구버전 **`docker stop -t <N ≥ phase 타임아웃>`** 으로 graceful 드레인 후 rm(§2.2-5, 나).
  YAML 인라인 스크립트를 얇게 유지하고 로직을 한 곳에 모은다.

### 3.5 워크플로 — `.github/workflows/api-ci-cd-with-ec2.yml`

- `build-and-push`: 태그를 **`${{ github.sha }}`** (immutable)로 변경(필요 시 `latest` 병행 push).
  롤백은 이전 sha 로 재지정.
- `deploy`: 기존 `docker rm -f → rmi → compose up → prune` 제거. 대신 **SSM Run Command**
  (SSH 아님 — ADR 0008)로 `deploy/switch-backend.sh <new-sha>` 를 호출. OIDC 로 단기 AWS
  자격을 얻어 `aws ssm send-command`(태그 `Project=mmt` 타겟) → `runuser -l ec2-user` 로
  스크립트 실행 → 비동기라 상태 폴링·stdout 회수로 성패 판정. `docker rmi`/`prune` 의 무차별
  삭제는 제거하거나 전환 완료 **이후** 단계로 이동.

### 3.6 변경 파일 요약

| 파일 | 종류 | 변경 |
|---|---|---|
| `api/.../controller/HealthController.java` | 신규 | `/api/v1/health` 200 |
| `api/.../config` (Security 화이트리스트) | 수정 | health 경로 permitAll |
| `web/nginx.conf` | 수정 | upstream 블록 + include + 타깃 통일 |
| `deploy/active-backend.conf` | 신규 | 교체용 upstream fragment |
| `deploy/switch-backend.sh` | 신규 | blue-green 전환 로직 |
| `docker-compose.yml` (gitignored) | 수정 | 볼륨 마운트, container_name 제거, blue/green |
| `.github/workflows/api-ci-cd-with-ec2.yml` | 수정 | immutable 태그 + 전환 스크립트 호출 |

---

## 4. 검증 방법 — "유실률 N% → 0%" 측정

부하 도구로 배포 **도중** 트래픽을 계속 쏘면서 non-2xx 개수를 세어 before/after 를 비교한다.

### 4.1 측정 타깃

⚠️ **`/health` 단독 측정의 거짓 확신 주의.** sub-ms `/health` ping 만으로 "유실률 0%"를 재면, 전환
직후 구버전에 남아 있던 **in-flight 요청이 드레인보다 오래 살아 stop 시 502** 나는 경계를 영영 못
드러낸다 — 테스트는 통과하지만 실 트래픽 유실은 0%가 아닐 수 있다. 따라서:

- **주 타깃**: 실제 처리 시간이 있는 **대표 GET 엔드포인트**(예: `/api/v1/concepts/...` 그래프 조회 —
  DB 왕복이 있어 in-flight 윈도우가 드러남). idempotent·읽기 전용으로 선택.
  - ⚠️ **반드시 `permitAll`(인증 불요) 엔드포인트여야 함.** 인증 필요 엔드포인트를 고르면 부하 테스트가
    배포와 무관한 401/403 을 뱉어 `http_req_failed: rate==0` 이 깨지고, 토큰을 얻으려면 OAuth→HTTPS
    (R2)라 **HTTP 부하로는 토큰을 못 얻는 교착**이 된다. ✓ 검증: `/api/v1/concepts/**`·
    `/api/v1/chapters/**` 는 `SecurityConfig.java:80` 에서 이미 `permitAll` + DB 왕복 有 → 적합.
- **보조**: `/health`(§3.1)는 기동/전환 게이트 폴링용으로만.
- 성공 기준 "0% 유실"은 **§2.2-5 의 graceful shutdown + 드레인이 적용된 상태**에서만 의미 있음.

🌐 **프로토콜 = HTTP.** 본 검증은 EC2 공인 IP/도메인의 **80(HTTP)** 으로 쏜다. HTTPS 는 §6/§9.8 로
미뤘으므로 부하 명령에 `https://` 를 쓰면 실행 불가(자기모순). 무중단 메커니즘은 HTTP 로 충분히
증명된다. (OAuth 로그인 기능은 HTTPS 필요 — §10-R2, 무중단 검증과 별개.)

### 4.2 절차

> 🔬 **구성 고정 원칙 (대조군 설계의 핵심).** Before/After 는 **스토리지 레이아웃을 고정하고 배포
> 메커니즘만 변수**로 둬야 다운타임 델타를 blue-green 에 깨끗이 귀속할 수 있다.
> - **Before** = {RDS + Neo4j 미구동(더미 env) + **단일 백엔드 `docker stop/rm → docker run` 재기동**}
> - **After** = {RDS + Neo4j 미구동(더미 env) + **blue-green 전환**}
> - 둘 다 같은 엔드포인트·같은 스토리지, 오직 배포 choreography 만 다름.
>
> ⚠️ Before 를 글자 그대로 "옛 `docker-compose` 흐름(로컬 MySQL + 로컬 Neo4j + rm→up)"으로 잡으면
> **안 된다** — 스토리지까지 동시에 달라져 비교가 오염되고, 특히 로컬 Neo4j 부팅이 502 버스트를
> *늘려서* blue-green 이 실제보다 좋아 보이는 **유리한 편향**이 생긴다("그 before 가 느린 건 Neo4j 도
> 같이 떠서 아니냐"에 무너짐). Before 하니스 = "신 스토리지 위 백엔드 1개 rm→재run".

0. **Steady-state 200 게이트**: 부하 시작 후 배포 트리거 *전*, 정상 200 흐름을 먼저 확인. 엔드포인트·
   스토리지가 멀쩡함을 선검증해야 이후 non-2xx 가 "배포 탓"임이 보장된다.
1. **Before**: 부하 지속 중 위 Before 하니스(단일 백엔드 stop/rm→run)를 1회 실행. non-2xx 개수·
   에러율·다운타임 구간 길이 기록 → 기준선(예상: 부팅 시간만큼 502 버스트).
2. **After**: 동일 부하·동일 시간·동일 스토리지로 blue-green 전환을 1회 실행. non-2xx 0(또는 0 근접)
   확인.
3. 동일 부하 프로파일(동시성·지속시간)로 비교. 측정값: 총 요청 수, non-2xx 수, 에러율(%), 측정
   윈도우. **성공 기준 = 유실률 0%.**

### 4.3 도구 예시 (검증 아티팩트 — 제품 코드 아님)

> URL 은 **HTTP + 대표 엔드포인트**(§4.1). `<EC2_IP>` = EIP. 대표 엔드포인트는 idempotent GET 으로.

hey (간단):
```bash
hey -z 120s -c 20 http://<EC2_IP>/api/v1/concepts/<id>
# 출력의 Status code distribution 에서 non-2xx 합계를 센다
```

k6 (임계값 자동 판정):
```javascript
import http from 'k6/http';
export const options = {
  scenarios: { soak: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
                       duration: '2m', preAllocatedVUs: 50 } },
  thresholds: { http_req_failed: ['rate==0'] },   // 유실 0% 일 때만 PASS
};
export default function () { http.get('http://<EC2_IP>/api/v1/concepts/<id>'); }
```
부하를 띄운 상태에서 별도 터미널로 배포를 트리거하고, k6 종료 시 `http_req_failed` 가 0 인지 확인.
graceful shutdown/드레인(§2.2-5) 미적용 상태로 한 번, 적용 후 한 번 측정해 차이를 본다.

### 4.4 롤백 시나리오 (마이그레이션 규칙 — 롤백 없는 변경 금지)

- 전환 **전** 신버전 헬스 실패 → fragment 미변경, 구버전 그대로 서비스. 영향 0.
- 전환 **후** 신버전 이상 발견 → fragment 를 직전 색으로 되돌리고 `nginx -s reload`(수동 flip-back).
- 워크플로 차원 롤백 → 직전 git sha 태그로 재배포.
- nginx.conf/compose 변경 자체 롤백 → 본 spec 브랜치 revert(설정 baked 이미지 재빌드).

---

## 5. 측정·기준선 메모

- 현행 다운타임 실측치는 §4.2 Before 단계에서 확보(현재는 미측정). M1 의 성능 측정 관습(warmup +
  반복)과 별개로, 여기서는 "배포 1회당 non-2xx 버스트 길이"가 핵심 지표.
- EC2 RAM 예산: blue-green 전환 구간에 **JVM 백엔드 2개**가 잠깐 공존하므로 헤드룸 부족 시 OOM
  위험. 프리티어 t3.micro(1 GiB)에서의 구체 예산·완화책(RDS 분리, Neo4j 미구동, 스왑, mem_limit)은
  **§9.4** 참조.

---

## 6. Out of scope (의도적으로 이번에 안 하는 것)

기반만 깔고, 아래는 명시적으로 손대지 않는다.

- readiness/liveness **프로브 철학** 정립, Actuator 기반 정교한 프로브(헬스는 200 ping 수준만)
- **자동 롤백** 오케스트레이션(수동 flip-back 만 제공)
- **의존성(DB/Redis) 게이팅** — 헬스가 DB/Redis 연결까지 확인하지 않음
- 쿠버네티스/ECS/오토스케일링/ALB 등 멀티 인스턴스·외부 LB
- **프론트(정적 SPA) 무중단 배포** — 정적 파일 교체는 사실상 무중단이라 별도 취급, 이번은 백엔드 한정
- 무중단 **DB 스키마 마이그레이션**(전·후 버전 호환 스키마)
- HTTPS/인증서·도메인 전환, 관측성·알림 연동
- 세션/스티키니스(현재 JWT 무상태 가정 — 위배 시 별도 검토)

---

## 7. 결정 (확정 — 사용자 위임)

> 갈리는 지점에 트레이드오프와 추천을 달고, 사용자가 "추천대로"로 위임하여 아래와 같이 **확정**한다.
> 구현 착수 시 배포 전략 ADR(`docs/adr/`) 작성. EC2 미생성 상태이므로 D1 의 RAM 전제는
> §9(AWS 프리티어 프로비저닝)에서 충족한다.

### D1. blue-green vs 롤링 (핵심)

| | blue-green (추천) | 롤링(축차 교체) |
|---|---|---|
| 전환 | upstream 한 번에 원자적 전환 | 인스턴스 단위 점진 전환 |
| 롤백 | fragment flip-back 즉시 | 더 복잡(부분 상태) |
| 버전 혼재 | 없음(한 색만 서비스) | 전환 중 신·구 동시 서비스 |
| 피크 메모리 | 백엔드 2개 잠깐 공존(≈2x) | 비슷(N+1) |
| 단일 EC2 적합성 | ◎ 단순·명확 | △ 인스턴스 1~2개라 이점 미미 |

**확정: blue-green.** 단일 EC2 + 긴 JVM 부팅 + 즉시 롤백 요구에 가장 단순하고 베스트 프랙티스에
부합. 유일한 비용은 전환 구간 백엔드 2개 공존 메모리.

> ⚠️ **프리티어 RAM 전제.** AWS 프리티어 t3.micro 는 1 GiB 라 `2×JVM + MySQL + Neo4j` 동시 구동
> 불가. blue-green 이 성립하려면 무거운 상태 저장소를 EC2 밖/축소해야 한다 → **MySQL=RDS 분리,
> Neo4j=미구동(CTE 플래그 ON), Redis=로컬 유지, 스왑 2GB + 컨테이너 mem_limit**. 구체 예산·셋업은
> §9. 이 전제가 깨지면(예: Neo4j 를 EC2 에서 계속 띄워야 함) blue-green 대신 롤링/유지보수창
> 재검토.

### D2. 헬스 신호: 커스텀 엔드포인트 vs Actuator

| | 커스텀 `/api/v1/health`(추천) | Spring Boot Actuator |
|---|---|---|
| footprint | 컨트롤러 1개, 의존성 0 | 의존성 추가 |
| 표준성 | 낮음 | 높음(`/actuator/health`) |
| 레포 방침 | "Actuator 미도입" 의도 유지 | 의도적 미도입을 뒤집음 |

**확정: 커스텀 최소 엔드포인트.** "프로브 철학은 Out of scope" + 레포가 의도적으로 Actuator 를
배제한 맥락에 부합. 나중에 풍부한 헬스가 필요하면 Actuator 로 승격.

### D3. 전환 지점: 프론트 nginx 재사용 vs 전용 리버스 프록시

**확정: 프론트 nginx 재사용**(설정 볼륨 마운트 + `nginx -s reload`). 새 컨테이너 0,
과설계 금지에 부합. 전용 프록시는 관심사 분리는 깔끔하나 이번 범위엔 과함.

### D4. 백엔드 2슬롯 운용: compose 서비스 2개 vs 스크립트 `docker run`

| | compose 서비스 2개(blue/green) | 스크립트 docker run |
|---|---|---|
| 선언성 | 높음(compose 에 명시) | 낮음(스크립트가 상태 관리) |
| 동적 태그 주입 | compose 변수로 다소 번거로움 | 유연 |
| 인프라 정의 일관성 | 인프라와 함께 compose | 백엔드만 compose 밖 |

**확정: 인프라/프론트는 compose, 백엔드 blue/green 만 스크립트 `docker run`.** 동적 sha 태그 주입과
색 전환 제어가 단순하고, 인프라 정의는 compose 에 그대로 둔다. (compose 2서비스도 가능하나 태그
주입이 번거로워 제외.)

---

## 8. 분석 메모 (Analyze-Before-Change)

- **참조 지점**: nginx upstream 을 바라보는 곳 = `web/nginx.conf` 의 `/api/v1/`·`/oauth2/`·
  `/login/oauth2` 3개 location. 배포 스크립트 = 두 워크플로의 deploy job(api/web). 헬스 경로
  추가는 Security 설정 화이트리스트에 영향.
- **영향받는 테스트**: 현재 배포·nginx 관련 자동화 테스트 없음. 헬스 컨트롤러 추가 시 `@WebMvcTest`
  단위 테스트 1개 동반(테스트 없이 컨트롤러 변경 금지). ⚠️ 레포에 `@WebMvcTest` 선례 없음 — 첫
  슬라이스가 `SecurityConfig`(JWT/OAuth 빈) 로딩을 떠안으니 `@Import`/모킹 준비 필요(흔한 마찰).
  `FeatureFlagIntegrationTest` 는 `application.yml` 기본 flag=`false` 를 단정하므로 **기본값은
  false 유지**(배포 env 로만 true 주입) — 바꾸면 깨짐. 부하 검증은 §4.
- **확인된 코드 사실**: flag ON 시 `ConceptService` 의 Neo4j 호출은 전부 else 분기(우회), `KnowledgeSpace`
  는 JdbcTemplate(MySQL), `ProbabilityService` 는 flag-guarded `findPrerequisitesAsDepthMap` 경유.
  단 `conceptRepository` 빈 주입은 남아 기동 시 Neo4j Driver 빈 생성 → §10-R1.
- **롤백**: §4.4.
- **ADR**: 배포 전략(blue-green) 확정 시 ADR 작성. ⚠️ 번호는 **착수 시점 디스크의 다음 빈 번호로
  확정** — roadmap 상 M3 가 0006·0007 을 거론하나 M4→M3 비차단(§9.3)이라 M3 가 먼저 쓴다는 보장이
  없음. "0008 고정"은 M3 선행을 암묵 전제하므로 피한다.
- **피처 플래그**: 인프라/배포 레이어 변경이라 앱 피처 플래그 대상 아님. 단, "구버전·신버전 병행
  + 즉시 롤백" 정책은 blue-green 구조 자체로 충족.
- **상세 리스크·의존성 레지스터**: §10.

---

## 9. EC2 프로비저닝 (AWS 프리티어 — 신규 구축)

> EC2 가 아직 없으므로 본 spec 에서 프리티어 기준 프로비저닝을 함께 정의한다. 12개월 무료 한도
> (단일 t3.micro 등)에 맞춘다. **§7 D1(blue-green)의 RAM 전제를 충족하는 것이 본 절의 핵심 목적.**

### 9.1 제약과 핵심 판단

- 프리티어 EC2 = **t3.micro 1 vCPU / 1 GiB RAM**(750h/월, 12개월). 1 GiB 로는 `2×Spring Boot(JVM) +
  MySQL + Neo4j` 동시 구동 불가.
- 무중단(blue-green)은 전환 순간 **앱 컨테이너 2개의 잠깐 공존**이 필수 → 무거운 상태 저장소를
  EC2 밖/축소로 빼야 성립. ⇒ 아래 9.3 배치가 D1 의 전제.

### 9.2 인스턴스·네트워크

- 리전 **ap-northeast-2(서울)** — 기존 compose 의 주석 RDS 엔드포인트(`...ap-northeast-2.rds...`)와 일치.
- EC2 **t3.micro**, Amazon Linux 2023, EBS **gp3 30GB**(프리티어 30GB 한도 내).
- **Elastic IP 1개**(실행 인스턴스에 연결 시 무료) → 공인 IP 고정, DNS 안정.
- 보안그룹(SG):
  - inbound **80** 0.0.0.0/0, **443**(HTTPS, 후속) 0.0.0.0/0
  - **22(SSH)** 내 IP 만
  - **8080(백엔드) 공개 금지** — Docker 내부 네트워크로만, 외부 노출은 nginx(80/443)만.
- **CI 배포 채널 = SSM Run Command(SSH 아님, ADR 0008).** 러너는 EC2 로 SSH 하지 않는다 →
  SSH 인바운드를 러너에 열지 않는다. EC2 는 IAM instance profile(`AmazonSSMManagedInstanceCore`)로
  SSM 에 등록되고, CI 는 GitHub OIDC 로 얻은 단기 자격의 `ssm:SendCommand` 로만 배포를 트리거한다.
- **22(SSH) 내 IP 만 = 존치(ADR 0008 D4).** 수동 재시드·긴급 운영용으로 남긴다(CI 채널은 SSM).
  키페어 인증(로컬 `~/.ssh/mmt-ec2`), 공용 파일에 자격 미포함. (관리 접근까지 100% SSM Session
  Manager 로 옮기는 것은 실험 종료 시 후속.)

### 9.3 상태 저장소 배치 (RAM 확보의 핵심)

| 구성 | 배치 | 근거 |
|---|---|---|
| MySQL | **RDS 프리티어** (db.t3.micro, Single-AZ, 20GB gp2, 백업 20GB) | EC2 에서 ~400MB+ 오프로드. compose 에 이미 RDS 엔드포인트 주석 존재 → 원래 방향. SG: 3306 은 EC2 SG 에서만 |
| Neo4j | **EC2 미구동** | M2 에서 그래프 탐색이 MySQL CTE 로 이전됨 → `mmt.migration.use-mysql-cte-for-graph=true` 로 CTE 사용. 최대 RAM 절감. **M3(Neo4j 폐기) 방향과 정합** |
| Redis | **EC2 로컬 컨테이너 유지** | 메모리 수십 MB 로 작음. 분리 원하면 ElastiCache 프리티어(cache.t3.micro 750h) 선택 |

> ✅ **Neo4j 미구동 = 의도된 구성.** 소유자 판단상 Neo4j 는 이미 폐기 대상(디렉토리·코드만 미삭제).
> 따라서 M4 의 EC2 는 **처음부터 CTE-only**(`use-mysql-cte-for-graph=true`)로 구성한다 — 이는 M3 의
> "점진 출시"가 아니라 **신규 환경의 초기 구성**이다.
>
> **의존 방향: M4 → M3 (역방향, 비차단).** M4 가 단일 인스턴스를 처음 띄울 때 "MySQL/CTE 만으로
> 실서버 환경에서 정상 동작하는지"를 검증하게 되며(§4·§9.6), 이 결과가 M3 의 Neo4j 폐기 go/no-go
> 근거가 된다. 즉 M3 의 코드·인프라 삭제가 M4 의 선행조건이 아니라, **M4 의 bring-up 이 M3 에 입력을
> 준다.** Neo4j 관련 코드 · `neo4j-deprecated/` · compose 의 `mmt-neo4j` 실제 삭제는 그대로 M3
> 잔여 작업으로 남는다.

### 9.4 EC2 메모리 예산 (배포 피크 — JVM 2개 공존 구간)

| 프로세스 | 평상시 | 배포 피크 |
|---|---|---|
| nginx(mmt-front) | ~20MB | ~20MB |
| Spring Boot 백엔드 | ~350MB ×1 | ~350MB **×2 = 700MB** |
| Redis | ~30MB | ~30MB |
| OS + Docker 데몬 | ~180MB | ~180MB |
| **합계** | **~580MB** | **~930MB** |

- 피크 ~930MB 로 1 GiB 빠듯 → **2GB 스왑파일** 추가(t3.micro 표준 관행)로 순간 오버런 흡수.
- 백엔드 컨테이너에 **`mem_limit: 350m`** + JVM **`-XX:MaxRAMPercentage=70`**(또는 `-Xmx256m`).
- 2개 공존은 전환 수 초~수십 초뿐. 평상시는 단일 백엔드라 충분히 여유.

> ⚠️ **스왑은 양날의 검 — 헬스 폴 false negative / OOM-kill 위험.** 스왑을 "순간 오버런 흡수"로만
> 볼 게 아니다. 2-JVM 공존 구간에 신규 JVM 페이지가 스왑으로 밀리면 **부팅+첫 헬스 응답이 느려져
> `switch-backend.sh` 폴 타임아웃을 넘김 → 멀쩡한 배포가 거짓 실패로 중단**되거나, 메모리 압박 시
> OOM-killer 가 한쪽 컨테이너를 수확할 수 있다. 완화:
> - 헬스 폴 **타임아웃을 넉넉히 + 재시도**(부팅이 스왑으로 느려지는 것을 정상 범위로 흡수).
> - `mem_limit` 을 보수적으로(스왑 thrash 최소화). 두 백엔드가 동시에 풀로드되지 않도록 전환 후
>   즉시 구버전 드레인.
> - 그래도 불안정하면 §7 D1 의 RAM 전제가 깨진 것 → 인스턴스 상향 또는 롤링 재검토.

### 9.5 레지스트리

- **현행 Docker Hub 유지**(공개 무료). ECR 프리티어(500MB) 전환은 선택 — 본 spec 범위 밖.

### 9.6 셋업 순서 (요약)

1. **RDS(MySQL) 프리티어** 생성 → 스키마·시드 적재(기존 마이그레이션 자산).
2. **EC2 t3.micro** 생성 + Elastic IP + SG + 키페어.
3. EC2 초기화: **2GB 스왑**, Docker / docker compose 설치.
4. **비커밋 `docker-compose.yml`** 배치 — `RDS_HOST`=RDS 엔드포인트, `mmt-neo4j` 제외 +
   **더미 `GDB_*`**(§3.3·R1), `use-mysql-cte-for-graph=true`, 백엔드 `mem_limit`(§9.4),
   front fragment 볼륨 마운트.
5. `deploy/active-backend.conf`(§3.2) + `deploy/switch-backend.sh`(§3.4) 배치.
6. **GH Secrets**: `AWS_DEPLOY_ROLE_ARN`=terraform 출력 `ci_deploy_role_arn`, `DOCKERHUB_*`
   (RDS 자격은 compose env/비커밋). EC2 SSM 등록은 terraform instance profile 이 담당 →
   `EC2_HOST`/`EC2_SSH_KEY`/`EC2_PORT`/`EC2_USERNAME` 은 배포 채널에서 폐기(ADR 0008 D5).
7. 최초 1회 수동 배포(`workflow_dispatch`) → **§4 부하 검증**으로 유실률 0% 확인.

### 9.7 비용 주의

- 12개월 이후 프리티어 만료 시 EC2 t3.micro + RDS 과금 시작(러프하게 월 $15~25 수준 추정 — 변동
  크므로 **AWS 요금 계산기로 확인**). 포트폴리오 용도면 만료 전 중지/스냅샷 정책 권장.

### 9.8 후속 (범위 밖 — §6)

- **HTTPS(Let's Encrypt/Certbot 또는 ALB+ACM)** 가 공개 서비스의 즉시 다음 단계. 본 spec 에서는
  80 만 다루고 인증서는 후속.

---

## 10. 리스크·의존성 레지스터 (analyze-before-change 산출)

> 구현 착수 전 점검할 항목. ✓=검증된 사실, ⚠️=가정/미검증.

### R1 — Neo4j 미구동 시 기동 무결성 (⚠️ 미검증, 핵심)

- flag ON 으로 런타임 Neo4j 호출은 우회되지만(✓ §8), `conceptRepository`(ReactiveNeo4jRepository)
  빈은 주입되어 **Spring 이 기동 시 Neo4j Driver 빈을 생성**한다. Actuator 미도입(§1.5)이라 startup
  `verifyConnectivity()` 강제도 없음.
- **처방 사다리(싼 것 먼저)**:
  1. **1차 (코드 0 변경)**: `GDB_URL/GDB_PORT/GDB_USERNAME/GDB_PASSWORD` 에 **더미 값** 주입 →
     플레이스홀더 해소 → 빈 생성 → 드라이버 lazy + flag ON 이라 더미 자격 미사용 → 앱 기동.
  2. **폴백 (코드 변경, 1차 실패 시)**: `Neo4jReactiveAutoConfiguration`/
     `Neo4jReactiveDataAutoConfiguration` exclude + Neo4j 리포지토리 빈 제거. 이는 사실상 M3 의
     영역 → M4 에선 가급적 1차로 버틴다.
- **검증**: 단일 인스턴스 bring-up 시 더미 env 로 기동 성공 + 대표 그래프 엔드포인트 200 확인
  (= M4→M3 입력, §9.3).

### R2 — OAuth ↔ HTTPS 의존 (✓ 사실 / **결정: 후속 유지** — 2026-06-15 확정)

- `application-secure.yml` 의 OAuth `redirect-uri` 가 `https://www.my-math-teacher.com/...` 하드코딩 +
  `allowed.origins1`/`allowed.origins2 = https://${EC2_DOMAIN_NAME1,2}`(`application-secure.yml:70-71`).
  즉 **배포 env 에서 로그인은 HTTPS 필수.**
- 무중단 메커니즘(health/nginx reload/blue-green)은 HTTP·대표 엔드포인트로 시연 가능(§4) → **무중단
  검증과 OAuth 로그인 동작은 분리.** HTTPS 는 §6/§9.8 후속. (M4 로 끌어올지는 사용자 결정.)

### R3 — Docker 네트워크 join (⚠️ spec 누락이었음)

- 백엔드를 compose 밖 `docker run`(§7 D4)으로 빼면, nginx(front, compose)가 `mmt-backend-blue/green`
  을 이름 해석하려면 **같은 user-defined 네트워크에 `--network <compose_default_net>` 로 join** 필요.
  기본 bridge 는 이름 해석 불가 → 누락 시 blue-green 이 조용히 전부 실패. `switch-backend.sh` 에 명시.

### R4 — RDS 스키마·인덱스 선결 (✓ 사실)

- CTE 성능은 M2 spec-01 의 `knowledge_space` 복합 인덱스에 의존 → **RDS 에 M2 스키마 + 인덱스 + 시드
  적재가 §9.6 step1 의 필수 전제.** 누락 시 CTE 가 느려지거나 결과 비정상.

### R5 — "유실률 0%" correctness (⚠️ 측정 설계 보강, §4 반영됨)

- sub-ms `/health` 단독 측정은 drain 경계의 502 를 못 드러냄 → **대표 엔드포인트 + `server.shutdown=
  graceful` + 드레인 타임아웃**이 있어야 0% 클레임이 의미. §2.2-5·§4.1 반영.

### R6 — 스왑 ↔ 헬스 폴 타이밍 (⚠️, §9.4 반영됨)

- 2-JVM 공존 시 스왑 지연이 신규 JVM 의 첫 헬스 응답을 늦춰 폴 타임아웃 초과(거짓 실패) 또는
  OOM-kill 유발 가능 → 헬스 폴 타임아웃·재시도 여유 + 보수적 `mem_limit`. §9.4 반영.

### R7 — 컨테이너명 transitive 참조 (✓ 사실)

- `ec2-user-mmt-backend-1` 가 `web/nginx.conf:20,24`(oauth) + `api-ci-cd-with-ec2.yml:76` 에서 참조.
  upstream 통일 + 워크플로 재작성으로 전부 정리 필요(누락 시 oauth 프록시 깨짐).

### R8 — 프론트(=nginx) 재시작 ≠ 백엔드 무중단 (✓ 구분)

- 백엔드 무중단은 `nginx -s reload`(front 컨테이너 유지)로 달성. front **이미지** 재배포는 컨테이너
  재시작이라 API 프록시도 끊김 → 별도 이벤트(§6 "프론트 무중단 out of scope")와 정합.
