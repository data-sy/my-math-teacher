# [Infra] SSH 를 IP 고정 인그레스에서 SSM Session Manager 로 옮기기

**등록:** 2026-08-06 (CD 실패 진단 중 근본 원인으로 드러남) · **상태:** 📌 **미착수 — 착수 대기 상위** ·
**분류:** 운영 위생 / 재발성 결함 제거 · **선행 조건:** SSM 등록이 먼저 정상화돼야 한다(순환 의존)

> **한 줄:** `network.tf` 의 SSH 인그레스가 `${var.my_ip}/32` 라 **공인 IP 가 바뀔 때마다 SSH·배포가 통째로 막힌다.**
> 임시 대응(스크립트로 IP 재동기화)은 있으나 재발이 구조에 내장돼 있다. SSM Session Manager 로 옮기면
> 인그레스 22 규칙 자체를 없앨 수 있다.

## 왜 지금 백로그인가

지금 당장 못 한다 — **SSM 등록이 죽어 있어서** Session Manager 로 갈아타는 순간 접근 수단이 0이 된다.
순서상 [CD 복구](ci-backend-image-missing-secure-yml.md)로 SSM 이 살아난 뒤에 착수해야 한다.

## 증상 (2026-08-06 실측)

- 사이트(80/443)는 정상인데 **SSH 만 timeout** → "서버는 살아있는데 왜 못 붙지"로 오진하기 쉽다.
- 그 결과 SSM 진단·에이전트 재기동 같은 **복구 작업 자체가 막힌다**(이번에 실제로 막혔다).
- CD(GitHub Actions → SSM)도 같이 멈춘 것처럼 보여 원인 분리에 시간이 든다.

## 임시 대응 (이미 있음)

`docs/handoff/scripts/sync-my-ip.sh` — 현재 IP 대조 → `terraform.tfvars` 의 `my_ip` 갱신 →
SG 규칙 1건만 `-target` apply. 기본은 확인만, `--apply` 로만 변경.
**재발할 때마다 사람이 돌려야 한다는 게 이 백로그의 존재 이유다.**

## 할 일 (착수 시)

1. **SSM Session Manager 접속 경로 확정** — `aws ssm start-session --target <id>`. 세션 로깅(S3/CloudWatch) 여부 결정.
2. **SSH 를 쓰는 자산 전수 이관** — `deployfront.sh`(호스트 컨테이너 교체) · `zdbg-cleanup.sh`(RDS 접속) ·
   `ssm-deploy-diagnose.sh` · `ssm-recover.sh` · `sync-my-ip.sh`(자기 자신 소멸). 각각 `ssh` → `ssm start-session`
   또는 `send-command` 로. **RDS 접속처럼 포트 포워딩이 필요한 것**은 `AWS-StartPortForwardingSessionToRemoteHost` 문서 사용.
3. **인그레스 22 규칙 제거** — `network.tf` 의 `aws_vpc_security_group_ingress_rule.ssh` 와 `var.my_ip` 삭제.
   `terraform.tfvars` 에서도 제거.
4. **비상 접근 경로 확보** — SSM 이 다시 죽었을 때의 탈출구. EC2 Serial Console 또는 임시 SG 규칙 추가 절차를 런북에 명시.
   **이게 없으면 이번과 같은 교착이 재발한다.**

## 대안 (기각/보류)

- **terraform `http` data source 로 my_ip 자동 탐지** (`data "http" "myip"` → `checkip.amazonaws.com`):
  6줄이면 되지만 **어디서 apply 하느냐에 따라 SG 가 조용히 바뀐다**. plan 이 비결정적이 되고,
  다른 네트워크에서 apply 하면 이전 IP 가 소리 없이 잠긴다. 명시적 스크립트가 낫다고 판단 — **보류**.
- **SSH 인그레스를 0.0.0.0/0 으로**: 재발은 없어지지만 공개 SSH. **기각**.
- **VPN/베스천**: 1인 프로젝트에 과함. **기각**.

## 규모 감각

SSH 사용처가 5개 스크립트 + 런북이라 **이관이 본체**다. 인프라 변경 자체는 작다(규칙 1건 삭제).
비상 경로 설계(§4)를 빼먹으면 안 된다.
