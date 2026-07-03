# MMT M4 인프라 — Terraform 런북

단일 EC2 blue-green 배포용 AWS 인프라(EC2·EIP·SG·RDS)를 Terraform 으로 관리한다.
두 페이즈를 `var.use_localstack` 토글로 공존시킨다.

| 페이즈 | `use_localstack` | 타깃 | 과금 |
|---|---|---|---|
| **A** | `true`(기본) | LocalStack mock(`localhost:4566`) | 없음 — IaC 사이클 학습용 |
| **B** | `false` | 실제 AWS(`ap-northeast-2`) | apply 부터 과금 |

> ⚠️ 시크릿(`db_password`, `~/.aws` 자격, `*.tfstate`, `*.tfvars`)은 **커밋 금지**. `.gitignore` 로 차단돼 있다.

---

## 1. 사전조건 (1회성)

**AWS 프로필** — `~/.aws/config`(정적 키는 `~/.aws/credentials`, 커밋 안 됨):

```ini
[profile mmt-base]                 # IAM user mmt-cli 정적 키(sts:AssumeRole 전용)
region = ap-northeast-2

[profile mmt-admin]                # mmt-base 로 admin role 을 MFA assume
role_arn         = arn:aws:iam::<ACCOUNT_ID>:role/mmt-terraform-admin
source_profile   = mmt-base
mfa_serial       = arn:aws:iam::<ACCOUNT_ID>:mfa/mmt-cli
region           = ap-northeast-2
duration_seconds = 3600
```

검증: `aws sts get-caller-identity --profile mmt-admin` → MFA 입력 → `assumed-role/mmt-terraform-admin` 반환.

**비커밋 `terraform.tfvars`** (gitignored):

```hcl
use_localstack = false
my_ip          = "<현재 공인 IP>"    # curl -s https://checkip.amazonaws.com
db_password    = "<RDS 마스터 비번>"  # 8~41자, / @ " 공백 불가. 비밀번호 관리자에도 백업
```

---

## 2. Phase B 자격 획득 (매 세션 / 1시간마다)

Terraform 의 AWS provider 는 shared-config 프로필의 MFA assume-role 를 **대화형 처리하지 못한다**
(`AssumeRoleTokenProvider session option not set`). 그래서 CLI 로 role 을 assume 해 임시자격을
환경변수로 넘긴다:

```bash
cd infra/terraform
source tf-assume.sh      # MFA 코드 입력 → AWS_ACCESS_KEY_ID/SECRET/SESSION_TOKEN export (1h)
```

- 반드시 `source` (그냥 실행하면 export 가 현재 셸에 안 남는다).
- 만료(1h) 시 재-`source`. role 최대 세션을 늘리려면 roadmap 백로그 "세션 8h 연장" 참고.
- 보안 모델: 장기 admin 키 없음 — MFA 로 assume 한 임시자격만 사용.

---

## 3. plan / apply / destroy

```bash
terraform plan                       # tfvars 자동 로드. 9 리소스 create 예상
terraform apply                      # ⚠️ 과금 시작. 승인(G3) 후에만
terraform destroy                    # 안 쓸 땐 반드시 — 요금·크레딧 방어
```

기대 리소스(12): `aws_instance.app`(+`aws_key_pair.app`) · `aws_eip.app`(+association) ·
`aws_security_group.app` + ingress 80/443/22(내 IP) · egress all · `aws_db_instance.app`(MySQL 8,
db.t3.micro) + 전용 `aws_security_group.db` + 3306 ingress(app SG 출발지만).
모든 리소스에 `Project=mmt`(default_tags).

Phase A(LocalStack) 로 돌리려면: `terraform plan -var use_localstack=true`
(또는 tfvars 값을 바꾼다). LocalStack 은 RDS 미지원 → RDS 는 plan/validate 까지만.

---

## 4. 시크릿 관리

| 대상 | 어디에 | 비고 |
|---|---|---|
| `db_password`(RDS 마스터) | `terraform.tfvars`(로컬) + **비밀번호 관리자**(정본 백업) | GitHub Secrets 아님 — 배포는 DB 자격을 CI 로 안 넘긴다 |
| 백엔드 런타임 DB 자격 | EC2 호스트의 **비커밋 compose env**(C단계 SSH 로 작성) | tfvars 와 같은 값 |
| AWS 자격 | `~/.aws` 로컬 프로필만 | provider 는 env 임시자격 사용, 정적 키 커밋 0 |
| `*.tfstate`(비번 평문 포함) | 로컬, gitignored | 백업 시에도 암호화 저장 |

---

## 5. 알려진 후속 (apply 후 사용성까지 남은 것)

- ~~EC2 키페어~~ · ~~RDS 3306 SG 룰~~ — **배선 완료**(`aws_key_pair.app`, 전용 `aws_security_group.db` + 3306).
  개인키는 로컬 `~/.ssh/mmt-ec2`. apply 후 `ssh -i ~/.ssh/mmt-ec2 ec2-user@<EIP>` 로 접속.
- **EC2 초기화** — apply 후 EC2 는 bare AL2023(Docker/compose·2GB 스왑 미설치, user_data 는 env 파일만).
  SSH 로 초기화 필요(spec-04 C).
- **RDS 스키마·시드(R4)** — apply 후 RDS 는 빈 상태. M2 스키마·인덱스·시드 적재해야 CTE 정상.
- **HTTPS(R2)** — 무중단 검증은 HTTP 80 으로. 도메인·인증서는 후속(범위 밖).

apply=인프라 생성이지 서비스 기동이 아니다.
