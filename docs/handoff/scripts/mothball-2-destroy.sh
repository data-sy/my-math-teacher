#!/usr/bin/env bash
# mothball 2/3 — terraform 전체 destroy (⚠️ 비가역)
#
# 런북 §4-4~6. 1단계(mothball-1-snapshot.sh)가 PASS 여야 실행한다.
# 선행: bash ~/mmt-aws-session.sh <MFA 6자리>
# 실행: bash docs/handoff/scripts/mothball-2-destroy.sh
#
# 지워지는 것: EC2 · EIP · RDS(mmt-db) · SG · IAM role/OIDC/instance profile · keypair
# 남는 것   : 수동 스냅샷 · Route53 A레코드(타 계정 · 3단계에서 수동 삭제) · ~/.ssh/mmt-ec2

set -uo pipefail

PROFILE=mmt-session
REGION=ap-northeast-2
SNAP=mmt-mothball-2026-09-08
TFDIR="$(cd "$(dirname "$0")/../../../infra/terraform" && pwd)"
FAIL=0

aws() { command aws --profile "$PROFILE" --region "$REGION" "$@"; }
ok()  { echo "✅ $*"; }
bad() { echo "❌ $*"; FAIL=1; }

echo "=== 0. AWS 세션 ==="
ARN=$(aws sts get-caller-identity --query Arn --output text 2>&1)
if [[ "$ARN" == arn:* ]]; then ok "$ARN"; else
  bad "세션 없음/만료 — 'bash ~/mmt-aws-session.sh <MFA코드>' 먼저 ($ARN)"; exit 1
fi

echo
echo "=== 1. 스냅샷 available 재확인 (파괴 직전 게이트) ==="
read -r S_STATUS S_TYPE S_VER < <(aws rds describe-db-snapshots \
  --db-snapshot-identifier "$SNAP" \
  --query 'DBSnapshots[0].[Status,SnapshotType,EngineVersion]' --output text 2>/dev/null)
if [ "${S_STATUS:-}" != "available" ]; then
  bad "$SNAP 이 available 이 아니다 (=${S_STATUS:-없음}) — 파괴 중단. 1단계부터 다시."
  exit 1
fi
ok "$SNAP · $S_TYPE · MySQL $S_VER · available"

echo
echo "=== 2. destroy 계획 ==="
cd "$TFDIR" || { bad "terraform 디렉토리 없음: $TFDIR"; exit 1; }
export AWS_PROFILE="$PROFILE"
terraform init -input=false -no-color >/dev/null 2>&1 || true
terraform plan -destroy -no-color -input=false 2>&1 | tail -n 40

echo
echo "=== 3. destroy 실행 (terraform 이 'yes' 를 물어본다) ==="
echo "    ⚠️ 여기서부터 비가역이다. 위 계획의 destroy 개수를 확인하고 진행할 것."
terraform destroy -no-color -input=true
TF_EXIT=$?
echo "(terraform exit=$TF_EXIT — 판정은 아래 실제 상태로 한다)"

echo
echo "=== 4. 검증: exit code 말고 AWS 실제 상태로 (런북 §4-9) ==="
EC2=$(aws ec2 describe-instances --filters Name=tag:Project,Values=mmt \
  --query 'Reservations[].Instances[?State.Name!=`terminated`].InstanceId' --output text 2>/dev/null)
[ -z "$EC2" ] && ok "EC2: 살아있는 mmt 인스턴스 없음" || bad "EC2 잔존: $EC2"

RDS=$(aws rds describe-db-instances \
  --query 'DBInstances[?DBInstanceIdentifier==`mmt-db`].DBInstanceStatus' --output text 2>/dev/null)
[ -z "$RDS" ] && ok "RDS: mmt-db 소멸" || bad "RDS 잔존: mmt-db ($RDS · deleting 이면 몇 분 후 재조회)"

EIPS=$(aws ec2 describe-addresses --query 'Addresses[].PublicIp' --output text 2>/dev/null)
[ -z "$EIPS" ] && ok "EIP: 보유 주소 없음 (IPv4 과금 정지)" || bad "EIP 잔존: $EIPS"

SNAP_OK=$(aws rds describe-db-snapshots --db-snapshot-identifier "$SNAP" \
  --query 'DBSnapshots[0].Status' --output text 2>/dev/null)
[ "$SNAP_OK" = "available" ] && ok "스냅샷 $SNAP 는 그대로 살아있음" || bad "스냅샷 상태 이상: ${SNAP_OK:-없음}"

LEFT=$(terraform state list 2>/dev/null | grep -v '^data\.' | wc -l | tr -d ' ')
[ "$LEFT" = "0" ] && ok "terraform state: managed 리소스 0" || bad "terraform state 잔존 $LEFT 건 → terraform state list 확인"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "판정: ✅ PASS — 파괴 완료. 다음 = mothball-3-residual.sh (+ 타 계정 Route53 A레코드 삭제)"
else
  echo "판정: ❌ FAIL — 위 ❌ 항목 확인 (deleting 중이면 2~3분 후 이 스크립트 재실행: 멱등)"
fi
exit "$FAIL"
