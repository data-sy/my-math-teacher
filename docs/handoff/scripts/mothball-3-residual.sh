#!/usr/bin/env bash
# mothball 3/3 — 잔여 과금·dangling DNS 점검 (read-only)
#
# 런북 §4-7·§4-8. 7개월 휴면을 전제로 "매달 새는 것"이 없는지 본다.
# 실행: bash docs/handoff/scripts/mothball-3-residual.sh

set -uo pipefail

PROFILE=mmt-session
REGION=ap-northeast-2
FAIL=0

aws() { command aws --profile "$PROFILE" --region "$REGION" "$@"; }
ok()  { echo "✅ $*"; }
bad() { echo "❌ $*"; FAIL=1; }
info(){ echo "ℹ️  $*"; }

echo "=== 0. AWS 세션 ==="
ARN=$(aws sts get-caller-identity --query Arn --output text 2>&1)
[[ "$ARN" == arn:* ]] && ok "$ARN" || { bad "세션 없음 — 'bash ~/mmt-aws-session.sh <MFA코드>'"; exit 1; }

echo
echo "=== 1. 시간당 과금 리소스 ==="
V=$(aws ec2 describe-volumes --query 'Volumes[].[VolumeId,Size,State]' --output text 2>/dev/null)
[ -z "$V" ] && ok "EBS 볼륨 0" || bad "EBS 볼륨 잔존 (월 ~\$0.08/GB):
$V"

A=$(aws ec2 describe-addresses --query 'Addresses[].[PublicIp,AllocationId]' --output text 2>/dev/null)
[ -z "$A" ] && ok "EIP 0" || bad "EIP 잔존 (월 ~\$3.6/개):
$A"

D=$(aws rds describe-db-instances --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]' --output text 2>/dev/null)
[ -z "$D" ] && ok "RDS 인스턴스 0" || bad "RDS 잔존:
$D"

I=$(aws ec2 describe-instances --filters Name=instance-state-name,Values=running,stopped \
  --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output text 2>/dev/null)
[ -z "$I" ] && ok "EC2 0 (stopped 도 EBS 로 과금되므로 함께 확인)" || bad "EC2 잔존:
$I"

echo
echo "=== 2. 저장 과금 (휴면 중 유일하게 남는 비용) ==="
aws rds describe-db-snapshots --snapshot-type manual \
  --query 'DBSnapshots[].[DBSnapshotIdentifier,EngineVersion,AllocatedStorage,Status]' --output table
info "수동 스냅샷은 인스턴스 삭제 후에도 영구 보존 · 대략 \$0.095/GB-월"
info "옛 mmt-mothball-2026-07-31(8.0.45)은 복원 시 Extended Support 재편입 위험 —"
info "  보관 가치가 없다고 판단되면 삭제:"
info "  aws rds delete-db-snapshot --db-snapshot-identifier mmt-mothball-2026-07-31 --profile $PROFILE --region $REGION"

ES=$(aws ec2 describe-snapshots --owner-ids self --query 'Snapshots[].[SnapshotId,VolumeSize]' --output text 2>/dev/null)
[ -z "$ES" ] && ok "EBS 스냅샷 0" || info "EBS 스냅샷 존재 (판단 필요):
$ES"

echo
echo "=== 3. \$0 이지만 남아있을 수 있는 것 (전체 destroy 했으므로 0 이어야 정상) ==="
for R in $(aws iam list-roles --query 'Roles[?contains(RoleName,`mmt`)].RoleName' --output text 2>/dev/null); do
  info "IAM role 잔존: $R"
done
OIDC=$(aws iam list-open-id-connect-providers --query 'OpenIDConnectProviderList[].Arn' --output text 2>/dev/null)
[ -n "$OIDC" ] && info "OIDC provider 잔존: $OIDC" || ok "OIDC provider 0 (⚠️ 재런치 때 CI 배포 배선 재생성 필요)"
KP=$(aws ec2 describe-key-pairs --query 'KeyPairs[].KeyName' --output text 2>/dev/null)
[ -n "$KP" ] && info "keypair 잔존: $KP" || ok "keypair 0 (공개키 ~/.ssh/mmt-ec2.pub 로 재생성 가능)"
SG=$(aws ec2 describe-security-groups --filters Name=tag:Project,Values=mmt \
  --query 'SecurityGroups[].GroupName' --output text 2>/dev/null)
[ -n "$SG" ] && info "SG 잔존: $SG" || ok "mmt SG 0"
LG=$(aws logs describe-log-groups --query 'logGroups[].logGroupName' --output text 2>/dev/null)
[ -n "$LG" ] && info "CloudWatch 로그그룹: $LG" || ok "로그그룹 0"

echo
echo "=== 4. DNS (⚠️ 타 AWS 계정 Route53 — 이 프로파일로는 못 지운다) ==="
DNS=$(dig +short www.my-math-teacher.com A | tr '\n' ' ')
if [ -z "$DNS" ]; then
  ok "www.my-math-teacher.com A레코드 없음 (dangling DNS 해소)"
else
  bad "A레코드 살아있음 → $DNS
     반환된 IP 가 타 계정에 재할당되면 내 도메인으로 남의 서버가 서빙된다.
     도메인 소유 계정의 Route53 호스팅 영역에서 www A레코드를 직접 삭제할 것.
     삭제 후 이 스크립트를 다시 돌려 dig 가 비는지 확인한다(권위 서버 캐시로 TTL 만큼 지연)."
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "판정: ✅ PASS — 시간당 과금 0. 남는 비용은 스냅샷 저장분뿐"
else
  echo "판정: ❌ FAIL — 위 ❌ 항목이 휴면 중에도 계속 과금되거나 위험으로 남는다"
fi
exit "$FAIL"
