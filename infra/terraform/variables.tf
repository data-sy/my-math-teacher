# infra/terraform/variables.tf
#
# 원칙: "안 닿은 배선은 미리 선언 안 한다" — 이 슬라이스(network.tf SG)가
# 실제로 쓰는 변수만 둔다. region/instance_type/db_*/GDB_* 등은 해당 슬라이스
# (compute.tf / database.tf)가 들어올 때 추가한다.

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
