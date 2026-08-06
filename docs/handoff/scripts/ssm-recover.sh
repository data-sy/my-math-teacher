#!/usr/bin/env bash
# SSM 등록 복구 — 에이전트 점검 → (--apply 면) 재기동 → 등록 확인
#
#   점검만 (기본):  bash ~/ssm-recover.sh
#   재기동까지:     bash ~/ssm-recover.sh --apply
#
# 전제: **SSH 가 열려 있어야 한다.** 막혀 있으면 먼저:  bash ~/sync-my-ip.sh --apply
#       (SG 의 SSH 인그레스가 var.my_ip/32 라 공인 IP 가 바뀌면 통째로 막힌다)
#
# 무엇을 고치나 (ssm-deploy-diagnose.sh 2026-08-06 결과):
#   태그·IAM instance profile = 정상 / SSM 등록 = 전무(ConnectionLost 아님 = 한 번도 등록 안 됨)
#   egress 전체 허용 + AL2023(에이전트 기본 탑재)이라 네트워크·이미지 문제는 아니다.
#   유력 가설 = instance profile 이 부팅 이후 attach 돼(compute.tf:70 "in-place 업데이트")
#   에이전트가 자격증명 없이 뜬 상태. 재기동으로 잡히는지 본다.
#   ⚠️ 재기동으로도 등록이 안 되면 이 가설이 틀린 것이고, [2] 가 찍는 로그가 다음 단서다.
#
# 안전: 기본은 읽기만 한다(에이전트 상태·로그 조회). --apply 만 재기동한다.
#       인프라·서빙 무관 — 백엔드 컨테이너를 건드리지 않는다.
# 끝나면 지워도 됨: rm ~/ssm-recover.sh

set -uo pipefail

MODE="${1:-check}"
TFDIR=~/my-math-teacher/infra/terraform
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
REGION=ap-northeast-2

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
if [ "$MODE" = "--apply" ]; then
  echo " SSM 등록 복구 — ⚠️  에이전트 재기동 포함"
else
  echo " SSM 등록 복구 — 점검 모드 (변경 없음)"
fi
echo "=============================================="

# ---------- 1. 전제: SSH ----------
echo
echo "[1] SSH 전제 확인"
if ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
      "$SSHU@$HOST" 'echo ok' >/dev/null 2>&1; then
  ok "SSH 접속됨"
else
  bad "SSH timeout — 이 스크립트는 여기서 할 수 있는 게 없다"
  echo
  echo "      먼저 실행:  bash ~/sync-my-ip.sh --apply"
  echo "⛔ 중단"; exit 1
fi

# ---------- 2. 에이전트 실물 점검 ----------
echo
echo "[2] amazon-ssm-agent 실물 점검"
ssh -i "$KEY" -o ConnectTimeout=15 "$SSHU@$HOST" '
  echo "instance-id : $(curl -s --max-time 5 http://169.254.169.254/latest/meta-data/instance-id)"
  echo "iam role    : $(curl -s --max-time 5 http://169.254.169.254/latest/meta-data/iam/security-credentials/ || echo 없음)"
  if ! systemctl list-unit-files 2>/dev/null | grep -q amazon-ssm-agent; then
    echo "agent       : 미설치 → 설치 필요 (AL2023 기본 탑재인데 없다면 이미지/부트스트랩 확인)"
    exit 0
  fi
  echo "agent 상태  : $(systemctl is-active amazon-ssm-agent 2>/dev/null) / $(systemctl is-enabled amazon-ssm-agent 2>/dev/null)"
  echo "--- 최근 로그 8줄 (원인 단서) ---"
  sudo tail -8 /var/log/amazon/ssm/amazon-ssm-agent.log 2>/dev/null || echo "(로그 없음 — 한 번도 안 떴을 수 있다)"
' 2>&1 | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*" | sed 's/^/      /'

if [ "$MODE" != "--apply" ]; then
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " 🔍 점검만 했다 — 재기동하려면:  bash ~/ssm-recover.sh --apply"
  echo "=============================================="
  exit 0
fi

# ---------- 3. 재기동 ----------
echo
echo "[3] 에이전트 재기동"
ssh -i "$KEY" -o ConnectTimeout=15 "$SSHU@$HOST" '
  sudo systemctl enable --now amazon-ssm-agent >/dev/null 2>&1
  sudo systemctl restart amazon-ssm-agent
  sleep 3
  echo "재기동 후   : $(systemctl is-active amazon-ssm-agent 2>/dev/null)"
' 2>&1 | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*" | sed 's/^/      /'
ok "재기동 명령 전송"

# ---------- 4. 등록 폴링 ----------
echo
echo "[4] AWS 임시자격 (MFA) → SSM 등록 확인"
cd "$TFDIR" || exit 1
# shellcheck disable=SC1091
source ./tf-assume.sh
aws sts get-caller-identity >/dev/null 2>&1 && ok "자격 획득" || { bad "자격 획득 실패"; exit 1; }

echo "      최대 2분 폴링..."
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
  bad "2분 내 등록 안 됨 — [2] 의 로그가 원인을 말해준다(자격증명/엔드포인트/시각동기 등)"
fi

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ 복구 완료 — 이제 CD 를 재실행하면 된다:"
  echo "      gh workflow run api-ci-cd-with-ec2.yml --ref feat/m7-item-selection -f skip_tests=true"
else
  echo " ⛔ 미완 — 위 ❌ 확인"
fi
echo "=============================================="
