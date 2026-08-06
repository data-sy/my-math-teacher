# [Infra/Deploy] CI 빌드 백엔드 이미지가 프로덕션 부팅 불가 — application-secure.yml 매핑 소실

**등록:** 2026-07-27 (M7 배포 마감 세션에서 A1 blue-green 다크 배포 시도 중 발견) · **분류:** 선재 배포 결함(M7 코드와 무관)

> ## ✅ 근본 원인 해소 완료 (2026-07-27) — 아래 "미착수" 서술은 stale, 본 블록으로 정정 (2026-08-06 실측)
>
> 배선 소실은 **`f432c53` "fix(api): restore production wiring as profile-gated tracked config"** 로
> 고쳐졌다(= 아래 수정 옵션 **1a** 채택). 결정 정본 = [ADR-0013](../adr/0013-restore-production-wiring-as-profile-gated-tracked-config.md) **Accepted**.
> 배선은 `application.yml` 안 `spring.config.activate.on-profile: secure` 멀티도큐먼트로 들어갔고
> **전부 `${ENV}` placeholder — 리터럴 시크릿 0**, 시크릿-보유 파일명(`application-secure*.yml`)은 재도입하지 않았다.
>
> **라이브 증명:** 현재 프로덕션 백엔드 `mmt2024/mmt-backend:ea94a1a…` 는 **CI 가 빌드한 이미지**이고
> `f432c53` 를 조상으로 포함한다(`git merge-base --is-ancestor` 확인) — 즉 **CI 이미지가 프로덕션에서 실제로 부팅 중**이다.
> ⚠️ "프로덕션은 수동 빌드 이미지라 문제가 가려져 있다"는 서술은 **틀렸다**(2026-08-06 정정).
>
> **CI 무중단배포 정합(아래 §함께 처리)도 대부분 해소:** repo variable `COMPOSE_NET=mmt-net` 설정됨(2026-07-27) ·
> `FRAGMENT_HOST_FILE` 기본값이 `/home/ec2-user/active-backend.conf` 로 워크플로에 명시됨(`889390a`) ·
> `deploy/switch-backend.sh`·`active-backend.conf` 리포에 존재. **CI 전체 성공 이력 = 2026-07-27·07-28 2회.**
>
> ### 🔵 남은 것 하나 — deploy job(SSM)이 **재런치된 인스턴스에서** 도는지 미증명
>
> 2026-08-05 실행([run 30972873738](https://github.com/data-sy/my-math-teacher/actions/runs/30972873738))은
> **build-and-push 성공 · deploy 실패**:
> ```
> ::error::SSM 이 대상 인스턴스를 찾지 못함(Project=mmt running 인스턴스 부재?)
> ```
> **⚠️ "SSM 등록 전 타이밍" 가설은 기각됐다 (2026-08-06 실측).** 대기를 30초→2분으로 올리고(`e1d2e95`)
> 재실행한 [run 31078404015](https://github.com/data-sy/my-math-teacher/actions/runs/31078404015) 도
> **build 성공 · deploy 동일 실패** — 하루 넘게 떠 있는 인스턴스이므로 에이전트 등록 지연이 아니다.
> ### 🟢 SSM 복구 완료 (2026-08-07) — 남은 것은 CD 1회 실증뿐
>
> | 층 | 상태 |
> |---|---|
> | SG SSH 인그레스(공인 IP 드리프트) | ✅ 해소 — `sync-my-ip.sh --apply` |
> | SSM 에이전트 | ✅ 해소 — **minimal AMI 라 미설치였다** → `dnf install` + enable, `PingStatus=Online` 확인(`i-098e63bf15a150633`) |
> | CD deploy job 실증 | ⏳ **미완 — GitHub Actions 장애로 막힘** |
>
> 2026-08-07 재실행 [run 31117744990](https://github.com/data-sy/my-math-teacher/actions/runs/31117744990) 은
> `The job was not acquired by Runner of type hosted` 로 **build 단계에서 실패**했고(deploy 는 시작도 못 함),
> 곧이은 재-dispatch 는 `HTTP 500`. githubstatus 확인 결과 **Actions `partial_outage` + 인시던트 investigating**.
> **우리 쪽 원인이 아니다** — 장애가 걷힌 뒤 아래 명령 한 줄로 재실행하면 된다:
> ```bash
> gh workflow run api-ci-cd-with-ec2.yml --ref feat/m7-item-selection -f skip_tests=true
> ```
> 재발 방지(=애초에 minimal 을 집게 만든 AMI 필터)는 [별도 백로그](ami-filter-picks-minimal-no-ssm-agent.md)로 분리했다.
>
> ### ✅ 진단 완료 (2026-08-06, `ssm-deploy-diagnose.sh` 실행 결과)
>
> | 후보 | 실측 |
> |---|---|
> | Project=mmt 태그 누락 | ❌ 아님 — 인스턴스 `i-098e63bf15a150633`(15.164.145.106)에 태그 정상 |
> | IAM instance profile 미부착 | ❌ 아님 — `mmt-ec2-ssm-profile` 부착 확인 |
> | SSM 등록 | ⚠️ **전무** — `describe-instance-information` 이 빈 목록. `ConnectionLost` 가 아니라 **한 번도 등록된 적 없음** |
> | 호스트 SSH(22) | ⚠️ **timeout** |
>
> **SSH timeout 의 원인은 확정됐다:** `network.tf:51-58` 의 SSH 인그레스가 `${var.my_ip}/32`("SSH from my IP only")
> 인데 **현재 공인 IP 가 `terraform.tfvars` 의 `my_ip` 와 다르다**(2026-08-06 대조 확인). 즉 SG 가 지금 IP 를 막고 있다.
>
> **SSM 미등록은 네트워크·이미지 문제가 아니다:** egress 는 전부 허용(`-1`, `0.0.0.0/0` — `network.tf:65-70`)이고
> AMI 는 AL2023(에이전트 기본 탑재·활성). 남은 유력 가설은 **instance profile 이 부팅 이후 attach 돼
> 에이전트가 자격증명 없이 뜬 상태**다 — `compute.tf:70` 이 스스로 *"attach 는 in-place 업데이트(교체 아님)"* 라고 적어 둔
> 바로 그 경로다. 확인·복구하려면 **SSH 가 먼저 열려야 한다.**
>
> **다음 한 걸음(사람 몫 — MFA 필요). 순서가 중요하다 — SSH 가 열려야 에이전트를 만질 수 있다:**
> ```bash
> bash ~/sync-my-ip.sh          # ① 진단(변경 0) → 확인 후 --apply 로 SG 규칙 1건 적용
> bash ~/ssm-recover.sh         # ② SSH 열린 뒤 에이전트 점검 → --apply 로 재기동·등록 폴링
> ```
> 두 스크립트 모두 기본은 **읽기 전용**이고 `--apply` 로만 바꾼다. 홈에 이미 배치돼 있다.
>
> ⚠️ **판정 기준은 tfvars 가 아니라 "SSH 가 실제로 되는가" 다.** `terraform.tfvars` 는 희망 상태일 뿐이라
> apply 전에는 AWS 의 SG 와 다를 수 있다 — **2026-08-07 에 실제로 이 드리프트가 났다**(tfvars 만 새 IP,
> SG 는 옛 IP → SSH 계속 막힘). tfvars 만 보고 "동기화됨"으로 판정하면 거짓 초록이 된다.
>
> ⚠️ `my_ip` 는 **IP 가 바뀔 때마다 재발**한다. 재발 시 `sync-my-ip.sh` 를 다시 돌리면 되고,
> 구조적 제거는 [SSM Session Manager 이관 백로그](ssh-ingress-ip-pinning-to-session-manager.md)로 분리했다.
>
> **프로덕션 서빙에는 영향 없음** — 라이브는 정상이고 막힌 것은 "CI 로 배포하는 경로"뿐이다(현재는 수동 배포로 우회 가능).

> **한 줄:** 시크릿 유출 대응(`c7f608d`)이 `application-secure.yml` 을 **추적 해제**하면서, 그 안의 **비밀 아닌 env 매핑 구조까지** 사라졌다. 그 이후 CI 가 빌드하는 백엔드 이미지는 datasource 설정이 없어 프로덕션에서 **부팅 실패**한다. M7 이 그 조치 이후 첫 CI 백엔드 배포라 이때 처음 표면화.

## 증상

`api-ci-cd-with-ec2.yml` 이 빌드·push 한 신 이미지(`mmt2024/mmt-backend:<sha>`)로 blue-green green 슬롯 기동 시:

```
APPLICATION FAILED TO START
Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.
Reason: Failed to determine a suitable driver class
The following 2 profiles are active: "securelocal", "secure"
```

blue-green 헬스 게이트가 컷오버 *전* 에 잡아 **abort no-op → 프로덕션 영향 0**(구 blue 그대로 서빙). switch-backend.sh 가 설계대로 fail-safe.

## 근본 원인 (git 히스토리로 확정)

1. 프로덕션 datasource/redis/neo4j/oauth/jwt/cors 는 `RDS_*`·`REDIS_*`·`GOOGLE_*`·`JWT_SECRET` 등 **env 변수를 spring 프로퍼티로 조립하는 매핑**이 `application-secure.yml` 안에 있다. env-file(`~/mmt-backend.env`)은 **값만** 주고 `SPRING_DATASOURCE_URL` 같은 완성 키는 없다. `application.yml` 의 `spring.profiles.include: securelocal` 도 CI 이미지엔 `application-securelocal.yml` 이 없어 무의미.
2. 이 파일은 원래 **추적됐다**. 현 prod `mmt2024/mmt-backend:47063986…`(blue) 커밋 트리에 `application-secure.yml` blob 존재(`10ecb485…`) → 그 시절 CI 가 체크아웃할 때 딸려와 **이미지에 구워짐** → 프로덕션 부팅 OK. **그게 "그 전엔 어떻게 배포됐나"의 답.**
3. 커밋 **`c7f608d`** "fix(security): 프로덕션 시크릿 application-secure.yml 추적 해제 + 로테이션 런북" 이 2026-07 유출 대응으로 이 파일을 **추적 해제 + `.gitignore`(`api/src/main/resources/application-secure*.yml`)**.
4. 그 이후 CI 빌드 이미지는 secure.yml 없음 → 부팅 불가. **보안 조치가 배포 파이프라인을 조용히 깨뜨린** 케이스.

**중요 단서:** 추적됐던 `application-secure.yml`(blob `10ecb485`, `c7f608d^`)은 **전부 `${ENV}` 플레이스홀더 기반**이다(`url: jdbc:mysql://${RDS_HOST}:${RDS_PORT}/${RDS_NAME}?serverTimezone=…`, `username: ${RDS_USERNAME}`, redis `host: ${REDIS_URL}`, neo4j `bolt://${GDB_URL}`, oauth `client-id: ${GOOGLE_CLIENT_ID}` …). [`production-deploy-live-resume-link.md`](production-deploy-live-resume-link.md) §9 도 "추적되던 파일은 `${ENV}` 플레이스홀더, 공개된 건 이미 대체된 옛 리터럴 3종뿐" 으로 확인. → **매핑 복구는 안전**(리터럴 시크릿 재커밋 없이 placeholder-only 로 복구 가능).

## 수정 옵션 (`/analyze-before-change` + ADR 대상 — datasource/redis/oauth 해석을 건드림)

1. **매핑을 추적되는 tracked 설정으로 이관 (권장, 재현 가능).** `10ecb485` 의 placeholder-only 매핑을 되살리되 시크릿-보유 파일명(`application-secure*.yml`, gitignore 대상)을 피한다. 방법 후보:
   - (1a) 매핑을 **`application.yml`**(이미 tracked)에 통합 — 값은 `${ENV}` placeholder, 리터럴 0.
   - (1b) **placeholder-only 신규 tracked 파일**(예: `application-prod-wiring.yml` 또는 secure 프로파일을 tracked 로 재도입하되 `.gitignore` 제외 규칙 추가) — 리터럴 절대 금지, PR 리뷰에서 literal 스캔.
   - 어느 쪽이든 **실제 값은 계속 env-file** 에만. 재커밋 전 `git grep`/훅으로 리터럴 시크릿 0 확인.
2. **CI 빌드 시 GitHub Secret 으로 secure.yml 주입.** 현 구조 유지, 빌드 스텝에서 파일 생성. secret 관리 오버헤드 + 비직관.
3. **로컬 빌드로 우회(비권장).** secure.yml 있는 환경에서 docker build → push. 오늘 당장은 되나 비재현 로컬 빌드 패턴 재도입(이 결함의 원인). **현 dev 머신엔 secure.yml 부재** — blue 이미지에서 꺼내야 해서 이것도 즉시 불가.

**권장 = 1(placeholder 매핑 tracked 화) + ADR.** 원본은 `git show 10ecb485…` 로 복구(값 마스킹, 리터럴이면 대응 `${ENV}` 로 치환).

## 함께 처리할 CI 무중단배포 정합 (이번 세션에서 실측된 호스트-워크플로 불일치)

`api-ci-cd-with-ec2.yml` deploy job 이 **현 프로덕션 호스트**(EC2 `i-0eb170169ac70ee05`)에서 그대로 돌면 실패한다. 실측 불일치:

- **`~/deploy/` 자산 부재** → deploy job 이 `bash deploy/switch-backend.sh` 를 못 찾음(`exit 127`). *(이번 세션에 `~/deploy/switch-backend.sh` 수동 배치함 — md5 `fb24eb01a5e3177c15ee11120271b1cd`, `bash -n` OK. 단 리포↔호스트 동기가 아니라 1회성. 영구화 필요.)*
- **`COMPOSE_NET`** 워크플로 기본값 `ec2-user_default` ≠ 호스트 실제 `mmt-net`. repo **variable `COMPOSE_NET=mmt-net`** 설정 필요.
- **`FRAGMENT_HOST_FILE`** — 스크립트 기본 `$SCRIPT_DIR/active-backend.conf`(=`~/deploy/active-backend.conf`) ≠ nginx 가 실제 마운트하는 `~/active-backend.conf`. 워크플로가 `FRAGMENT_HOST_FILE=/home/ec2-user/active-backend.conf` 를 넘기거나, 자산 배치를 `~/active-backend.conf` 기준으로.
- **IMAGE_REPO** = `mmt2024/mmt-backend`(org 아님) — `secrets.DOCKERHUB_USERNAME=mmt2024` 전제와 정합 확인.
- 이 3~4건은 [`production-deploy-live-resume-link.md`](production-deploy-live-resume-link.md) §9 "CI 정상 무중단배포 정합" 과 동일 — 그 항목과 합쳐 처리.

## 참고 (이번 세션 실측 스냅샷)

- 프로덕션 호스트: EC2 `i-0eb170169ac70ee05`(ap-northeast-2, tag `Project=mmt`). 네트워크 `mmt-net`. 컨테이너: `mmt-backend-blue`(=`mmt2024/mmt-backend:47063986…`, 현 활성)·`mmt-front`(=`mmt-front:m6`, **호스트 로컬 빌드**)·`mmt-redis`·`mmt-ai`. fragment `~/active-backend.conf` = `server mmt-backend-blue:8080;`.
- env-file 키: `RDS_HOST/NAME/PORT/USERNAME/PASSWORD`·`REDIS_URL/PORT/PASSWORD`·`GOOGLE/NAVER/KAKAO_CLIENT_ID/SECRET`·`JWT_SECRET`·`EC2_DOMAIN_NAME1/2`. `MMT_DIAGNOSIS_*` 미설정(→ 다크 배포 OK).
- push 된 broken 이미지: `mmt2024/mmt-backend:2859b9e1bd91ac18fff7b88aa273e08eaac34fd6`(secure.yml 없음 = 부팅 불가). 수정 후 재빌드 시 새 sha 태그로 대체 — 이 태그는 폐기(선택: Docker Hub 에서 삭제).
- 메모리: `project_ci_image_missing_secure_yml`. M4 배포 인프라 맥락: [`../milestones/milestone-4-zero-downtime-deployment.md`](../milestones/milestone-4-zero-downtime-deployment.md), 유출 대응: [`../specs/m6/oauth-and-secret-rotation-runbook.md`](../specs/m6/oauth-and-secret-rotation-runbook.md).
