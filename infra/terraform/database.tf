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
  instance_class = "db.t3.micro" # 최소 사양(RDS 최대 비용원 ~$21/월 24/7) — 신규 크레딧 모델, 크레딧 차감

  allocated_storage = var.db_allocated_storage
  db_name           = var.db_name
  username          = var.db_username
  password          = var.db_password

  # snapshot_identifier 는 없다 — apply 는 **빈 mmt-db** 를 만든다. 데이터는 api/sql 시드로 적재한다
  # (재런치 런북 §3). 계정의 RDS 스냅샷은 2026-10-02 에 전부 삭제했다(복원할 원천이 없다).
  # ⚠️ 나중에 스냅샷 복원으로 되돌리려면: snapshot_identifier 는 ForceNew 라, 인스턴스가 떠 있는
  # 상태에서 추가·변경하면 RDS replacement(=데이터 소멸)다. 그리고 8.0.x 스냅샷은 복원하지 말 것 —
  # 표준지원 종료(2026-07-31) 이후 복원은 Extended Support 에 자동 편입된다
  # (docs/incidents/2026-08-rds-extended-support.md). 새 인스턴스는 var.db_engine_version(8.4)을 따른다.

  multi_az            = false # Single-AZ (§9.3)
  skip_final_snapshot = true  # 학습/일회성 — 삭제 시 스냅샷 강제 안 함

  # 자동 백업 7일(2026-08-31). 그 전까지 retention=0 이라 **PITR 이 아예 없었고**
  # 복원점은 수동 스냅샷뿐이었다. 실사용 2.5GB 는 할당 20GB 무료 백업 한도 안이라 비용 ~$0
  # (실단가 $0.095/GB-월). ⚠️ 이 값을 0 으로 되돌리면 RDS 가 인스턴스를 재시작한다(0↔비영 전환).
  # 백업 창은 03:00-03:30 KST(=18:00 UTC) — 구 22:09 KST 는 서비스 활성 시간대였다.
  backup_retention_period = 7
  backup_window           = "18:00-18:30"
  publicly_accessible     = false # 앱(EC2)에서만 접근, 공인 노출 안 함

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
