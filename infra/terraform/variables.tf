# infra/terraform/variables.tf
#
# 원칙: "안 닿은 배선은 미리 선언 안 한다" — 슬라이스가 실제로 쓰는 변수만
# 둔다. db_* 등 RDS 변수는 database.tf 가 들어올 때 추가한다.

# --- provider 페이즈 토글 (spec-03 Phase A/B) -----------------------------
# true  = Phase A: LocalStack mock(무계정·무과금) — 기존 기본 동작 보존.
# false = Phase B: 실제 AWS(mmt-admin 프로필, MFA assume-role) — real plan/apply.
# 기본을 true 로 둬 bare `terraform plan` 이 실 AWS·MFA·과금을 건드리지 않게 한다.
# Phase B 는 명시적 opt-in: `-var use_localstack=false` 또는 비커밋 terraform.tfvars.
variable "use_localstack" {
  description = "true=LocalStack(Phase A), false=real AWS via mmt-admin profile(Phase B)"
  type        = bool
  default     = true
}

# --- iam.tf 슬라이스 (SSM 배포 채널, ADR 0008) ---------------------------
# GitHub OIDC role trust 의 sub 스코프에 들어갈 repo 슬러그(<owner>/<repo>).
# 노출돼도 무해(공개 repo 식별자) → 커밋 가능. 다른 repo 로 포크 시 override.
variable "github_repo" {
  description = "GitHub Actions OIDC role trust 스코프용 repo 슬러그 (owner/repo)"
  type        = string
  default     = "data-sy/my-math-teacher"
}

variable "my_ip" {
  description = <<-EOT
    SSH(22/tcp) 인바운드를 허용할 단일 출발지 IP (CIDR /32 로 조립).
    Phase A(LocalStack mock)에서는 실제 값이 무의미 → 기본값은 문서용
    TEST-NET-3 placeholder. Phase B/C(real plan/apply)에서는 비커밋
    terraform.tfvars 또는 TF_VAR_my_ip 로 실제 IP 주입(§7 시크릿 위생).
  EOT
  type        = string
  default     = "203.0.113.0" # RFC 5737 TEST-NET-3 (문서·예시 전용, 실 IP 아님)
}

# --- compute.tf 슬라이스 (EC2 + EIP, spec-01 §9.2) ------------------------

# SSH 공개키 경로. aws_key_pair 가 공개키만 등록(개인키는 로컬 ~/.ssh 에 남는다).
# 파일 부재 시 plan/validate 에러 → 먼저 `ssh-keygen -t ed25519 -f ~/.ssh/mmt-ec2`.
variable "ssh_public_key_path" {
  description = "EC2 키페어로 등록할 SSH 공개키 경로"
  type        = string
  default     = "~/.ssh/mmt-ec2.pub"
}

variable "instance_type" {
  # M4(spec-01 §9.2)는 측정용 최소 사양 t3.micro 였다. M6(spec-01 D1=a, TF Serving
  # 실서빙 유지)는 JVM+MySQL(RDS 분리)+Redis+TF Serving 공존으로 4GB 하한이 필요(R7)
  # → 상시 프로덕션 기본을 x86 t3.medium(4GB)로 확정(D4=x86, ARM t4g 대비 IaC 재배선 0).
  # 신규 크레딧 모델이라 상시 무료 아님 — 크레딧 차감.
  description = "EC2 인스턴스 타입 (M6 상시 프로덕션: 4GB 하한, x86 t3.medium)"
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size" {
  description = "루트 EBS 볼륨 크기 GB (spec-01 §9.2: gp3 30GB)"
  type        = number
  default     = 30
}

# 더미 GDB_* — spec-01 R1: Neo4j 를 더미 env 로 재워 코드 0 변경으로 풀
# 컨텍스트 기동. 이 값들은 *의도된 placeholder*(실 자격 아님) → 커밋 가능.
# use-mysql-cte-for-graph=true 와 함께 user_data 로 주입.
variable "gdb_host" {
  type    = string
  default = "localhost"
}

variable "gdb_port" {
  type    = string
  default = "7687"
}

variable "gdb_user" {
  type    = string
  default = "neo4j"
}

variable "gdb_password" {
  description = "R1 더미 placeholder('dummy') — 실 시크릿 아님. 실 GDB 자격은 도입 시 비커밋 주입."
  type        = string
  default     = "dummy"
}

# --- database.tf 슬라이스 (RDS MySQL, spec-01 §9.3) -----------------------

variable "db_name" {
  description = "초기 생성 DB 스키마 이름"
  type        = string
  default     = "mmt"
}

variable "db_username" {
  description = "RDS 마스터 유저명 (시크릿 아님 — 노출돼도 비번 없이는 무용)"
  type        = string
  default     = "mmtadmin"
}

variable "db_password" {
  description = <<-EOT
    RDS 마스터 비밀번호. ★ 진짜 시크릿 — default 없음.
    비커밋 terraform.tfvars 또는 TF_VAR_db_password 로만 주입(§7·G2).
    한 번도 코드/커밋/tfstate(=비커밋)에 평문으로 남기지 않는다.
  EOT
  type        = string
  sensitive   = true
}

variable "db_allocated_storage" {
  description = "RDS 스토리지 GB (spec-01 §9.3: 20GB)"
  type        = number
  default     = 20
}

variable "db_engine_version" {
  description = "MySQL 엔진 버전 (운영 MySQL 8 과 정렬)"
  type        = string
  default     = "8.4"
}
