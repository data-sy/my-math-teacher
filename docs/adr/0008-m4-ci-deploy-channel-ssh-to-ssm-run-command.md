# ADR 0008: M4 CI 배포 채널을 SSH-from-runner → SSM Run Command 로 전환 (CI 인증=GitHub OIDC, SSH 룰=내 IP 존치)

## Status

Proposed

## Context

ADR 0007(blue-green 무중단 배포)로 배포 *메커니즘*(EC2 위 `switch-backend.sh` 색 전환)은 확정됐으나, **CI 러너가 그 스크립트를 EC2 에서 어떻게 트리거하는가**(배포 채널)는 SSH 로 남아 있었다. M4 첫 배포 시도(2026-07-05)에서 이 채널이 실패했다:

1. **SG lockdown vs 러너 IP:** deploy job 이 GitHub 호스티드 러너에서 EC2 로 SSH(`appleboy/ssh-action`) 접속한다. 그러나 SG 의 SSH(22) ingress 는 `var.my_ip/32`(내 로컬 IP)로만 열려 있어, Azure 대역(고정 IP 없음)의 러너가 차단돼 `dial tcp i/o timeout` 으로 죽었다.
2. **키 파싱 실패:** `EC2_SSH_KEY` 시크릿이 유효한 private key 로 파싱되지 않아(`ssh: no key found`) 별도로도 실패했다.

SSH 를 러너에 열어주는 것(`0.0.0.0/0` + 키전용)은 안티패턴이고, GitHub 호스티드 러너는 IP 대역이 방대·변동해 SG 룰 수 제한상 화이트리스트가 불가하다. 사용자 목표는 "실무 정석대로".

**토폴로지 제약(정직한 정리):** "GH Actions → EC2 SSH → docker run" 은 소규모/단일 VM 레거시 패턴이다. 진짜 무중단 정석은 한 층 위(ALB + Target Group + ASG/CodeDeploy, 또는 ECS/EKS)로, 인스턴스 SSH 자체가 불필요하다. 우리 M4 의 "단일 EC2 + nginx 가 blue/green 전환"은 이미 포트폴리오용 단순화이며, 완전 정석(ALB/ECS)은 spec-01 전체 재설계라 M4 범위 밖이다. **그 단일-EC2 토폴로지를 유지하면서 SSH 를 안 여는 정석 = SSM(Systems Manager) Run Command** 다. AWS 권장(키·포트 관리 제거)이며 실무에서 실제로 쓴다. 이 결정은 "무중단의 정석"을 바꾸는 게 아니라, 확정된 단일-EC2 토폴로지 안에서 **CI→EC2 명령 채널만** 교체한다.

관련 사실(2026-07-05 조사):
- AL2023 은 `amazon-ssm-agent` 프리인스톨·활성 → EC2 에 IAM instance profile 만 붙이면 SSM 에 등록된다. 아웃바운드 443 만 필요하고, egress 는 이미 전체 허용 + EIP/IGW 로 공용 SSM 엔드포인트에 도달하므로 VPC endpoint 불필요.
- deploy job 은 `EC2_HOST`/`EC2_USERNAME`/`EC2_SSH_KEY`/`EC2_PORT` 시크릿과 `IMAGE_REPO`/`COMPOSE_NET`/`NEW_TAG` env 로 홈(`~`)에서 `bash deploy/switch-backend.sh "$NEW_TAG"` 를 ec2-user 로 실행한다.
- `switch-backend.sh` 는 `docker`(그룹 권한), 절대경로 env-file(`/home/ec2-user/mmt-backend.env`), `SCRIPT_DIR`(BASH_SOURCE 절대경로 기준)에 의존하고 상대경로(`deploy/switch-backend.sh`)로 호출된다. `COMPOSE_NET` 은 env 로 주입돼 실행 사용자와 무관하다.
- repo 에 2024-06 생성 static `AWS_ACCESS_KEY_ID`/`SECRET` 시크릿이 잔존한다.

## Decision

CI→EC2 배포 채널을 **SSM Run Command** 로 전환한다. SSH 인바운드로의 러너 접속은 제거하되, **내 IP SSH(22) 룰은 존치**한다(수동 운영·재시드용). CI→AWS 인증은 **GitHub OIDC → IAM role assume** 로 한다.

