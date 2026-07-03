# infra/terraform/provider.tf
#
# 이 provider 는 var.use_localstack 토글로 두 페이즈를 공존시킨다:
#   Phase A (기본, use_localstack=true): LocalStack 타깃 — 무계정·무과금.
#     학습 목표 = validate→plan→apply→destroy 사이클 + 리소스 그래프.
#     AWS 정합이 목표가 아님 → LocalStack green ≠ AWS 기동.
#   Phase B (use_localstack=false): 실제 AWS — mmt-admin role 을 MFA 로 assume 한
#     임시자격으로 real plan/apply(과금·MFA). Terraform 은 프로필의 MFA assume 를
#     대화형 처리 못 하므로(aws-sdk 제약), `source infra/terraform/tf-assume.sh` 로
#     임시자격을 AWS_* env 에 주입해 사용(장기 admin 키 커밋 0, 보안 모델 동일).

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0" # 정확한 patch 핀은 .terraform.lock.hcl 이 소유 → init 후 lock 파일 커밋
    }
  }

  # 의도적으로 local state. Phase A 에 remote backend 없음
  # (기본 local backend, terraform.tfstate 는 infra/terraform/ 아래에 남음)
}

provider "aws" {
  region = "ap-northeast-2" # 실제 AWS 타깃 리전. LocalStack mock 에는 무의미

  # Phase B(real): 자격은 ambient 자격 체인(환경변수)에서 온다 — provider 에
  #   profile 을 박지 않는다. Terraform 은 shared-config 프로필의 MFA assume-role 를
  #   대화형 처리 못 하기 때문("AssumeRoleTokenProvider session option not set").
  #   대신 `source infra/terraform/tf-assume.sh` 로 mmt-admin role 을 MFA assume 해
  #   AWS_ACCESS_KEY_ID/SECRET/SESSION_TOKEN 를 셸에 주입한 뒤 plan/apply.
  #   보안 모델 동일: IAM user + MFA assume-role 임시자격, 장기 admin 키 0.
  # Phase A(LocalStack): 아래 더미 access/secret key 로 fallback.
  access_key = var.use_localstack ? "test" : null
  secret_key = var.use_localstack ? "test" : null

  # 실제 AWS 메타데이터/IAM 자동 호출을 Phase A 에서만 끈다. Phase B 는 real 검증.
  skip_credentials_validation = var.use_localstack
  skip_metadata_api_check     = var.use_localstack
  skip_requesting_account_id  = var.use_localstack

  # 모든 리소스에 Project=mmt 태깅(비용 추적·정리). 양 페이즈 공통.
  default_tags {
    tags = {
      Project = "mmt"
    }
  }

  # Phase A 전용: 닿는 서비스만 LocalStack 엣지(단일 포트 4566)로 라우팅.
  #   ec2 = network.tf(SG)·compute.tf(EC2/EIP). rds = database.tf.
  #   ⚠️ community LocalStack 은 RDS 미지원 → rds 는 plan/validate 까지만 신뢰(R-T1).
  # Phase B: endpoints 블록 자체를 비워 real AWS 로 나간다.
  dynamic "endpoints" {
    for_each = var.use_localstack ? [1] : []
    content {
      sts = "http://localhost:4566"
      ec2 = "http://localhost:4566"
      rds = "http://localhost:4566"
    }
  }
}

# --- 1-touch 배선 probe ---------------------------------------------------
# read-only. plan 단계에서 실제 STS GetCallerIdentity 를 엣지로 쏜다.
# 이게 "문법 OK"를 "왕복이 LocalStack 에 닿음"으로 바꾸는 지점.
data "aws_caller_identity" "current" {}

output "caller_identity_account" {
  description = "LocalStack 은 000000000000 을 반환 — STS 엔드포인트가 응답했다는 증거"
  value       = data.aws_caller_identity.current.account_id
}

output "caller_identity_arn" {
  value = data.aws_caller_identity.current.arn
}
