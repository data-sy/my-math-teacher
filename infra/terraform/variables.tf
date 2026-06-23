# infra/terraform/variables.tf
#
# 원칙: "안 닿은 배선은 미리 선언 안 한다" — 슬라이스가 실제로 쓰는 변수만
# 둔다. db_* 등 RDS 변수는 database.tf 가 들어올 때 추가한다.

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

variable "instance_type" {
  description = "EC2 인스턴스 타입 (spec-01 §9.2: 프리티어 t3.micro)"
  type        = string
  default     = "t3.micro"
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
  default     = "8.0"
}
