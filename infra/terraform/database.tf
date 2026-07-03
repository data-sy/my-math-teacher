# infra/terraform/database.tf
#
# spec-01 §9.3 RDS MySQL → HCL. Phase A 마지막 슬라이스.
#
# ⚠️ 다른 슬라이스와 닫는 법이 다르다: community LocalStack 은 RDS 를
# 미지원(Pro 기능)이라 **mock-apply 안 됨 → validate + plan 까지만** 신뢰
# (spec-03 R-T1·§3 D1). plan diff(생성 계획)를 읽는 것까지가 이 슬라이스의
# green. apply 시도 금지 — 사이클 미완은 정상·의도.
#
# 비번 = var.db_password(sensitive, default 없음). 비커밋 tfvars/TF_VAR 로만.

resource "aws_db_instance" "app" {
  identifier     = "mmt-db"
  engine         = "mysql"
  engine_version = var.db_engine_version
  instance_class = "db.t3.micro" # 프리티어

  allocated_storage = var.db_allocated_storage
  db_name           = var.db_name
  username          = var.db_username
  password          = var.db_password

  multi_az            = false # Single-AZ (§9.3)
  skip_final_snapshot = true  # 학습/일회성 — 삭제 시 스냅샷 강제 안 함
  publicly_accessible = false # 앱(EC2)에서만 접근, 공인 노출 안 함

  # app SG 출발지로만 3306 을 여는 전용 db SG(아래)만 사용. 공인/기본SG 노출 안 함.
  vpc_security_group_ids = [aws_security_group.db.id]

  tags = {
    Name      = "mmt-db"
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# db SG: RDS 전용. app SG(network.tf) 출발지에서만 3306 허용(spec-01 §9.2 후속).
resource "aws_security_group" "db" {
  name        = "mmt-db-sg"
  description = "MMT RDS SG - MySQL 3306 from app SG only"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# 출발지 = app SG(참조). CIDR 이 아니라 SG-to-SG 규칙이라 EC2 만 3306 도달.
resource "aws_vpc_security_group_ingress_rule" "db_mysql" {
  security_group_id            = aws_security_group.db.id
  description                  = "MySQL 3306 from app SG only"
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
}

output "rds_endpoint" {
  description = "RDS 접속 엔드포인트 (Phase A 는 plan 단계 known after apply)"
  value       = aws_db_instance.app.endpoint
}
