# infra/terraform/compute.tf
#
# spec-01 §9.2 EC2 + EIP → HCL. network.tf 의 SG 를 묶어 단일 앱 호스트를
# 표현한다. Phase A 는 LocalStack mock — AMI 는 canned, 실제 부팅 아님
# (green = 테라폼 그래프 체득이지 AWS 기동 아님, spec-03 §5).
#
# 더미 GDB_* env 주입(spec-01 R1) = user_data 부트스트랩. 앱 코드 0 변경,
# IaC 는 env 만 넣는다.

# AL2023 AMI 조회(data source = plan 시점 read, R-T2 → LocalStack 엣지로).
# Phase B(real)에서는 실제 최신 AL2023 을 조회. most_recent 로 갱신 추종.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
}

# 부트스트랩: 더미 GDB_* + 피처 플래그를 백엔드 컨테이너 env 파일로.
# (실제 배포 시 compose/systemd 가 읽음. Phase A 는 문자열로만 존재.)
locals {
  user_data = <<-EOT
    #!/bin/bash
    set -euxo pipefail

    # --- 2GB 스왑 (spec-01 §9.4: t3.micro 1GiB 에 전환 구간 JVM 2개 공존용) ---
    if [ ! -f /swapfile ]; then
      dd if=/dev/zero of=/swapfile bs=1M count=2048
      chmod 600 /swapfile
      mkswap /swapfile
      swapon /swapfile
      echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi

    # --- Docker + compose v2 플러그인 (AL2023) ---
    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

    # --- spec-01 R1: Neo4j 더미 env 로 재우고 MySQL CTE 경로로 풀 기동 ---
    mkdir -p /etc/mmt
    cat >/etc/mmt/backend.env <<'ENV'
    GDB_HOST=${var.gdb_host}
    GDB_PORT=${var.gdb_port}
    GDB_USER=${var.gdb_user}
    GDB_PASSWORD=${var.gdb_password}
    MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true
    ENV
  EOT
}

# SSH 키페어: 공개키만 등록(개인키는 로컬 ~/.ssh/mmt-ec2). spec-04 C 의 EC2 초기화 SSH 용.
resource "aws_key_pair" "app" {
  key_name   = "mmt-ec2"
  public_key = trimspace(file(pathexpand(var.ssh_public_key_path)))
}

resource "aws_instance" "app" {
  ami           = data.aws_ami.al2023.id
  instance_type = var.instance_type
  key_name      = aws_key_pair.app.key_name

  vpc_security_group_ids = [aws_security_group.app.id]
  user_data              = local.user_data

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
  }

  tags = {
    Name      = "mmt-app"
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# EIP: 인스턴스에 고정 공인 IP. 실행 인스턴스에 연결돼 있으면 무료(§9.2).
resource "aws_eip" "app" {
  domain = "vpc"

  tags = {
    Name      = "mmt-app-eip"
    Project   = "mmt"
    ManagedBy = "terraform"
  }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}

output "app_instance_id" {
  value = aws_instance.app.id
}

output "app_public_ip" {
  description = "EIP 공인 IP (nginx 진입점; Phase A 는 LocalStack mock 값)"
  value       = aws_eip.app.public_ip
}