### D1. 배포 채널 = SSM Run Command (SSH-from-runner 제거)

deploy job 의 `appleboy/ssh-action` 을 제거하고 `aws ssm send-command`(document `AWS-RunShellScript`, target=인스턴스 ID 또는 태그 `Project=mmt`)로 `switch-backend.sh` 를 트리거한다.

- send-command 는 **비동기** → 반드시 `aws ssm wait command-executed`(+ `get-command-invocation`)로 exit status·stdout 을 회수해 job 성패를 판정한다. 안 기다리면 항상 green 오탐.
- SSM 명령은 기본 **root** 로 실행되나 `switch-backend.sh` 는 ec2-user 홈의 파일·상대경로·docker 그룹에 의존 → **`runuser -l ec2-user -c 'cd ~ && IMAGE_REPO=... COMPOSE_NET=... NEW_TAG=... bash deploy/switch-backend.sh <sha>'`** 로 ec2-user 정체성·작업디렉토리·docker 그룹을 그대로 태워 실행한다(root 로 직접 돌리지 않는다 — 근원 경로/그룹 가정 유지).

### D2. EC2 SSM 등록 = IAM instance profile (`AmazonSSMManagedInstanceCore`)

`aws_iam_role`(trust=ec2) + `aws_iam_role_policy_attachment`(관리형 `AmazonSSMManagedInstanceCore`) + `aws_iam_instance_profile` 을 신설하고 `aws_instance.app.iam_instance_profile` 로 참조한다. 프로필 attach 는 **인스턴스 in-place 업데이트**(교체 아님)라 replace 를 유발하지 않는다.

### D3. CI→AWS 인증 = GitHub OIDC → IAM role assume (static key 아님)

IAM OIDC provider(`token.actions.githubusercontent.com`) + role 을 신설한다. role trust 는 `repo:data-sy/my-math-teacher:*` 로 스코프하고, 권한은 `ssm:SendCommand`/`ssm:GetCommandInvocation`(+ 필요 시 `ssm:ListCommandInvocations`)을 **대상 인스턴스/문서 리소스로 제한**한다. 워크플로는 `permissions: id-token: write` + `aws-actions/configure-aws-credentials@v4`(role-to-assume)로 단기 자격을 얻는다. repo 잔존 static `AWS_ACCESS_KEY_ID`/`SECRET`(2024-06)은 이 전환 후 **폐기 대상**.

### D4. SG SSH(22) 룰 = 내 IP 존치 (완전 삭제 아님)

CI 는 SSM(포트 0개)로 가되, `network.tf` 의 `aws_vpc_security_group_ingress_rule.ssh`(`var.my_ip/32`)는 남긴다. 근거: 수동 재시드·긴급 운영에서 SSH 가 여전히 편하고, 노출면은 내 IP/32 로 제한돼 안티패턴이 아니다. `.claude/settings.local.json` 의 ssh/scp allow 룰 2개도 이 결정에 따라 존치한다. (관리 접근을 완전히 SSM Session Manager 로 옮기는 것은 실험 완전 종료 시 후속.)

### D5. 폐기 대상 GH 시크릿

`EC2_SSH_KEY`·`EC2_HOST`·`EC2_PORT`·`EC2_USERNAME` 은 SSM 전환 후 배포 채널에서 불필요 → 폐기(D4 의 내 수동 SSH 는 로컬 `~/.ssh/mmt-ec2` 로 하지 GH 시크릿을 안 쓴다).

