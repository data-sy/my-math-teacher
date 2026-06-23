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

  # 3306 인바운드 SG 배선은 이 슬라이스 범위 밖(§9.2 SG 는 80/443/22/8080).
  # Phase B/§9 에서 app SG 출발지로만 3306 여는 별도 db SG 추가 예정.

  tags = {
    Name      = "mmt-db"
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

output "rds_endpoint" {
  description = "RDS 접속 엔드포인트 (Phase A 는 plan 단계 known after apply)"
  value       = aws_db_instance.app.endpoint
}
