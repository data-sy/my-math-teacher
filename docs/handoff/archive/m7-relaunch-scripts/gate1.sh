#!/usr/bin/env bash
# M7 재런치 GATE 1 — 읽기 전용 확인 (AWS 뮤테이션 없음)
# 실행: bash ~/gate1.sh
# 전제: 같은 셸에서 `source ./tf-assume.sh` 로 임시자격이 주입돼 있을 것
# 끝나면 지워도 됨: rm ~/gate1.sh

REGION=ap-northeast-2
SNAP=mmt-mothball-2026-07-31
TFDIR=~/my-math-teacher/infra/terraform

pass=0
fail=0
ok()   { echo "  ✅ $1"; pass=$((pass+1)); }
bad()  { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
echo " GATE 1 — AWS 자격 세션 & 스냅샷 확인"
echo "=============================================="

# ---------- ① 자격 ----------
echo
echo "[1/3] AWS 자격"
ARN=$(aws sts get-caller-identity --query Arn --output text 2>&1)
if [ $? -ne 0 ]; then
  bad "자격 없음 — 'source ./tf-assume.sh' 먼저 실행"
  echo "      $ARN"
else
  echo "      $ARN"
  case "$ARN" in
    *assumed-role/mmt-terraform-admin/terraform*)
      ok "mmt-terraform-admin 롤로 assume 됨" ;;
    *)
      bad "기대한 롤이 아님 (assumed-role/mmt-terraform-admin/terraform)" ;;
  esac
fi

# ---------- ② 스냅샷 ----------
echo
echo "[2/3] 스냅샷 $SNAP"
SNAPINFO=$(aws rds describe-db-snapshots \
  --db-snapshot-identifier "$SNAP" \
  --region "$REGION" \
  --query 'DBSnapshots[0].[Status,AllocatedStorage,Engine,EngineVersion,MasterUsername,SnapshotCreateTime]' \
  --output text 2>&1)

if [ $? -ne 0 ]; then
  bad "스냅샷 조회 실패 — 복원할 데이터가 없다는 뜻이면 전체 중단"
  echo "      $SNAPINFO"
else
  set -- $SNAPINFO
  echo "      Status         : $1"
  echo "      AllocatedStorage: $2 GB"
  echo "      Engine         : $3 $4"
  echo "      MasterUsername : $5"
  echo "      Created        : $6"
  [ "$1" = "available" ] && ok "Status=available" || bad "Status=$1 (available 아님)"
  [ "$2" = "20" ]        && ok "용량 20GB — database.tf 와 일치" || bad "용량 $2GB — database.tf 는 20 기대"
  [ "$5" = "mmtadmin" ]  && ok "마스터 유저 mmtadmin — variables.tf 와 일치" || bad "마스터 유저 $5 — mmtadmin 기대"
fi

# ---------- ③ 인프라가 실제로 비어 있나 ----------
echo
echo "[3/3] 재생성 대상이 정말 비어 있나 (mothball 상태 확인)"
EC2=$(aws ec2 describe-instances --region "$REGION" \
  --filters Name=tag:Project,Values=mmt Name=instance-state-name,Values=running,pending,stopped \
  --query 'length(Reservations[].Instances[])' --output text 2>/dev/null)
DB=$(aws rds describe-db-instances --db-instance-identifier mmt-db \
  --region "$REGION" --query 'DBInstances[0].DBInstanceStatus' --output text 2>&1)

echo "      살아있는 EC2(Project=mmt): ${EC2:-조회실패}"
[ "$EC2" = "0" ] && ok "EC2 없음 (예상대로)" || bad "EC2 ${EC2}개 존재 — 예상과 다름, 확인 필요"

case "$DB" in
  *DBInstanceNotFound*) ok "RDS mmt-db 없음 (예상대로)" ;;
  *) bad "RDS 가 이미 존재: $DB — apply 전에 확인 필요" ;;
esac

# ---------- 판정 ----------
echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ GATE 1 통과 — Phase 2 진행 가능"
else
  echo " ⛔ GATE 1 미통과 — 위 ❌ 항목 확인 후 재실행"
fi
echo "=============================================="
