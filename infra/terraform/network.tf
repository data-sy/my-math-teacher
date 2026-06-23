# infra/terraform/network.tf
#
# spec-01 §9.2 SG → HCL. Phase A 의 첫 *생성* 리소스(앞선 provider.tf 는
# data source 1개뿐). 여기서 validate→plan→(mock)apply→destroy 사이클을
# 실제로 한 바퀴 돌린다(spec-03 §5 Phase A 학습 목표).
#
# 룰 규약(spec-01 §9.2):
#   80/tcp, 443/tcp  → 공개(0.0.0.0/0)
#   22/tcp           → 내 IP 만(var.my_ip/32)
#   8080/tcp         → inbound 룰 *없음*(앱 포트 비공개; nginx 만 도달) ← 의도적 부재
#   egress           → 전체 허용(아웃바운드 패키지/외부 API)

# SG 는 VPC 에 속한다. 계정에 기본 제공되는 default VPC 를 조회해 묶는다
# (LocalStack community 도 default VPC 를 mock 제공 → R-T2 read 가 엣지로 감).
data "aws_vpc" "default" {
  default = true
}

resource "aws_security_group" "app" {
  name        = "mmt-app-sg"
  description = "MMT single-EC2 app SG (spec-01 §9.2)"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# --- ingress (분리 리소스 = provider v5 권장; 인라인 block 과 혼용 금지) ----

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.app.id
  description       = "HTTP public"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS public"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.app.id
  description       = "SSH from my IP only"
  cidr_ipv4         = "${var.my_ip}/32"
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
}

# 8080 은 의도적으로 ingress 룰을 두지 않는다 — 앱 포트는 외부 비공개.
# (nginx ↔ backend 는 같은 호스트/네트워크 내부 통신)

# --- egress (전체 허용) ----------------------------------------------------

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.app.id
  description       = "Allow all outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1" # 모든 프로토콜/포트
}

output "app_security_group_id" {
  description = "생성된 앱 SG 의 ID (compute.tf 의 aws_instance 가 참조 예정)"
  value       = aws_security_group.app.id
}
