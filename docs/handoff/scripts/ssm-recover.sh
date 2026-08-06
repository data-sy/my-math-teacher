#!/usr/bin/env bash
# CD(SSM 배포) 복구 — 공인 IP 갱신 → SSH 복구 → SSM 에이전트 점검/재기동 → 등록 확인
#
#   진단·plan 까지만 (기본):  bash ~/ssm-recover.sh
#   실제 적용:                bash ~/ssm-recover.sh --apply
#
# 왜 이게 필요한가 (ssm-deploy-diagnose.sh 결과, 2026-08-06):
#   [1][2] Project=mmt 태그·IAM instance profile = 정상
#   [3]    SSM 등록 = 전무 (ConnectionLost 가 아니라 "한 번도 등록된 적 없음")
#   [4]    SSH 22 = timeout ← SG 의 SSH 인그레스가 var.my_ip/32 인데 공인 IP 가 바뀌었다
#   egress 는 전부 허용이고 AMI 는 AL2023(에이전트 기본 탑재)이라 네트워크·이미지 문제는 아니다.
#   → 남은 가설: instance profile 이 부팅 이후 attach 돼(compute.tf:70 "in-place 업데이트")
#     에이전트가 자격증명 없이 뜬 채로 있다. 확인·복구하려면 먼저 SSH 가 열려야 한다.
#
# 안전: 기본은 아무것도 바꾸지 않는다(tfvars 갱신 + terraform plan 까지).
#       --apply 는 SG 의 SSH 인그레스 규칙 하나만 -target 으로 바꾼다(가역, 서빙 무관).
# 끝나면 지워도 됨: rm ~/ssm-recover.sh

set -uo pipefail

MODE="${1:-plan}"
REPO=~/my-math-teacher
TFDIR="$REPO/infra/terraform"
TFVARS="$TFDIR/terraform.tfvars"
TARGET='aws_vpc_security_group_ingress_rule.ssh'
PLANFILE=/tmp/mmt-ssh-ip.tfplan
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
REGION=ap-northeast-2

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
if [ "$MODE" = "--apply" ]; then
  echo " CD 복구 — ⚠️  적용 모드 (SG SSH 규칙 변경)"
else
  echo " CD 복구 — 진단·plan 모드 (변경 없음)"
fi
echo "=============================================="

# ---------- 0. 전제 ----------
echo
echo "[0] 전제 확인"
[ -d "$TFDIR" ] || { bad "terraform 디렉토리 없음: $TFDIR"; exit 1; }
[ -f "$TFVARS" ] || { bad "terraform.tfvars 없음 (비커밋 파일)"; exit 1; }
command -v terraform >/dev/null || { bad "terraform 미설치"; exit 1; }
ok "terraform·tfvars 확인"

# ---------- 1. 임시자격 (MFA) ----------
echo
echo "[1] AWS 임시자격 (MFA 코드 입력)"
cd "$TFDIR" || exit 1
# shellcheck disable=SC1091
source ./tf-assume.sh
if aws sts get-caller-identity >/dev/null 2>&1; then
  ok "자격 획득"
else
  bad "자격 획득 실패 — MFA 코드/프로필 확인"; exit 1
fi

