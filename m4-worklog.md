# M4 무중단 배포 — 다음 세션 재개 프롬프트

> 새 세션에서 이 파일 읽고 "재개하자 / X번으로 가자" 하면 이어서 진행.
> 디테일은 **spec-01**(배포 설계, R1~R8, §4 측정, §9 프로비저닝),
> **spec-02**(하네스 게이트 G1~G6, §6.1 개입 원장),
> **spec-03**(terraform plan-only IaC 샌드박스) 참조 — 여기 복붙 안 함.
> 시작 시 **`git log --oneline -12` 로 커밋 상태 재확인** 권장.

## 🟢 2026-07-05 (최신) 상태 (Redis 캐시버그 = 수정 완료, 별도 브랜치 — 다음 = 그 PR 머지→M4 After 재측정) — 새 세션 정본

**최신 한 줄:** §4 After 를 오염시킨 **Redis 크로스인스턴스 캐시 역직렬화 버그를 코드로 수정 완료**(별도 브랜치 `fix/redis-cross-instance-cache-serializer`, off `origin/main`, 커밋 `72d70f7`). 사용자 결정으로 M4 배포 PR 과 분리(백엔드/auth 위험 변경 → 단독). 고강도 코드리뷰(워크플로) 반영해 **무중단 배포 오버랩 안전**까지 강화. **AWS 불필요한 작업은 여기서 끝** — 다음 = 이 fix PR 머지 → M4 브랜치가 픽업 → **§4 After 재측정(= AWS 재-apply 필요, 사람 MFA 게이트)**.

### ✅ 이번 세션 완료 (2026-07-05 캐시버그 수정)
- **근본원인**: `RedisUtil.set()` 이 write 마다 공유 싱글턴 RedisTemplate 의 value serializer 를 `o.getClass()` 기반 Jackson 으로 갈아끼움 → write 이력 없는 인스턴스는 기본 `StringRedisSerializer` 로 남아 캐시 List/Map 을 String 으로 읽음 → 소비측 캐스트 ClassCastException → 401. (`redisBlackListTemplate` 은 별도 @Bean 없어 같은 싱글턴 주입 → 인증경로까지 오염 증폭.)
- **핵심 수정**: `RedisConfig` value serializer 를 `GenericJackson2JsonRedisSerializer`(@class 타입내장) 로 **한 번만 고정**, `RedisUtil` per-write 뮤테이션 2곳 제거.
- **잠복버그 2건 동반 수정**(리뷰/테스트로 발견): ① ids 캐시가 불변 `Stream.toList()` 저장 → 인스턴스간 read 시 `InvalidTypeIdException` → **ArrayList 정규화**. ② depthmap `Map<Integer,Integer>` 직접 캐시 → JSON object 키 String 뭉갬 → `ProbabilityService` 가 int 키로 소비 시 캐시히트마다 터짐(**M2부터 잠복**, 기존 테스트가 Redis mock 이라 은폐) → **`List<ConceptDepth>` 저장 후 read 에서 Map 재구성**.
- **무중단 배포 안전(코드리뷰 반영, 중요)**: 배포 오버랩엔 구·신 인스턴스가 같은 Redis 공유 → 포맷 변경이 크로스버전 read 를 깸. ⇒ ① **그래프 캐시 키 버전 네임스페이스 `graph:v2:`** 도입해 keyspace 분리(구 인스턴스가 신 포맷 안 읽음 — 구 코드엔 fallback 없어 필수). ② `RedisUtil.get()`/`getBlackList()` 가 `SerializationException` 삼키고 **null 반환(fail-closed)** → 오버랩 잔여 구포맷/레거시값을 캐시 miss·값부재로 안전 강등(500 아님).
- **⚠️ 배포 스토리 정정(이전 🟡🟢 블록의 "flush" 전제 폐기)**: 키 버전닝으로 **flush 불필요**(구 `graph:*` 는 24h TTL 자연만료). 이전에 상정했던 "재배포 시 FLUSHALL" 은 **하면 안 됨** — 로그아웃 blacklist 를 지워 이미 로그아웃한 토큰을 재검증(auth 우회, 리뷰 F4). `deleteByPrefix("graph:")` 는 prefix 매치라 v2 도 포함(운영 수동 무효화 유지).
- **검증**: `RedisCrossInstanceSerializerTest`(Testcontainers 실 Redis, 독립 템플릿 2개=blue/green) — 신↔신 round-trip 4종(ArrayList<ConceptResponse>·<Integer>·<ConceptDepth>record·String)·구↔신 레거시 null 강등·불변리스트 null 강등. `ConceptServiceCacheTest`(키 v2·ids ArrayList 타입 가드·depthmap List 저장) 갱신·통과. (`BfsDepthMapEquivalenceTest` 는 실 Redis 연결 필요 — 로컬 redis 부재로 미실행, 내 변경과 무관한 선재 인프라 갭.)
- **정리 커밋(M4 브랜치)**: `6c0abe9`(neo4j 스냅샷 20260622), `59e0100`(개인 M4 운영문서·scheduled_tasks.lock gitignore).

### 🔴 다음 세션 = fix PR 머지 → M4 After 재측정
1. **[사람/AI]** fix 브랜치 push → **PR 생성**(base main). 리뷰 findings 이미 반영. 머지 시 main 에 캐시수정 안착.
2. **[AI]** M4 브랜치가 main 픽업(rebase/merge) → After 재측정 시 새 코드 사용.
3. **[사람 MFA 게이트]** §4 After 재측정 = AWS 재-apply 필요: `source infra/terraform/tf-assume.sh`(MFA) → apply → 재시드 → 재프로비저닝 → 배포 → `green→blue2` 컷오버에서 concepts 유실 0(`status_502+transport_err==0`). **flush 하지 말 것**(위 정정). 끝나면 destroy.
4. **[별개, 문서]** `api/CLAUDE.md` stale 정정(아래 이전 블록 기록): `Optional<MysqlConceptRepository> 스텁` 서술 틀림(그 클래스 없음). 실제 CTE = `JdbcTemplateConceptRepository.findPrerequisitesWithDepth/findPrerequisiteConcepts`.

---

## 🟢 2026-07-05 상태 (재배포·§4 측정 세션 완료 — 인프라 DESTROYED — 다음 = 캐시버그 수정→After 재측정) — ⚠️ 캐시버그·flush 부분은 위 블록으로 대체됨