⚠️ static AWS 키(`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, 2024-06)는 **즉시 폐기 불가** — `web-ci-cd-to-s3` 워크플로가 아직 이 키로 S3 배포에 사용 중이다(조사 2026-07-05). D3 는 M4 api 배포 채널을 OIDC 로 옮길 뿐이고, static 키 완전 폐기는 web 워크플로도 OIDC 로 이관한 뒤로 **이월**한다(범위 밖·후속). 그때까지 static 키는 존치.

## Consequences

### Positive
- SSH 인바운드를 러너에 안 열고 배포 → 안티패턴(0.0.0.0/0 SSH) 제거, AWS 권장 채널 사용. 노출면 감소.
- OIDC 로 CI 에 장기 AWS 키 부재 → 키 로테이션·유출 리스크 제거. trust/권한이 repo·인스턴스·액션으로 스코프됨.
- 단일-EC2 토폴로지·blue-green 메커니즘(ADR 0007) 그대로 유지 — 이번 변경은 채널 한정, 재설계 없음.
- `runuser -l ec2-user` 로 실행 정체성이 SSH 시절과 동일 → `switch-backend.sh` 의 경로/그룹 가정 무변경.

### Negative
- OIDC provider + role + trust 스코프 + instance profile 로 **셋업 스텝이 static 키보다 많다**(초기 IaC 비용). 다만 1회성.
- SSM send-command 비동기 특성상 **wait/결과회수 로직을 정확히 짜야** job 성패가 진짜로 반영된다(누락 시 green 오탐). 워크플로 복잡도↑.
- 내 IP SSH 존치(D4)는 노출면 0 이 아님(내 IP/32). 완전 정석(포트 0개)은 아니며, 실험 종료 시 회수 필요.

### Neutral
- Docker Hub 레지스트리·이미지 태그(immutable sha)·switch-backend.sh 색 전환 로직은 불변 — ADR 0007 결정 유지.
- 배포 채널 교체는 M4 §4 유실률 측정 방법론에 영향 없음(측정은 채널 무관, 전환 동작만 검증).
- SSM Session Manager 관리 접근은 프로필 부착으로 부수적으로 가능해지나(포트 0), 이번엔 배포용으로만 쓰고 운영 SSH 대체는 후속.

## Alternatives Considered

1. **SSH 를 0.0.0.0/0 + 키전용으로 개방** — 기각. 안티패턴(무차별 스캔·brute 표면). 러너 IP 화이트리스트가 불가한 상황을 노출면 확대로 맞바꾸는 것.
2. **bastion / VPN 뒤 SSH** — 기각. 단일-EC2 포트폴리오에 bastion 상시 비용·복잡도 과함. 얻는 게 SSM 대비 없음.
3. **self-hosted runner(고정 IP)** — 기각. 러너 인프라 상시 운영·보안 패치 부담. 단발 배포에 과설계.
4. **ALB + Target Group + (ASG/CodeDeploy) 또는 ECS/EKS** — 기각(범위). 진짜 무중단 정석이자 SSH 불필요하나 spec-01 전체 재설계 → M4 범위 밖. 본 ADR 은 이것이 상위 정석임을 인지만 하고 채널만 교체.
5. **CI 인증에 static access key(2024-06 잔존/신규 IAM user 키) 재사용** — 기각. 간편하나 장기 키 로테이션·유출 부담. OIDC 로 장기 키 자체를 제거하는 편이 정석(D3).
6. **SSM 명령을 root 로 직접 실행** — 기각. `switch-backend.sh` 가 ec2-user 홈 경로·docker 그룹·상대경로에 의존 → root 실행 시 경로/권한 가정이 깨짐. `runuser -l ec2-user` 로 정체성 유지(D1).
7. **SG SSH 룰 완전 삭제(관리도 100% SSM Session Manager)** — 보류. 가장 정석·노출면 최소지만 수동 재시드가 번거로워짐. 실험 종료 시로 이월(D4).

## References

- 선행 ADR: ADR 0007(blue-green 무중단 배포 메커니즘 — 본 ADR 은 그 배포 채널만 교체), ADR 0006(동일 호스트 nginx 토폴로지)
- 적용 spec: `docs/specs/m4/spec-01-zero-downtime-deployment.md` §9.2(SG·SSH 인증 전제 → SSM 으로 갱신), §3.5(deploy job)
- 핵심 파일: `.github/workflows/api-ci-cd-with-ec2.yml`(deploy job), `infra/terraform/network.tf`(SG), `infra/terraform/compute.tf`(instance profile 참조), `deploy/switch-backend.sh`(root/ec2-user 실행 규약)
- 핸드오프: 루트 `m4-ssm-handoff.md`(설계·순서), `m4-resume-prompt.md`(재배포·§4 측정 재개 정본)
- 관련 규칙: 루트 `CLAUDE.md` ADR·마이그레이션·피처플래그 규칙