# ---------- 2. 공인 IP 비교·갱신 ----------
echo
echo "[2] 공인 IP 대조 (SG 의 SSH 인그레스 = var.my_ip/32)"
CUR=$(curl -s --max-time 10 https://checkip.amazonaws.com | tr -d '[:space:]')
OLD=$(grep -E '^\s*my_ip' "$TFVARS" | sed 's/.*=//' | tr -d ' "'"'"'')
[ -n "$CUR" ] || { bad "현재 공인 IP 조회 실패"; exit 1; }

if [ "$CUR" = "$OLD" ]; then
  ok "tfvars 의 my_ip 와 일치 — SSH 차단 원인은 IP 가 아니다"
else
  echo "      tfvars 의 my_ip 가 현재 IP 와 다르다 (값은 출력하지 않음)"
  cp "$TFVARS" "$TFVARS.bak.$(date +%Y%m%d%H%M%S)"
  # my_ip 줄만 현재 IP 로 치환 (나머지 변수는 건드리지 않는다)
  awk -v ip="$CUR" '
    /^[ \t]*my_ip[ \t]*=/ { printf("my_ip = \"%s\"\n", ip); next }
    { print }
  ' "$TFVARS" > "$TFVARS.tmp" && mv "$TFVARS.tmp" "$TFVARS"
  ok "tfvars 의 my_ip 를 현재 IP 로 갱신 (원본은 .bak 으로 보존)"
fi

# ---------- 3. plan (SSH 규칙만) ----------
echo
echo "[3] terraform plan — SSH 인그레스 규칙 하나만 대상"
terraform plan -target="$TARGET" -out="$PLANFILE" -input=false 2>&1 \
  | grep -E "will be|must be|No changes|Plan:|Error" | sed 's/^/      /'
if [ ! -f "$PLANFILE" ]; then
  bad "plan 실패 — 위 출력 확인"; exit 1
fi
ok "plan 생성 (${PLANFILE})"

if [ "$MODE" != "--apply" ]; then
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " 🔍 아무것도 적용하지 않았다. 위 plan 이 SSH 규칙 1건만 바꾸는지 확인 후:"
  echo "      bash ~/ssm-recover.sh --apply"
  echo "=============================================="
  exit 0
fi

# ---------- 4. apply ----------
echo
echo "[4] terraform apply (저장된 plan — SSH 규칙 1건)"
if terraform apply -input=false "$PLANFILE" 2>&1 | tail -5 | sed 's/^/      /'; then
  ok "apply 완료"
else
  bad "apply 실패"; exit 1
fi

# ---------- 5. SSH 복구 확인 ----------
echo
echo "[5] SSH 복구 확인"
SSHOK=0
for i in 1 2 3; do
  if ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
        "$SSHU@$HOST" 'echo ok' >/dev/null 2>&1; then
    SSHOK=1; break
  fi
  sleep 5
done
[ "$SSHOK" = "1" ] && ok "SSH 접속 성공" || { bad "SSH 여전히 실패 — SG 반영 지연이면 잠시 후 재시도"; exit 1; }

# ---------- 6. SSM 에이전트 점검 → 필요 시 재기동 ----------
echo
echo "[6] amazon-ssm-agent 점검 (재기동은 필요할 때만)"
ssh -i "$KEY" -o ConnectTimeout=15 "$SSHU@$HOST" '
  echo "    instance-id : $(curl -s --max-time 5 http://169.254.169.254/latest/meta-data/instance-id)"
  echo "    iam role    : $(curl -s --max-time 5 http://169.254.169.254/latest/meta-data/iam/security-credentials/ || echo 없음)"
  if ! systemctl list-unit-files 2>/dev/null | grep -q amazon-ssm-agent; then
    echo "    agent       : 미설치 → 설치 필요"
    exit 3
  fi
  echo "    agent 상태  : $(systemctl is-active amazon-ssm-agent 2>/dev/null)"
  echo "    --- 최근 로그 5줄 ---"
  sudo tail -5 /var/log/amazon/ssm/amazon-ssm-agent.log 2>/dev/null | sed "s/^/    /"
  echo "    --- 재기동 ---"
  sudo systemctl enable --now amazon-ssm-agent >/dev/null 2>&1
  sudo systemctl restart amazon-ssm-agent
  sleep 3
  echo "    재기동 후   : $(systemctl is-active amazon-ssm-agent 2>/dev/null)"
' 2>&1 | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*" | sed 's/^/    /'

# ---------- 7. SSM 등록 확인 (폴링) ----------
echo
echo "[7] SSM 등록 확인 (최대 2분 폴링)"
REG=""
for _ in $(seq 1 24); do
  REG=$(aws ssm describe-instance-information --region "$REGION" \
        --query 'InstanceInformationList[?PingStatus==`Online`].InstanceId' --output text 2>/dev/null)
  [ -n "$REG" ] && [ "$REG" != "None" ] && break
  sleep 5
done
if [ -n "$REG" ] && [ "$REG" != "None" ]; then
  ok "SSM 등록 완료 — Online: $REG"
else
  bad "2분 내 등록 안 됨 — [6] 의 로그 5줄이 원인을 말해준다(자격증명/네트워크/시각동기 등)"
fi

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ 복구 완료 — 이제 CD 를 재실행하면 된다:"
  echo "      gh workflow run api-ci-cd-with-ec2.yml --ref feat/m7-item-selection -f skip_tests=true"
  echo "    ⚠️ tfvars 의 my_ip 가 바뀌었다 — 다음 terraform apply 때 이 값이 정본이다"
else
  echo " ⛔ 미완 — 위 ❌ 확인"
fi
echo "=============================================="