**최신 한 줄:** 전체 재배포 완주(apply→재시드→재프로비저닝→**SSM 배포 성공**→smoke). **SSM→runuser 무중단 배포 라이브 첫 검증 통과.** 배포 중 nginx Host 헤더 버그 발견·수정·커밋(`8dc97eb`). CTE 마이그레이션 정확성 라이브 증명. §4 Before(구식 in-place)=유실 60% 측정. **§4 After 는 Redis 크로스컨테이너 캐시 역직렬화 버그(green이 blue 캐시를 String으로 읽어 ClassCastException→401)로 오염 — 컷오버 자체는 깨끗(전송 갭 0), 클린 0% 숫자는 이 앱 버그 수정에 종속.** 인프라 `terraform destroy` 완료(과금 정지). 계정 471934607256·region ap-northeast-2 불변. **다음 = 캐시 버그 수정(로컬 Testcontainers, AWS 불필요)→After 재측정→PR #45 ready.**

### ✅ 이번 세션 완료 (2026-07-05 재배포·측정)
- **apply** 18 리소스(SSM/OIDC IAM 6개 추가로 12→18). EIP·RDS 엔드포인트·instance 값은 destroy로 전부 무효.
- **RDS 재시드**: chapters 647·concepts 1631·knowledge_space 3446 (유실0). EC2에서 mysql:8 컨테이너·sql_mode 완화.
- **EC2 재프로비저닝**: M4용 compose(front+redis만, 백엔드는 switch-backend.sh docker run) + `~/mmt-backend.env`(600) + `~/nginx.conf`(conf.d/default.conf 마운트) + `~/deploy/{switch-backend.sh,active-backend.conf=placeholder}`. nginx 콜드부트 OK(`/`=200·`/health`=502).
- **SSM 첫 배포 성공**: **feat ref** 트리거(main은 아직 SSH!). `gh workflow run api-ci-cd-with-ec2.yml --ref feat/... -f skip_tests=true`. test skip·build-push·deploy(OIDC→`aws ssm send-command`→`runuser -l ec2-user`→switch-backend.sh) 전부 green(deploy 48s). blue 부트스트랩. **SSM→runuser 무중단 라이브 첫 검증 = 통과.**
- **🐛 nginx Host 헤더 버그 수정·커밋 `8dc97eb`**(feat, PR #45 스코프): `upstream mmt_backend`(언더스코어)가 `proxy_pass http://mmt_backend`로 Host에 새어 Tomcat 10.1 이 "character [_] is never valid in a domain name" 400. server 블록에 `proxy_set_header Host $host`(+X-Forwarded-*) 추가. EC2 bind-mount라 재배포 불필요(nginx reload로 반영).
- **CTE 마이그레이션 정확성 라이브 증명**: neo4j 스냅샷 **unique 집합** 대조 3/3 일치(6646 d2=10·d3=14, 7595 d2=4). EXPLAIN `idx_knowledge_space_composite` "Using index"(커버링). ⚠️ **문서 stale 정정 필요**: `api/CLAUDE.md`의 `Optional<MysqlConceptRepository> 스텁` 서술 틀림 — 그 클래스 없음. 실제 CTE = `JdbcTemplateConceptRepository.findPrerequisitesWithDepth/findPrerequisiteConcepts`(WITH RECURSIVE). /nodes depth = school_level(초등 3·그외 5). 스냅샷 count(37/105/…)는 Neo4j 경로 multiset이라 CTE로 재현 불가 — 동치성 게이트도 Set만 비교(의도된 설계).
- **§4 Before(구식 in-place `docker restart mmt-backend-blue`)**: 유실 **60.33%**(900중 543 = nginx 502). 다운타임 명백 → 페어니스 OK.
- **텔레메트리** `infra/terraform/run-logs/2026-07-05T05-35-20Z/`: apply·ec2 steady/cutover 스냅샷·s4-{steady,before,after,after2}.{json,k6.log}. 메모리: blue steady **78.6MiB/350MiB**, green 피크 **247.8MiB/350MiB(70.8%, =MaxRAMPercentage70)**. 지연 웜 ~60ms·p95 137ms@10rps. **30rps 붕괴(t3.micro 1vCPU 천장)** → §4 RATE=10 채택.

### 🔴 다음 세션 = Redis 캐시 버그 수정 → After 재측정 (핵심 블로커)
- **버그: Redis 크로스컨테이너 캐시 역직렬화.** 그래프 엔드포인트가 CTE 결과 List를 Redis(`graph:prerequisites:objs:<id>:<depth>`, TTL 24h)에 캐시. **다른 컨테이너 인스턴스가 그 값을 String으로 읽어 `java.lang.ClassCastException: String cannot be cast to List` → ExceptionTranslationFilter가 401**. 같은 인스턴스는 자기 write 정상 read(자기일관성 OK). FLUSHALL 하면 해당 인스턴스 200. (green 직접 401 확인, flush 후 200 확인, green steady 991/991 200.)
  - **의심 지점**: RedisTemplate value serializer(`util/RedisUtil`·RedisConfig). 캐시 값 타입이 인스턴스 간 round-trip 안 됨. green 로그에 프로파일 "securelocal","secure" 동시 활성(env는 SPRING_PROFILES_ACTIVE=secure만) — securelocal이 어디서 새는지도 점검.
  - **재현/검증**: 로컬 Testcontainers(백엔드 2인스턴스+redis, 한쪽 warm→다른쪽 read). **AWS 불필요.**
  - **함의(중요, 별도 finding)**: `/health` smoke는 통과하지만 데이터경로가 stale 캐시로 401 → **헬스게이트가 데이터경로 미검증.** switch-backend.sh 헬스폴(HEALTH_PATH)을 대표 데이터 엔드포인트로 강화 검토(spec-02 G4 smoke grader).
- **After 재측정**(캐시 수정 후): 짧게 재-apply→재시드→재배포→`green→blue2` 컷오버에서 concepts 유실 0 확인. 상태분해 프로브 `loss-probe2.js`(정본지표 `status_502+transport_err==0`) 사용. 무중단 전송 속성은 정황상 이미 입증(After 실패는 401=앱, 502=전송갭 아님)이나 클린 숫자는 이때.
- **PR #45(SSM)**: draft. SSM 라이브 검증 통과 → nginx 커밋(`8dc97eb`) 포함 Ready→머지 가능. 캐시버그를 M4 범위에 넣을지 별도 이슈로 뺄지 결정.
- **재현 자산**(스크래치패드, 세션소멸 주의 — 필요시 재작성): `loss-probe.js`/`loss-probe2.js`(k6)·`tf-creds-to-file.sh`(MFA 자격→파일)·`seed.sh`·`cte-check.sql`·provision 번들(compose/env/nginx). SSM 배포는 **feat ref** 필수(main deploy job은 아직 appleboy/ssh-action).

---

## 🟡 2026-07-05 (이전) 상태 (SSM 구현 세션 — 재배포 전 기록)

**최신 한 줄:** SSH 배포가 SG(my_ip/32) vs 러너 IP 로 막힌 뒤 **SSM Run Command 전환을 코드+ADR 로 구현 완료**(무과금·오프라인 validate). M4 브랜치에 커밋 3개(`3c899f6` terraform·`40e166e` ci·`1b221d5` docs), **draft PR #45**(라이브 검증 후 Ready→머지). 인프라는 여전히 DESTROYED(과금정지). **다음 = 재배포·§4 측정 단일 세션.**

### ✅ SSM 전환 구현 완료 (2026-07-05)
- **ADR 0008**(Proposed) `docs/adr/0008-...ssm-run-command.md` — SSM 채널·CI인증=**GitHub OIDC**·EC2 IAM instance profile·**내 IP SSH 존치(D4)**·static 키 폐기는 web-ci-cd-to-s3 의존으로 이월(D5).
- **terraform** `iam.tf`(EC2 SSM instance profile + GitHub OIDC provider+role, `ssm:SendCommand`/`GetCommandInvocation` 최소권한·Project=mmt 태그 스코프) + `compute.tf` attach(in-place) + `variables.tf` `github_repo`. `validate`/`fmt` ✅. **network.tf SSH 룰은 존치(무변경)**.
- **워크플로** deploy job: appleboy/ssh-action 제거 → OIDC(`configure-aws-credentials@v4`) + `aws ssm send-command`(태그 타겟) + 인스턴스ID 해석 + 상태 폴링(~10분) + stdout/stderr 회수 + 성패 판정.
- **switch-backend.sh**: 워크플로가 `runuser -l ec2-user` 로 호출(D1) → 스크립트 동작 변경 없음, 호출 규약만 헤더에 문서화.
- spec-01 §3.5/§9.2/§9.6 + roadmap M4 갱신.

### 다음 세션 = 재배포·§4 측정 (단일)
아래 "내일 재개 순서"(apply→재시드→재프로비저닝) + **SSM 배포**(SSH 아님) + smoke + Before/After 유실측정 + destroy. **SSM 특유의 사람 선행조건**:
- 🔴 **[사람] apply 후** GH Secret `AWS_DEPLOY_ROLE_ARN` = terraform 출력 `ci_deploy_role_arn` 주입. 구 `EC2_SSH_KEY`/`EC2_HOST`/`EC2_PORT`/`EC2_USERNAME` 은 배포 채널에서 폐기(수동 SSH 는 로컬 키로).
- 🔴 **static AWS 키(`AWS_ACCESS_KEY_ID`/`SECRET`)는 폐기 금지** — `web-ci-cd-to-s3` 가 아직 사용 중(ADR D5).
- 라이브에서 SSM→runuser 전환이 실제 무중단인지 = **이번이 첫 검증**. 초록불 뜨면 PR #45 Ready→머지.

---

## 🔴 2026-07-05 (이전) 상태 (test 게이트=옵션 A 결정·구현) — SSM 전환 전 기록

**핵심 한 줄:** 인프라·시드·프로비저닝·PR머지·첫 배포트리거까지 갔으나 **워크플로 `test` job이 CI 첫 실행에서 실패 → build/deploy 스킵 = 미배포.** `terraform destroy`로 과금 정지 상태. **test 게이트는 옵션 A(우회, 측정 우선)로 결정·구현 완료(커밋 `eeca7da`, PR #44 open).** 다음 = **PR #44 머지(사람)** + **MFA(사람)** → 재-apply→재시드→재프로비저닝→§4 측정→destroy.

### ✅ 2026-07-05 재-apply·재시드·재프로비저닝 완료 (첫 배포 직전, EC2_HOST 대기)
- **apply 완료** 12 리소스. **새 값**: EIP `15.164.111.180` · RDS `mmt-db.c7qu444ug8bf.ap-northeast-2.rds.amazonaws.com:3306` · instance `i-0b7adb321a8cc5be8` · SG `sg-0dfaf732f8f823978`. (계정 471934607256·region ap-northeast-2 불변)
- **RDS 재시드 완료**(유실0): chapters **647**·concepts **1631**·knowledge_space **3446**. mysql:8 클라 컨테이너로 EC2→RDS, sql_mode 완화. EC2 `~/seed/.dbpw`(600).
- **EC2 재프로비저닝 완료**: `docker compose up -d`로 mmt-front(nginx)+mmt-redis 기동(net `ec2-user_default`). `~/mmt-backend.env`(600: RDS 실endpoint·비번=tfvars·JWT자체생성·OAuth placeholder·REDIS 공유)·`~/.env`·`~/nginx.conf`·`~/deploy/{switch-backend.sh,active-backend.conf=placeholder `server 127.0.0.1:8080;`}` 배치. nginx 콜드부트 정상, 외부 `/api/v1/health`=502(백엔드 부재 정상)·`/`=200.
- **COMPOSE_NET variable 교정**: placeholder 문자열 → `ec2-user_default`(폴백 안 걸리는 버그 수정). 나머지 시크릿(SSH키·유저·포트·DockerHub) 유효.
- 📊 텔레메트리: run `infra/terraform/run-logs/2026-07-05T03-50-23Z/` (apply·seed-done·ec2-init 스냅샷).
- **EC2_HOST 갱신 완료**(새 EIP `15.164.111.180`, 04:10Z). **첫 배포 트리거함**(run 28729108946, skip_tests=true).
- **🔴 첫 배포 결과: `test`=skipped(옵션 A 정상 작동!) · `build-and-push`=FAIL(DockerHub 로그인 `unauthorized: incorrect username or password`) · `deploy`=skipped.** → **남은 블로커 = 🙋 DockerHub 시크릿 재발급**: `DOCKERHUB_USERNAME`(2024-06-13 오래됨)/`DOCKERHUB_TOKEN`(RW 스코프) 재설정. 이미지=`<user>/mmt-backend:<sha>`. 고친 뒤 `gh workflow run api-ci-cd-with-ec2.yml -f skip_tests=true` 재트리거(인프라·시드·프로비저닝 생존, 재빌드만).
- 스크래치패드: k6 `loss-probe.js`(검증됨)·MFA 자격헬퍼 `tf-creds-to-file.sh`·자격 `tfcreds.env`(1h)·env 스켈레톤. 세션ID `4340ea22`.

### ✅ 2026-07-05 이번 세션 진행 (게이트 우회 착수)
- **test 게이트 결정 = 옵션 A**(우회·측정 우선, 사용자 확정). 워크플로 `workflow_dispatch`에 `skip_tests`(기본 false) 입력 추가 + `test` job `if: !inputs.skip_tests` + build-and-push `if: result==success||skipped` + deploy `if: build result==success` 가드. **커밋 `eeca7da`**, YAML 검증 통과.
  - `skip_tests=false`(기본)면 게이트 정상 유지(실패 시 build/deploy 스킵) → 되돌림 안전. **§4 측정·destroy 후 이 input+가드 제거**(후속).
- **PR #44 open**(feat 브랜치 재사용, base main). `skip_tests` 입력이 UI/API에 뜨려면 **main에 있어야** 함(workflow_dispatch 입력은 기본 브랜치 정의에서 읽음) → **머지 필수(사람 게이트)**, 첫 배포(step 7/D) 전.
- **IP 확인**: 현재 `27.1.27.65` = tfvars my_ip 일치 → 재개순서 2번(SG 갱신) 이번엔 skip. (세션마다 바뀌면 재확인 필요)
- **남은 즉시 액션 2개(둘 다 사람):** ① PR #44 머지 → main에 skip_tests 안착 ② `source infra/terraform/tf-assume.sh`(MFA) → 그 뒤 AI가 apply부터 자율.

### 내일 재개 순서 (재-apply부터)
1. `cd infra/terraform && source tf-assume.sh`(MFA, 1h).
2. **my_ip 재확인**: `curl -s https://checkip.amazonaws.com` → tfvars my_ip 다르면 갱신(SG SSH 룰).
3. `terraform apply` → **새 EIP·새 RDS endpoint 발급**(아래 직전값 전부 무효).
4. **RDS 재시드**(skip_final_snapshot=true라 유실됨): `api/sql/` create→add_knowledge_space_indexes→insert_chapters→insert_concepts_escape→insert_knowledge_space. EC2에 scp 후 `docker run mysql:8` 클라이언트로 `mmt`/`mmtadmin`, sql_mode 완화. 기대 647·1631·1631(concepts는 파일 1633라인=1631문, 정상)·3446... **concepts 1631** 맞음. 비번=tfvars db_password(600 파일로만).
5. **EC2 재프로비저닝**: `~/mmt-backend.env`(RDS_HOST=새 endpoint)·`~/.env`·`~/nginx.conf`·`~/deploy/*`·`~/docker-compose.yml` 재배치, `active-backend.conf`=`server 127.0.0.1:8080;` placeholder, `docker compose up -d`.
6. **GH Secret `AWS_DEPLOY_ROLE_ARN`=terraform 출력 `ci_deploy_role_arn` 주입**(사람). 배포는 SSM(OIDC)라 `EC2_*` SSH 시크릿 불필요. `DOCKERHUB_*` 유효.
7. **배포 재트리거** `gh workflow run api-ci-cd-with-ec2.yml -f skip_tests=true` (test 게이트=옵션 A 확정·머지됨) → deploy job 이 `aws ssm send-command` 로 blue-green 전환.

### 직전 apply 값 (⚠️ destroy로 전부 무효 — 참고용)
- ~~EIP 13.125.175.143~~ · ~~RDS mmt-db.c7qu444ug8bf...~~ · ~~instance i-0f58...~~ · ~~SG sg-01e0...~~. 계정 `471934607256`·region ap-northeast-2 는 불변.

### 완료됨
- **apply** 12리소스(SG description 비-ASCII `§`→`sec` 픽스 `0b8ad80`). tfvars my_ip 현재값 갱신됨(세션마다 IP 바뀌면 재갱신+apply).
- **RDS 시드** chapters 647·concepts 1631·knowledge_space 3446(유실0), 복합인덱스 커버링 EXPLAIN "Using index"·CTE non-empty(7925→35) 검증. EC2 `~/seed/.dbpw`(600) destroy까지 유지.
- **EC2 프로비저닝**: `docker compose up`으로 mmt-front(nginx)+mmt-redis 기동(net `ec2-user_default`). `~/mmt-backend.env`(600: RDS실값·REDIS_URL=mmt-redis·JWT자체생성·OAuth placeholder·EC2_DOMAIN)·`~/.env`(REDIS_PASSWORD 공유)·`~/nginx.conf`·`~/deploy/{switch-backend.sh,active-backend.conf}` 배치. **콜드 부트스트랩**: `~/deploy/active-backend.conf`를 `server 127.0.0.1:8080;` placeholder로 둬 백엔드 부재에도 nginx 기동(committed 기본값 `mmt-backend-blue`는 cold-start "host not found"로 실패 → **deploy-design 후속**: 첫 배포가 mmt-front 존재 가정, 정식수정 별도).
- **커밋**: 워크플로 SSH `password→key` `3305ccb`, network.tf ASCII `0b8ad80`. **PR #43 머지됨**(main). GH Secrets 주입완료(`DOCKERHUB_*`·`EC2_HOST`·`EC2_USERNAME`·`EC2_PORT`·`EC2_SSH_KEY`). COMPOSE_NET variable 불필요(폴백 `ec2-user_default` 일치).

### 🚧 블로커: 워크플로 `test` job 실패 (run 28711626110)
- 이 워크플로는 그간 dormant(workflow_dispatch 전용, "M2 후 자동트리거 제거") → **`./gradlew test` 전수가 CI에서 처음 도는 것.** 로컬(로컬 MySQL·env)에선 가려졌던 CI 비호환.
- 실패 3부류: (a) **`ApiApplicationTests.contextLoads()`** — `@ActiveProfiles("test")` **없어** 기본프로파일로 뜨다 datasource 없음(DataSourceBeanCreationException). (b) **Testcontainers 테스트 다수**(Neo4jAbsentBootSmokeTest·FeatureFlagIntegrationTest·GracefulShutdownConfigTest·QueryTimingAspectTest·BfsDepthMapEquivalenceTest·ConceptServiceFeatureFlagTest) — test 프로파일에서 **미해소 `${...}` placeholder**(PropertyPlaceholderHelper.java:180). *정확한 placeholder명 미확정* → 새 세션에서 `./gradlew test --tests GracefulShutdownConfigTest` 로컬(docker 켜고) or raw 로그로 확정. (c) **`RepositoryBenchmarkTest.shouldNotRegressFindResultsPerformance`** — perf 회귀 assertion(:258), 공유러너에서 태생적 flaky.
- **내 M4 변경(SSH auth·network.tf)과 무관** — 선재 테스트/CI 비호환.

### ▶ 다음 결정 (트레이드오프 — 사용자에게)
- **옵션 A (측정 우선·게이트 우회):** 워크플로에 `workflow_dispatch` 입력 `skip_tests` 추가 or deploy/build의 `needs:`에서 test 임시 분리 → 첫 배포·§4 측정 완료 후 되돌림. CI 파이프라인(build-push·ssh-action·switch-backend) 배선은 검증되고 test게이트만 스킵. test CI-호환은 후속.
- **옵션 B (게이트 정공):** application-test.yml(경로 `api/src/main/resources/`)에 미해소 placeholder stub + ApiApplicationTests에 `@ActiveProfiles("test")` or `@Disabled` + RepositoryBenchmarkTest CI 제외(@Tag/그레이들 exclude). 정공이나 M4 무중단 목표엔 우회로.
- 권장: **A로 §4 측정 먼저 끝내고(인프라 과금 방어) B는 별도.** 단 사용자 확인 필요.

### 그 뒤 (AI, 배포 성공 후)
1. **smoke**: blue 부팅 + `/api/v1/health` 200 + `GET /api/v1/concepts/nodes/7925` **non-empty**(단순 200 아님, R4). `/api/v1/concepts/**`·`/api/v1/chapters/**`·`/api/v1/health` = **permitAll**(SecurityConfig 82·88행) → 부하에 JWT 불필요.
2. **§4 페어니스 vet(사람 1회)** → **부하측정**: 대표 GET=`/api/v1/concepts/nodes/{id}`, HTTP80 EIP, **k6**(설치됨) or ab. steady 200 게이트 → Before(단일 백엔드 stop/rm→run, 502 버스트) → After(blue-green, `http_req_failed==0`). 📊 컷오버순간 `run-log.sh ec2 13.125.175.143 cutover`. *k6 스크립트는 스크래치패드에 있었음(세션소멸) → 재작성: constant-arrival-rate RATE 50, thresholds http_req_failed rate==0, nodes/7925 non-empty체크.*
3. **`terraform destroy`** + 텔레메트리 `run-log.sh tf-destroy`.

### 실험 종료 teardown (destroy와 함께)
- `.claude/settings.local.json`의 ssh/scp allow 룰 2개 회수(`Bash(ssh -i ~/.ssh/mmt-ec2*)`·`Bash(scp -i ~/.ssh/mmt-ec2*)`, 2026-07-05 임시추가).
- run-log 원장 → `docs/benchmark/milestone-4-run-report.md` 큐레이션. 다음날 Cost Explorer 실제 대조.

### 텔레메트리
- run 디렉토리 `infra/terraform/run-logs/2026-07-04T15-13-32Z/` (apply time·outputs·cost-ledger·ec2-init 스냅샷). gitignore.

## 지금 어디 (2026-07-03)

- **M4 = 배포 무중단화.** 설계 3건 확정·커밋: spec-01(배포), spec-02(하네스 게이트), spec-03(terraform).
- 브랜치: `feat/m4-spec-01-zero-downtime-deployment`. PR 미생성(성공 후 올릴 예정). 푸시 안 함.
- **닫힌 조각:**
  - **A** — `GET /api/v1/health` (200 OK, 의존성 검사 없음). 커밋 `025daf1`
  - **A.5** — `server.shutdown=graceful` + `timeout-per-shutdown-phase: 30s` (R5). 커밋 `5da8d9b`
  - **R1** — Neo4j 부재 + flag ON + 더미 GDB props 로 풀 컨텍스트 기동 로컬 증명. 커밋 `1cc6efd`
  - **B** — nginx blue-green 전환 구조 + spec-01 §3.2 경로 정정. 커밋 `3491a55`. `nginx -t` form 검증 통과.
  - **Terraform Phase A · provider 슬라이스** — `provider.tf` + `.terraform.lock.hcl`. LocalStack STS 왕복 green(`caller_identity=000000000000`). 커밋 `aea163d`.
  - **Terraform Phase A · network 슬라이스** — `network.tf`(SG) + `variables.tf`(my_ip) + provider ec2 엔드포인트. **validate→plan→apply(5 added)→destroy(5 destroyed) 사이클 완주.** 커밋 `4840fba`.
  - **Terraform Phase A · compute 슬라이스** — `compute.tf`(EC2 t3.micro + EIP + association, AL2023 AMI data source, user_data 더미 GDB env) + `variables.tf` 확장. **plan→apply(8 added)→destroy(8 destroyed) 완주.** 커밋 `726e7a7`.
  - **Terraform Phase A · database 슬라이스 = Phase A 완료** — `database.tf`(RDS db.t3.micro/MySQL8/Single-AZ/20GB) + db_* 변수 + provider rds 엔드포인트. **validate+plan 까지만**(community RDS 미지원·R-T1, apply 의도적 미실행). plan 9 to add, password=(sensitive) 마스킹. 커밋 `890e038`.
- **✅ M4 본류(앱/CI 측) 전부 닫힘:** A·A.5·R1·B + **C(switch-backend.sh `ce3ccc7`)** + **D(워크플로 `abd08af`)** + Terraform Phase A + §6.1 원장 첫 줄. **AWS 무관 작업은 더 없음.**
- **G1 사람 핸드오프 완료(2026-07-03).** 계정·root MFA·빌링 경보·IAM assume-role(B_IAM: `mmt-base`/`mmt-admin` 프로필 + `mmt-terraform-admin` role). **Terraform Phase B `plan` 성공(12 리소스)** — provider 실 AWS 전환 · 키페어(`~/.ssh/mmt-ec2`) · RDS 3306 SG 배선 · tfvars(db_password·my_ip, gitignored). 런북 `infra/terraform/README.md`. **남은 것 = G2 시크릿 주입 · G3 `terraform apply`(과금, 사람 GO) · apply 후 RDS 시드·EC2 초기화 · §4 유실률 검증.** R2(HTTPS) 후속 유지.
- 최근 커밋(2026-07-03 Phase B): `dec0139`(provider 실AWS 전환) · `6279af8`(env assume-role 자격) · `f4e5c56`(키페어·RDS SG) · `97ad9af`(런북) · `b9a9111`(메타 동기화). 이전: `abd08af`(D) · `ce3ccc7`(C) · `890e038`(TF database).

## Carry-forward (스펙엔 안 적힌 결정들 — 다시 하지 말 것)

- **비용 모델 = 신규 크레딧(2026-07 계정, 유료 플랜, $200 선차감).** 옛 12개월 프리티어 아님(경계 2025-07-15). 상시 무료 한도 없이 모든 사용료가 $200 크레딧에서 선차감(크레딧 12개월 유효), 소진 후 실비. 인프라는 **측정용** → apply→측정→destroy 를 짧게 붙여 끝내고 **안 쓸 땐 반드시 `terraform destroy`**(24/7 ~$37/월 중 EIP IPv4 ~$3.6/월은 상시 차감). RDS 는 `skip_final_snapshot=true` 라 destroy 시 시드 유실 → 재시드 전제. 관련 코드 주석(EIP·t3.micro "무료" 표현) 정정 완료.
- **R1 은 이미 종결.** 더미 `GDB_*` 면 뜬다 → 비싼 폴백(`Neo4jReactiveAutoConfiguration` exclude) **불필요**. R1 재검증 금지.
- **A.5 의 30s = Task C `docker stop -t` 의 하한.** §9 에서 대표 엔드포인트 p99 측정 후 조정.
- **R1b(CTE non-empty) 단위 테스트 안 만듦.** 정확성=CI 동치성 오라클, prod 데이터 non-empty=G4 smoke 소유 (검수자 2층, spec-02 §4).
- **spec-02 §6.1 개입 원장 M4 첫 줄 = 기입 완료(커밋 `e32963e`).** "Terraform Phase A: G3 plan-only, 사람 수동 개입 0"(risk-forced 0/chosen 1). spec-03 §6 의 "어시스턴트 terraform 직접 실행 불가" 가정도 각주로 정정함(이 CC 환경은 Bash 로 직접 실행). **다음 줄("첫 real apply/배포")은 G1 진입 시 채움.**
- **AI attribution 트레일러 = 제거(2026-07-03 정책 override).** PR 컨벤션 우선 — 이전 "유지" 메모는 뒤집힘. 최근 M4 커밋들(`dec0139`~`b9a9111`)엔 트레일러 없음. 재논의 금지.
- **Terraform = spec-03 으로 승격(백로그→실행 스펙).** D1~D3 잠금: **D1 LocalStack**, **D2 `infra/terraform/`**, **D3 local state + provider 버전 핀**. 재논의 금지. backlog 문서(`docs/backlog/terraform-iac-for-m4-provisioning.md`)는 배경·경계 기록으로 유지.
- **LocalStack 이미지 = `:3` community 로 핀(2026-06-23 확정).** `:latest`(2026.5.4)는 라이선스 토큰을 요구하며 exit 55 로 boot 실패 → spec-03 의 "무계정·무과금" 전제가 latest 에선 깨짐. community `:3`(STS/EC2 mock)로 핀. **RDS 는 community 미지원(Pro)** → database.tf 슬라이스 mock-apply 는 못 함, plan/validate 까지만(R-T1 와 정합). 컨테이너: `docker run -d --name mmt-localstack -p 4566:4566 -e SERVICES=sts,ec2 localstack/localstack:3`. 재논의 금지.
- **호스트 toolchain 확정.** `terraform 1.15.6`(brew `hashicorp/tap/terraform`, core 아님 — 라이선스 변경), `init` 이 resolve 한 **aws provider = `v5.100.0`**(`.terraform.lock.hcl` 에 핀·커밋됨). provider.tf 가 `localhost:4566` 박아둬서 terraform 은 컨테이너 아닌 **호스트에서** 돈다.
- **SG rule 스타일 = 분리형 v5 리소스.** `aws_security_group`(인라인 룰 없음) + `aws_vpc_security_group_(in|e)gress_rule` 분리. 인라인 block 과 혼용 금지(provider v5 충돌). compute.tf 의 `aws_instance` 가 `output.app_security_group_id` 참조 예정.

## M4 트랙 현황

| 조각 | 내용 | 상태 |
|---|---|---|
| A·A.5·R1·B | 헬스·graceful·기동증명·nginx 전환구조 | ✅ 완료·커밋 |
| C switch-backend.sh | blue-green (`--network`, 헬스폴 재시도, `docker stop -t ≥30s`) | ✅ 커밋 `ce3ccc7` (🔴 동작 검증은 배포 때) |
| D 워크플로 | `github.sha` immutable + deploy job → 스크립트 호출 | ✅ 커밋 `abd08af` (🔴 동작 검증은 배포 때) |
| §9 프로비저닝 | EC2/RDS/EIP/SG + 더미 GDB + RDS 시드 | 미착수 (🙋 G1~G3, AWS 미준비) |
| **Terraform Phase A** | plan-only, LocalStack — IaC 학습 + §9 형태 잡기 | ✅ **완료**: provider·SG·EC2/EIP·RDS 4슬라이스 커밋(`aea163d`→`890e038`). EC2/SG 는 apply→destroy 완주, RDS 는 plan-only(R-T1) |

## Terraform Phase A 메모 (✅ 완료 — 4슬라이스 전부 커밋)

**슬라이스 순서 (전부 닫힘):**
`provider.tf`(STS 1-touch) ✅ → `network.tf`(SG) ✅ → `compute.tf`(EC2+EIP) ✅ → `database.tf`(RDS, plan-only) ✅. 더미 `GDB_*` 는 R1 값(`localhost`/`7687`/`neo4j`/`dummy`)으로 compute user_data 에 주입됨. `endpoints` = sts+ec2+rds. `variables.tf` = my_ip/instance_type/root_volume_size/gdb_*/db_*.
원칙 적용 완료: "안 닿은 배선은 미리 선언 안 한다" — 매 슬라이스 진입 시 endpoint·변수 추가.

**Phase B 진입(계정 생긴 후) 재현 절차 — LocalStack 다시 띄울 일 있으면 (`:3` community, latest 금지):**
```
docker run -d --name mmt-localstack -p 4566:4566 -e SERVICES=sts,ec2 localstack/localstack:3
curl -s http://localhost:4566/_localstack/health   # "sts"/"ec2" available 확인
cd infra/terraform && terraform init && terraform plan   # 호스트 terraform brew 1.15.6
# RDS 비번: export TF_VAR_db_password=... (비커밋). apply 는 Phase B(real)/G1 전까지 금지
```
**Phase B = 같은 config 를 real AWS 에 `plan`(T1 read 자격).** apply(=Phase C)는 spec-02 G3 사람 게이트.

**green 의미 고정 (spec-03 §5 패턴 — Phase B 에서도 적용):**
- `validate` green = HCL 문법+내부정합만. 도달성 0.
- `plan`/`apply` 가 LocalStack 엣지에 실제로 닿은 증거 = probe output(`caller_identity=000000000000`) + 리소스 ID 발급(예: SG `sg-...`). 파싱 성공만으론 닿았는지 모름(함정).
- LocalStack 내려가 있으면 connection refused 로 **red** → probe 설계대로 작동(정상).
- green 이 보증 **안 하는** 것: LocalStack↔실 AWS 정합(영영 증명 불가, G1 영역), 실제 계정·결제(G1, 미룸). **LocalStack 에서 배우는 건 AWS 아니라 *테라폼***(사이클·그래프·HCL).

**커밋 단위:** 각 슬라이스는 로컬 `validate`/`plan`(+첫 생성 리소스는 apply→destroy) green **후** 커밋. 미검증 초안 커밋 금지. 트리거는 "커밋하자". (provider 슬라이스는 `.terraform.lock.hcl` 동반 커밋 완료.)

## B 완료 메모 (참고)

- `web/nginx.conf`: `upstream mmt_backend { include /etc/nginx/active-backend.conf; }` + 3 location 모두 `proxy_pass http://mmt_backend;` 통일(R7).
- `deploy/active-backend.conf` 신규: 교체용 fragment(기본 `server mmt-backend-blue:8080;`).
- ⚠️ **spec-01 §3.2 정정 완료(커밋 `3491a55`).** fragment 를 conf.d **밖**(`/etc/nginx/active-backend.conf`)에 마운트 — `include conf.d/*.conf` 가 fragment 를 http 컨텍스트로 이중 로드해 `nginx -t` 깨지는 함정. spec 본문도 같은 경로+근거로 갱신됨.
- 검증: `docker run nginx:1.21.4-alpine nginx -t` — resolvable host 로 syntax ok, 실 fragment 로 `host not found in upstream`(=R3 deploy-time DNS, 형태 정상). 동작 검증은 배포 때.

## 아직 열린 것 / 다음 진입점

- **Terraform Phase A ✅ 완료 + §6.1 원장 첫 줄 ✅ 완료.** 남은 다음 작업 = **M4 본류** (AWS 무관, 지금 바로 가능):
  - ~~**C (switch-backend.sh)**~~ — ✅ 완료·커밋 `ce3ccc7`. `deploy/switch-backend.sh`: 활성색 감지→idle docker run(`--network` R3·더미 GDB R1·mem_limit/JAVA_TOOL_OPTIONS §9.4)→헬스폴(R6, 일회용 curl 프로브)→fragment 제자리 재작성(bind-mount inode 보존)→`nginx -t`+reload→구버전 `docker stop -t 30`. 실패처리 §4.4(전환전 실패=구버전유지, reload전 `nginx -t` 실패=fragment 롤백). 시크릿은 `BACKEND_ENV_FILE` 비커밋 env-file 로만. `bash -n` 통과(shellcheck 미설치). 동작 검증은 배포 때.
  - ~~**D (워크플로)**~~ — ✅ 완료·커밋 `abd08af`. `api-ci-cd-with-ec2.yml`: build-and-push 태그 `:1.0.0`→`${{ github.sha }}`, deploy job 의 `rm -f→rmi→compose up→prune` 제거하고 appleboy/ssh-action 으로 `bash deploy/switch-backend.sh "$NEW_TAG"` 호출. `envs: IMAGE_REPO,COMPOSE_NET,NEW_TAG` 전달(`COMPOSE_NET` 은 `vars.COMPOSE_NET || 'ec2-user_default'` 폴백). YAML 검증 통과(ruby). **⚠️ 이후 SSM 전환(ADR 0008·PR #45)으로 이 deploy job 은 appleboy/ssh-action → `aws ssm send-command`(OIDC) 로 재작성됨 — 위 상단 🟢 블록이 정본.**
  - ~~**§9 프로비저닝 / Terraform Phase B**~~ — ✅ **Phase B `plan` 성공(2026-07-03, 12 리소스).** 다음 = **G3 `apply`부터** → 상세 순서는 아래 **"남은 단계"** 참조.
- R2(HTTPS)는 "후속 유지" 확정(2026-06-15).

## GO 하면 어디까지 자율 / 어디서 멈추나 (autonomy run-map, 2026-07-04)

> "GO" 한 번으로 끝까지 자동 아님. **MFA·시크릿·머지 지점마다 사람 pause/resume.**
> 주체별 상세는 아래 **"남은 단계 — 소유권 체크리스트"** 정본 참조 — 이 표는 실행 순서 압축맵.

| 단계 | 주체 | AI 자율? |
|---|---|---|
| `source infra/terraform/tf-assume.sh` (MFA 코드 입력) | 🙋 사람 | ✋ **AI 불가**(MFA 못 침). 매 세션·1h 만료 시 재실행 |
| `terraform apply` (사람 GO = G3 승인) | 🤖 AI | ✅ 자격이 셸 env 에 들어온 뒤 |
| RDS 시드(R4) → EC2 deploy 파일 배치 | 🤖 AI | ✅ |
| GH Secret `AWS_DEPLOY_ROLE_ARN`(=ci_deploy_role_arn) 주입 | 🙋 사람 | ✋ AI 시크릿 read T0 영구차단 |
| PR 머지 (G6 permitAll 보안승인 포함) | 🙋 사람 | ✋ |
| `workflow_dispatch` 첫 배포 | ◑ | 사람이 트리거 열면 AI 진행 |
| 부하측정 → `terraform destroy` | 🤖 AI | ✅ |

## 로깅 하네스 (2026-07-04 신설, 미검증 — 동작 검증은 실제 런 때)

- **`infra/terraform/run-log.sh`** (커밋 대상, 시크릿 0): `init`→런디렉토리, `mark <phase>`→타임라인, `tf-apply`/`tf-destroy`→시간·outputs·비용원장, `ec2 <EIP> [label]`→SSH 메모리/swap/디스크/docker stats 스냅샷(컷오버 땐 `label=cutover`, §9.4 핵심), `cost`→원장×서울요율 추정.
- **raw 로그** → `infra/terraform/run-logs/<UTC>/` (**gitignore** — EIP/endpoint 평문). **큐레이션** → `docs/benchmark/milestone-4-run-report.md`(커밋 템플릿, 5축: 시간·비용·용량·메모리·부하).
- ⚠️ **비용은 실시간 정확값 없음**(Cost Explorer 24h+ 지연) → `cost` 는 리소스-시간 추정, 다음날 실제 대조 필수.

## 남은 단계 — 소유권 체크리스트 (정본, apply부터)

> **이 M4 남은 작업의 단일 정본.** 구 `m4-remaining-checklist.md` 를 여기로 통합(2026-07-04).
> 자격: 매 세션 `source infra/terraform/tf-assume.sh`(MFA, 1h). 절차 정본 = `infra/terraform/README.md` · spec-04 §2.
> 착수 경계 = spec-01 §4.2 유실률 0% before/after 부하검증 *실행* 직전(그 측정이 본 작업).
> 태그: **[AI]** 내가 실행 / **[사람]** 사용자만(비가역·권한·승인) / **[사람→AI]** 사람이 열어주면 AI / **[CI]** 워크플로. 비가역 ☐ · 가역 ◑. 📊 = `run-log.sh` 텔레메트리 훅. 시크릿 커밋 절대 금지.
> **apply 의존성 마커:** 🟢**[apply-전 가능]** = EIP·RDS엔드포인트 무의존 → 지금도 주입 가능. 🔴**[apply-후 강제]** = apply *출력*(EIP·엔드포인트) 또는 EC2 호스트 존재에 의존 → apply 전엔 불가.

### A. 코드 랜딩 + 신원·결제 (G1 — 대부분 완료)
- [ ] **[AI]** M4 브랜치 push + PR 생성 — default 브랜치에 워크플로 있어야 `workflow_dispatch` 가능
- [ ] **[사람]** PR 리뷰·머지 — **G6 보안표면**(SecurityConfig `/api/v1/health` permitAll 확대) 승인 포함
- [x] **[사람] ☐** AWS 계정 + 카드(2026-07-03) · 부트스트랩(root MFA·Budgets·IAM assume-role) 완료 — 상세 spec-04 §2 G1
- [ ] **[사람] ☐** DockerHub 계정·토큰 확보 (현행 레지스트리 유지)

### B. 시크릿 발급·주입 (G2 — 사람만; AI는 템플릿만)
- [x] **[AI]** `gh secret set` + 비커밋 compose env 템플릿 — `m4-secrets-and-env-template.md`
- [x] **[사람→AI]** SSH 키페어 `~/.ssh/mmt-ec2`(ed25519) + terraform `aws_key_pair.app` 등록
- [ ] 🟢**[apply-전 가능]** **[사람] ☐** GH Secrets 주입: `DOCKERHUB_*` · `COMPOSE_NET`(variable). **배포 채널이 SSM(OIDC)로 바뀌어 `EC2_*` SSH 시크릿은 배포에 불필요**(ADR 0008 D5) → 폐기 대상. static `AWS_ACCESS_KEY_ID`/`SECRET` 은 web-ci-cd-to-s3 가 아직 써서 존치. AI 시크릿 read T0 영구차단
- [ ] 🔴**[apply-후 강제]** **[사람] ☐** GH Secret `AWS_DEPLOY_ROLE_ARN` = terraform 출력 `ci_deploy_role_arn` — role ARN 이 apply 출력이라 C에서. (OIDC deploy job 이 read)
- [ ] 🔴**[apply-후 강제]** **[사람→AI] ☐** 백엔드 env-file 배치(호스트 `/home/ec2-user/mmt-backend.env`, `chmod 600`): `RDS_HOST`=apply 출력 엔드포인트 · RDS 자격 · Redis · OAuth · JWT. **호스트·엔드포인트가 apply 산물** → apply 뒤에만 완성(값 자체는 미리 준비 가능). 더미 `GDB_*`·`use-mysql-cte`·`mem_limit`는 스크립트가 inline 주입(env-file에 넣지 말 것). 템플릿 `m4-secrets-and-env-template.md` §2

### C. Infra apply (G3 — ⚠️ 여기부터 과금·크레딧 차감)
> C 이하는 정의상 전부 🔴 apply-후(plan 승인·apply 자체 제외). B의 🔴 두 항목(`AWS_DEPLOY_ROLE_ARN`·백엔드 env-file)이 여기서 실제 완성된다.
- [x] **[사람→AI]** Terraform Phase B `plan` 성공(2026-07-03, 12 리소스)
- [ ] **[사람] ☐** plan diff 승인 (**G3 게이트** — apply 는 과금이라 사람 GO)
- [ ] ⚠️ **[사람→AI]** `terraform apply` → 12 리소스(RDS ~10분). 📊 `run-log.sh init` → `tf-apply`. outputs EIP·`rds_endpoint` 확보.
      user_data 가 부팅 시 2GB 스왑 + Docker/compose + `/etc/mmt/backend.env` 자동설치(커밋 `476c7df`, 실패 시 `/var/log/cloud-init-output.log`)
- [ ] **[AI] ◑** RDS 시드 — M2 스키마·**인덱스·시드**(R4). ⚠️ 누락 시 CTE 비정상. **EC2 앱 띄우기 전에.** ([Infra/Data] 시드 백로그와 통합)
- [ ] **[사람→AI] ◑** EC2 초기화 확인(user_data 자동 — swap on·docker up) + `deploy/active-backend.conf`·`switch-backend.sh`·비커밋 `docker-compose.yml`(front+redis, R3 같은 네트워크) 배치(SSH `~/.ssh/mmt-ec2`). 📊 `run-log.sh ec2 <EIP> init`
- [ ] **[사람] ☐** `AWS_DEPLOY_ROLE_ARN`(=terraform 출력 `ci_deploy_role_arn`) GH Secret 주입 (B의 미룬 항목; OIDC deploy job 이 read)
- [ ] **[사람→AI] ◑** G3 재개 검증: RDS 연결 · 대표쿼리 `EXPLAIN` 복합 인덱스 사용 · swap on

### D. 첫 배포 + grader vet (§4 실행 직전까지)
- [ ] **[CI/사람→AI]** 최초 1회 `workflow_dispatch` → blue-green 부트스트랩(blue 기동) 확인
- [ ] **[사람]** smoke grader 기대 shape 1회 vet — `concepts` non-empty(단순 200 아님, R4)
- [ ] **[AI]** smoke grader green 확인 (R1 기동 + R4 데이터)
- [ ] **[사람]** 유실 grader 페어니스 설계 1회 vet — §4.1(대표 엔드포인트·Before/After 고정·sub-ms `/health` 단독 거부)
- [ ] **[AI]** 부하도구(hey/k6) 스크립트 + Before 하니스(단일 백엔드 stop/rm→run) 준비

### ▶ 경계: §4.2 유실률 0% before/after 부하검증 *실행*
steady-state 200 게이트 → Before(단일 재기동, 502 버스트 기준선) → After(blue-green, 유실 0) → 비교·클레임. 📊 컷오버 순간 `run-log.sh ec2 <EIP> cutover`(2 JVM 공존 메모리) + 부하 JSON. 측정 끝나면 📊 `run-log.sh tf-destroy`(크레딧 방어). G4 유실 grader 가 컷오버 윈도우 green → G5 컷오버 GO. HTTP 80, 대표 GET `http_req_failed==0`.

**가장 먼저 막힌 의존성:** ~~AWS 계정+카드(G1)~~ ✅ 해소(2026-07-03). 실질 다음 = **C의 apply diff 승인 → apply**(사람 GO).

### ⚠️ 실험 종료 시 teardown (destroy 와 함께 회수)
- **`terraform destroy`** 후 인프라 0 확인 (크레딧 방어 — EIP IPv4 상시 차감 정지).
- **`.claude/settings.local.json` 의 ssh/scp allow 룰 2개 회수** — `Bash(ssh -i ~/.ssh/mmt-ec2*)`·`Bash(scp -i ~/.ssh/mmt-ec2*)` (2026-07-05 실험용으로 임시 추가, 사용자 지시로 종료 시 제거).
- 스크래치패드 임시자격(`tfcreds.env`)·EC2 시드 잔여 파일은 destroy 로 EC2 소멸 시 함께 사라짐(로컬 tfcreds 는 세션 스크래치패드라 자동 정리).

### 프론트(web) 배포 전제 (이월, 2026-06-23)
- [ ] **[AI/사람]** `web/src/composables/api.js` `baseURL='http://localhost:8080'` 하드코딩 → 환경변수(.env/Vite). prod 값은 nginx 프록시 토폴로지 종속. **ADR 필요**(web/CLAUDE.md). 무중단 배포와 독립이나 프론트 prod 빌드 전 선결.
