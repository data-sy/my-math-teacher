#!/usr/bin/env bash
# CI deploy job(SSM)이 대상 인스턴스를 못 찾는 원인 가르기
#
#   실행: bash ~/ssm-deploy-diagnose.sh
#   전제: aws 프로파일 mmt-admin (MFA 코드 입력 프롬프트가 뜬다) + ~/.ssh/mmt-ec2
#   끝나면 지워도 됨: rm ~/ssm-deploy-diagnose.sh
#
# 배경: run 30972873738(2026-08-05, 대기 30초)·31078404015(2026-08-06, 대기 2분) 둘 다
#       "SSM 이 대상 인스턴스를 찾지 못함" 으로 실패. 2분도 초과했으므로 재런치 직후
#       에이전트 등록 지연 가설은 기각됐다 → 태그 / SSM 등록 / 권한 중 하나다.
# 읽기 전용 — 아무것도 바꾸지 않는다.

HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
REGION=ap-northeast-2
PROFILE=mmt-admin

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
echo " CI deploy(SSM) 대상 인스턴스 진단 — 읽기 전용"
echo "=============================================="

# ---------- 1. Project=mmt 태그가 붙은 running 인스턴스 ----------
echo
echo "[1] Project=mmt 태그 + running 인스턴스 (워크플로의 타겟 조건 그대로)"
OUT=$(aws ec2 describe-instances --profile "$PROFILE" --region "$REGION" \
  --filters Name=tag:Project,Values=mmt Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].{Id:InstanceId,Ip:PublicIpAddress,Profile:IamInstanceProfile.Arn}' \
  --output text 2>&1)
echo "$OUT" | sed 's/^/      /'
if echo "$OUT" | grep -q "^i-\|	i-"; then
  ok "타겟 조건에 맞는 인스턴스 있음 → 원인은 태그가 아니다"
else
  bad "타겟 조건에 맞는 인스턴스 0개 → 이게 원인. 아래 [2] 로 태그 상태 확인"
fi

# ---------- 2. 태그 무시하고 running 인스턴스 전수 ----------
echo
echo "[2] 태그 조건 없이 running 인스턴스 전수 (태그 누락인지 인스턴스 부재인지 가름)"
aws ec2 describe-instances --profile "$PROFILE" --region "$REGION" \
  --filters Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].{Id:InstanceId,Ip:PublicIpAddress,Project:Tags[?Key==`Project`].Value|[0],Profile:IamInstanceProfile.Arn}' \
  --output table 2>&1 | sed 's/^/      /'
echo "      ↑ 프로덕션 IP $HOST 인 행의 Project 값과 Profile(=IAM instance profile) 을 볼 것"
echo "        Project 가 None 이면 태그 누락, Profile 이 None 이면 SSM 등록 불가"

# ---------- 3. SSM 에 등록된 인스턴스 ----------
echo
echo "[3] SSM 에 등록된 인스턴스 (에이전트 + IAM 프로파일이 둘 다 돼야 여기 뜬다)"
SSMOUT=$(aws ssm describe-instance-information --profile "$PROFILE" --region "$REGION" \
  --query 'InstanceInformationList[].{Id:InstanceId,Ping:PingStatus,Agent:AgentVersion,Platform:PlatformName}' \
  --output text 2>&1)
echo "$SSMOUT" | sed 's/^/      /'
if echo "$SSMOUT" | grep -qi "Online"; then
  ok "SSM 등록된 Online 인스턴스 있음"
else
  bad "SSM 에 Online 인스턴스 없음 → 에이전트 미기동 또는 IAM instance profile 미부착이 원인"
fi

# ---------- 4. 호스트에서 에이전트 실물 확인 (SSH — MFA 불요) ----------
echo
echo "[4] 호스트의 amazon-ssm-agent 실물 상태 (SSH)"
ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 "$SSHU@$HOST" '
  echo "    instance-id: $(curl -s --max-time 5 http://169.254.169.254/latest/meta-data/instance-id || echo 조회실패)"
  echo "    iam role   : $(curl -s --max-time 5 http://169.254.169.254/latest/meta-data/iam/security-credentials/ || echo 없음)"
  echo -n "    agent      : "
  if systemctl is-active amazon-ssm-agent >/dev/null 2>&1; then
    echo "active ($(systemctl is-enabled amazon-ssm-agent 2>/dev/null))"
  elif systemctl list-unit-files 2>/dev/null | grep -q amazon-ssm-agent; then
    echo "설치됨이나 미기동 — sudo systemctl start amazon-ssm-agent 필요"
  else
    echo "미설치 — 이게 원인이면 에이전트 설치 필요"
  fi
' 2>&1 | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*" | sed 's/^/      /'

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
echo " 판정 가이드:"
echo "   [1] 비었고 [2] 에 인스턴스 있음        → Project 태그 누락 (terraform 밖에서 만든 인스턴스?)"
echo "   [2] 의 Profile 이 None                 → IAM instance profile 미부착 → SSM 등록 불가"
echo "   [3] 비었고 [4] agent 미기동/미설치      → 에이전트 문제"
echo "   전부 정상인데도 CI 가 실패              → CI 역할(mmt-ci-deploy-role) 의 SendCommand 조건 확인"
echo "=============================================="
