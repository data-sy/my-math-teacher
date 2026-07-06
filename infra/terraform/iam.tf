# infra/terraform/iam.tf
#
# ADR 0008: CI 배포 채널 SSH → SSM Run Command 전환의 IAM 배선.
#   (1) EC2 instance profile — 인스턴스를 SSM 에 등록(AmazonSSMManagedInstanceCore).
#   (2) GitHub OIDC provider + role — CI 러너가 장기 키 없이 단기 자격으로
#       ssm:SendCommand / GetCommandInvocation 만 수행(대상=Project=mmt 인스턴스).
#
# ⚠️ community LocalStack 은 IAM/OIDC 정합이 제한적(R-T 계열) → Phase A 는
#    validate/plan 형태 고정까지만 신뢰. 실 apply·SSM 등록 검증은 Phase B(다음 세션).

# 리전·계정 — SSM/EC2 리소스 ARN 조립용. plan 시점 read(validate 는 미접촉).
data "aws_region" "current" {}

# ─────────────────────────────────────────────────────────────────────────────
# (1) EC2 → SSM 등록용 instance profile
# ─────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2_ssm" {
  name               = "mmt-ec2-ssm-role"
  description        = "EC2 that registers with SSM (ADR 0008)"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json

  tags = {
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# AL2023 의 amazon-ssm-agent 가 SSM 에 등록·명령 수신하는 데 필요한 관리형 정책.
resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2_ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2_ssm" {
  name = "mmt-ec2-ssm-profile"
  role = aws_iam_role.ec2_ssm.name

  tags = {
    Project   = "mmt"
    ManagedBy = "terraform"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# (2) GitHub Actions OIDC → CI 배포 role (ADR 0008 D3)
# ─────────────────────────────────────────────────────────────────────────────

# GitHub 의 OIDC IdP 등록. thumbprint 는 GitHub 의 공개 CA 지문(널리 쓰는 상수 2종).
# 최신 AWS 는 well-known IdP 지문을 자체 관리하지만, provider validate/plan 정합을
# 위해 명시. GitHub 이 CA 를 교체하면 이 목록 갱신 필요.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fca",
  ]

  tags = {
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# role trust: 이 repo 의 워크플로만 assume 가능하도록 sub 를 스코프.
data "aws_iam_policy_document" "ci_deploy_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # repo:<owner>/<repo>:* — 이 repo 의 어느 ref/워크플로든 허용(브랜치 제한이
    # 필요해지면 sub 를 repo:...:ref:refs/heads/main 등으로 좁힌다).
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:*"]
    }
  }
}

resource "aws_iam_role" "ci_deploy" {
  name               = "mmt-ci-deploy-role"
  description        = "GitHub Actions OIDC role for SSM-based deploy (ADR 0008)"
  assume_role_policy = data.aws_iam_policy_document.ci_deploy_assume.json

  tags = {
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# CI 권한: 최소 — SendCommand(대상=Project=mmt 인스턴스 + AWS-RunShellScript 문서)와
# 실행 결과 회수(GetCommandInvocation). 인스턴스 종료·재구성 등은 불허.
data "aws_iam_policy_document" "ci_deploy_perms" {
  # SendCommand 를 AWS-RunShellScript 문서로 한정.
  statement {
    sid       = "SendCommandDocument"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${data.aws_region.current.name}::document/AWS-RunShellScript"]
  }

  # SendCommand 대상 인스턴스를 Project=mmt 태그로 한정.
  statement {
    sid       = "SendCommandInstances"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:instance/*"]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Project"
      values   = ["mmt"]
    }
  }

  # 실행 결과 폴링/회수. 이 API 들은 리소스 레벨 제한 미지원 → *.
  statement {
    sid    = "ReadCommandResult"
    effect = "Allow"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ci_deploy" {
  name   = "mmt-ci-deploy-ssm"
  role   = aws_iam_role.ci_deploy.id
  policy = data.aws_iam_policy_document.ci_deploy_perms.json
}

# ─────────────────────────────────────────────────────────────────────────────
# outputs — 워크플로에 넣을 role ARN 등.
# ─────────────────────────────────────────────────────────────────────────────

output "ci_deploy_role_arn" {
  description = "워크플로 aws-actions/configure-aws-credentials 의 role-to-assume 에 넣을 ARN"
  value       = aws_iam_role.ci_deploy.arn
}

output "ec2_ssm_instance_profile_name" {
  description = "aws_instance.app.iam_instance_profile 참조용 (compute.tf)"
  value       = aws_iam_instance_profile.ec2_ssm.name
}
