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
#
# ⚠️ 필터에 "2023." 을 박은 이유(백로그 D1): "al2023-ami-*-x86_64" 는 표준 이미지 외에
# al2023-ami-minimal-* / al2023-ami-ecs-hvm-* / al2023-ami-ecs-neuron-hvm-* 까지 매칭한다
# (2026-08-31 describe-images 로 4계열 확인). minimal 과 ECS 변형은 용도가 다르고,
# minimal 에는 SSM 에이전트가 없어 ADR 0008 의 CD 채널이 죽는다 — 2026-08-05 재런치가
# 실제로 minimal 을 집었다. 표준 이미지 이름만 "al2023-ami-2023.<날짜>-kernel-*-x86_64"
# 형태라 "2023." 을 붙이면 변형이 전부 배제된다.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
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

    # --- SSM 에이전트 보장 (백로그 D3) ---
    # ADR 0008 의 CD 채널이 SSM Run Command 라 에이전트가 없으면 배포가 죽는다.
    # AMI 필터가 표준 이미지를 집도록 조였지만(D1), 변형이 섞여 들어와도 살아남게
    # user_data 에서 한 번 더 보장한다. 표준 이미지에서는 설치 완료 상태라 no-op.
    dnf install -y amazon-ssm-agent
    systemctl enable --now amazon-ssm-agent

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

  # ADR 0008: SSM 등록용 instance profile. attach 는 in-place 업데이트(교체 아님).
  iam_instance_profile = aws_iam_instance_profile.ec2_ssm.name

  vpc_security_group_ids = [aws_security_group.app.id]
  user_data              = local.user_data

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
  }

  # ⚠️ AMI 우발 교체 차단 (백로그 D2, 2026-08-31 결정).
  # data.aws_ami.al2023 은 most_recent = true 라 새 AL2023 이 릴리스될 때마다 id 가 바뀌고,
  # aws_instance.ami 는 ForceNew 라 그 diff 가 곧 프로덕션 EC2 교체(+EIP 연결 교체)다.
  # 교체는 호스트 로컬 자산(~/mmt-backend.env·~/active-backend.conf·~/deploy/·nginx·TLS)을
  # 전부 소멸시킨다 — 재런치 절차를 처음부터 밟아야 한다.
  # 대가: AMI 가 현재 값에 고정돼 보안 업데이트가 자동으로 따라오지 않는다.
  # AMI 를 올릴 때는 이 블록을 일시 제거하거나 taint 로 사람이 의도적으로 교체한다.
  #
  # user_data 를 함께 무시하는 이유(백로그 D3): provider 5.x 는 user_data 변경을
  # 교체가 아닌 in-place 업데이트로 처리하는데, 그 구현이 stop → ModifyInstanceAttribute
  # → start 라 상시 서비스에 다운타임이 생긴다. 게다가 cloud-init 은 최초 부팅에만
  # 실행되므로 러닝 인스턴스에는 아무 효과가 없다 — 다운타임만 내고 얻는 게 없다.
  # ignore_changes 는 create 에는 적용되지 않으므로 다음 런치는 최신 user_data 로 뜬다.
  # 러닝 인스턴스의 에이전트 복구는 docs/handoff/scripts/ssm-recover.sh --apply 몫이다.
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = {
    Name      = "mmt-app"
    Project   = "mmt"
    Milestone = "m4"
    ManagedBy = "terraform"
  }
}

# EIP: 인스턴스에 고정 공인 IP. ⚠️ 2024-02 이후 AWS 는 연결 여부와 무관하게
# 모든 공인 IPv4 에 $0.005/hr(~$3.6/월) 부과 — "연결 시 무료"는 폐지됨.
# 이 계정(2026-07 생성)은 신규 크레딧 모델(유료 플랜, $200 선차감)이라 이 요금도
# 크레딧에서 빠진다. 안 쓰면 destroy 로 EIP release → IPv4 차감 정지.
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
