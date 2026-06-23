# infra/terraform/provider.tf
#
# Phase A 타깃: LocalStack (무계정·무과금).
# 학습 목표 = validate→plan→apply→destroy 사이클 + 리소스 그래프.
# AWS 정합이 목표가 아님 → LocalStack green ≠ AWS 기동.

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
  region = "ap-northeast-2" # 실제 AWS 타깃 리전과 맞춤. LocalStack mock 에는 무의미

  # 더미 크리덴셜 — LocalStack 관례. 실제 크리덴셜은 G1 영역(미룸)
  access_key = "test"
  secret_key = "test"

  # 실제 AWS 메타데이터/IAM 으로 나가는 provider 의 자동 호출들을 끔
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  # 이번 조각이 닿는 서비스만 LocalStack 엣지(단일 포트 4566)로 라우팅.
  # ec2/rds 엔드포인트는 compute.tf / database.tf 들어올 때 여기 추가
  endpoints {
    sts = "http://localhost:4566"
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
