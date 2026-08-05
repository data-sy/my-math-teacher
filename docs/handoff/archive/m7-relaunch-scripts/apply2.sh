#!/usr/bin/env bash
# M7 재런치 Phase 2 — terraform apply + GATE 2-b 실물 검증
# 실행: bash ~/apply2.sh
# 전제: GATE 2-a 통과 · 같은 셸에 임시자격(source ./tf-assume.sh)
#
# 🔴 이 스크립트는 AWS 리소스를 실제로 생성한다 (= 과금 재개).
#    저장된 plan 으로 apply 하므로 terraform 이 "yes" 를 되묻지 않는다 — 즉시 실행된다.
#    아래 5초 카운트다운 동안 Ctrl-C 로 취소 가능.
# 끝나면 지워도 됨: rm ~/apply2.sh

TFDIR=~/my-math-teacher/infra/terraform
OUT=~/mmt-relaunch
PLAN=$OUT/relaunch.tfplan
REGION=ap-northeast-2

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

cd "$TFDIR" || { echo "⛔ $TFDIR 없음"; exit 1; }
[ -f "$PLAN" ] || { echo "⛔ plan 파일 없음 — 먼저 bash ~/gate2a.sh"; exit 1; }

echo "=============================================="
echo " Phase 2 — 인프라 재생성 (EC2 · RDS · EIP)"
echo "=============================================="
echo
echo " 🔴 지금부터 실제 AWS 리소스를 만든다 (과금 재개)."
echo "    생성: aws_instance.app · aws_db_instance.app · aws_eip.app · aws_eip_association.app"
echo "    RDS 는 스냅샷 mmt-mothball-2026-07-31 에서 복원 (10분 내외 소요)"
echo
printf " 취소하려면 Ctrl-C — "
for i in 5 4 3 2 1; do printf "%d " "$i"; sleep 1; done
echo "시작"
echo

# ---------- apply ----------
echo "[1/4] terraform apply (저장된 plan)"
terraform apply "$PLAN" 2>&1 | tee "$OUT/apply.log"
echo "  (terraform exit=${PIPESTATUS[0]} — ⚠️ tee 가 가릴 수 있으므로 아래 실물 조회로 판정한다)"

# ---------- GATE 2-b: 실물 검증 ----------
echo
echo "[2/4] EC2 실물 확인"
EC2=$(aws ec2 describe-instances --region "$REGION" \
  --filters Name=tag:Project,Values=mmt Name=instance-state-name,Values=running,pending \
  --query 'Reservations[].Instances[].[InstanceId,State.Name,PublicIpAddress]' --output text 2>&1)
echo "      $EC2"
case "$EC2" in
  *running*) ok "EC2 running" ;;
  *pending*) echo "  ⏳ 아직 pending — 1분 뒤 재확인 필요" ;;
  *)         bad "EC2 확인 실패" ;;
esac

echo
echo "[3/4] RDS 실물 확인 (복원 중이면 여러 번 걸릴 수 있다)"
for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
  RDS=$(aws rds describe-db-instances --db-instance-identifier mmt-db --region "$REGION" \
    --query 'DBInstances[0].[DBInstanceStatus,Endpoint.Address,Endpoint.Port]' --output text 2>&1)
  STATUS=$(printf "%s" "$RDS" | awk '{print $1}')
  echo "      [$attempt] $RDS"
  [ "$STATUS" = "available" ] && break
  case "$RDS" in *DBInstanceNotFound*) break ;; esac
  sleep 60
done
[ "$STATUS" = "available" ] && ok "RDS available" || bad "RDS 가 아직 available 아님 (status=$STATUS) — 시간을 더 주고 재확인"

echo
echo "[4/4] EIP 실물 확인"
EIP=$(aws ec2 describe-addresses --region "$REGION" \
  --query 'Addresses[].[PublicIp,AllocationId,InstanceId]' --output text 2>&1)
echo "      $EIP"
case "$EIP" in
  *i-*) ok "EIP 가 인스턴스에 연결됨" ;;
  "")   bad "EIP 없음" ;;
  *)    bad "EIP 는 있으나 연결 안 됨 — eip_association 확인" ;;
esac

# ---------- 기록용 요약 ----------
echo
echo "=============================================="
echo " 📝 다음 Phase 가 참조할 값 (핸드오프 §6 에 기록)"
echo "----------------------------------------------"
echo " EC2 인스턴스 ID : $(printf "%s" "$EC2" | awk '{print $1}')"
echo " EC2 공인 IP     : $(printf "%s" "$EC2" | awk '{print $3}')"
echo " RDS 엔드포인트  : $(printf "%s" "$RDS" | awk '{print $2}')"
echo " RDS 포트        : $(printf "%s" "$RDS" | awk '{print $3}')"
echo " EIP             : $(printf "%s" "$EIP" | awk '{print $1}')"
echo "----------------------------------------------"
echo " ⚠️ RDS 엔드포인트가 예전과 다를 수 있다 →"
echo "    Phase 4 의 ~/mmt-backend.env RDS_HOST 를 반드시 위 값으로 갱신"
echo " ⚠️ Phase 3 = DNS A레코드를 위 EIP 로 재생성 (certbot 이 DNS 를 요구한다)"
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ GATE 2-b 통과 — Phase 3(DNS) 진행 가능"
else
  echo " ⛔ GATE 2-b 미통과 — 위 ❌ 확인. apply 로그: $OUT/apply.log"
fi
echo "=============================================="
