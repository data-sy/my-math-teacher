#!/usr/bin/env bash
# M7 재런치 GATE 2-a — terraform plan 검사 (읽기 전용, apply 안 함)
# 실행: bash ~/gate2a.sh
# 전제: 같은 셸에서 `source ./tf-assume.sh` 로 임시자격 주입 (GATE 1 통과 상태)
# 끝나면 지워도 됨: rm ~/gate2a.sh
#
# ⚠️ plan 산출물은 var 값(db_password)을 평문 임베드한다 → 리포 밖 ~/mmt-relaunch/ 에 둔다.

set -o pipefail

TFDIR=~/my-math-teacher/infra/terraform
OUT=~/mmt-relaunch
PLAN=$OUT/relaunch.tfplan
PJSON=$OUT/relaunch.tfplan.json

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

mkdir -p "$OUT" && chmod 700 "$OUT"
cd "$TFDIR" || { echo "⛔ $TFDIR 없음"; exit 1; }

echo "=============================================="
echo " GATE 2-a — terraform plan 검사"
echo "=============================================="
echo
echo "[1/5] plan 생성 (RDS 포함이라 30초~1분)"
if ! terraform plan -var use_localstack=false -out="$PLAN" > "$OUT/plan.log" 2>&1; then
  echo "  ❌ plan 실패 — 로그 마지막 30줄:"
  tail -30 "$OUT/plan.log"
  echo
  echo "⛔ GATE 2-a 미통과"
  exit 1
fi
chmod 600 "$PLAN"
ok "plan 생성됨 ($PLAN)"

terraform show -json "$PLAN" > "$PJSON" 2>/dev/null && chmod 600 "$PJSON"

# ---------- 액션 집계 ----------
echo
echo "[2/5] 변경 액션 집계"
CREATE=$(jq -r '[.resource_changes[]|select(.change.actions==["create"])|.address]|sort|.[]' "$PJSON")
DESTROY=$(jq -r '[.resource_changes[]|select(.change.actions|index("delete"))|.address]|sort|.[]' "$PJSON")
UPDATE=$(jq -r '[.resource_changes[]|select(.change.actions==["update"])|.address]|sort|.[]' "$PJSON")

NC=$(printf "%s\n" "$CREATE" | grep -c . )
ND=$(printf "%s\n" "$DESTROY" | grep -c . )
NU=$(printf "%s\n" "$UPDATE" | grep -c . )

echo "      create : $NC"; [ "$NC" -gt 0 ] && printf "        - %s\n" $CREATE
echo "      destroy: $ND"; [ "$ND" -gt 0 ] && printf "        - %s\n" $DESTROY
echo "      update : $NU"; [ "$NU" -gt 0 ] && printf "        - %s\n" $UPDATE

# ⚠️ 로케일별 sort 순서 차이(_ vs .)로 오탐이 났었다 → 양쪽 다 LC_ALL=C 로 정규화 (2026-08-05 수정)
EXPECTED=$(printf "%s\n" aws_db_instance.app aws_eip.app aws_eip_association.app aws_instance.app | LC_ALL=C sort)
GOT=$(printf "%s\n" $CREATE | LC_ALL=C sort)

[ "$GOT" = "$EXPECTED" ] \
  && ok "create 가 정확히 기대한 4개" \
  || bad "create 목록이 기대와 다름 (기대: aws_instance.app·aws_db_instance.app·aws_eip.app·aws_eip_association.app)"

[ "$ND" -eq 0 ] && ok "destroy 0" || bad "destroy $ND 개 — 잔존 리소스가 지워질 계획, 중단할 것"
[ "$NU" -eq 0 ] && ok "update 0" || echo "  ⚠️  update $NU 개 — 위 목록 확인 (치명 아닐 수 있음)"

# ---------- 스냅샷 배선 ----------
echo
echo "[3/5] 스냅샷 복원 배선"
SNAP=$(jq -r '.resource_changes[]|select(.address=="aws_db_instance.app")|.change.after.snapshot_identifier // "null"' "$PJSON")
echo "      snapshot_identifier = $SNAP"
[ "$SNAP" = "mmt-mothball-2026-07-31" ] \
  && ok "스냅샷에서 복원하도록 배선됨" \
  || bad "스냅샷 배선 없음/불일치 — 이대로 apply 하면 빈 DB 가 생긴다"

# ---------- RDS 주요 속성 ----------
echo
echo "[4/5] RDS 계획값 (스냅샷 실측: mysql 8.0.45 · 20GB · mmtadmin)"
jq -r '.resource_changes[]|select(.address=="aws_db_instance.app")|.change.after
       |"      identifier      : \(.identifier // "(unknown)")
      engine          : \(.engine // "?") \(.engine_version // "(after apply)")
      instance_class  : \(.instance_class // "?")
      allocated_storage: \(.allocated_storage // "(after apply)")
      db_name         : \(.db_name // "(스냅샷 값 사용)")
      username        : \(.username // "(스냅샷 값 사용)")
      publicly_accessible: \(.publicly_accessible)
      multi_az        : \(.multi_az)"' "$PJSON"

EV=$(jq -r '.resource_changes[]|select(.address=="aws_db_instance.app")|.change.after.engine_version // "unknown"' "$PJSON")
case "$EV" in
  8.0*|unknown) ok "engine_version=$EV — 스냅샷 8.0.45 와 호환" ;;
  *)            bad "engine_version=$EV — 스냅샷 8.0.45 와 불일치" ;;
esac

# ---------- 비번 출처 ----------
echo
echo "[5/5] db_password 주입 확인 (값 미출력, sha256 앞 12자만)"
TFV=$(grep -E '^[[:space:]]*db_password' "$TFDIR/terraform.tfvars" 2>/dev/null | sed -E 's/.*=[[:space:]]*"?([^"]*)"?[[:space:]]*$/\1/')
if [ -n "$TFV" ]; then
  echo "      tfvars db_password sha256[0:12] = $(printf %s "$TFV" | shasum -a 256 | cut -c1-12)"
  echo "      길이 = ${#TFV}"
  ok "tfvars 에서 비번 공급됨 (이 값이 복원 후 마스터 비번이 된다)"
  echo "      → Phase 4 의 ~/mmt-backend.env RDS_PASSWORD 와 Phase 5 의 mysql -p 에 같은 값을 쓸 것"
else
  bad "tfvars 에 db_password 없음 — apply 가 대화형으로 비번을 물어본다"
fi

# ---------- 판정 ----------
echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ GATE 2-a 통과"
  echo
  echo " 다음(승인 후): 저장된 이 plan 을 그대로 apply"
  echo "   cd $TFDIR"
  echo "   terraform apply $PLAN"
  echo " (plan 파일을 재사용해야 검토한 것과 실행되는 것이 동일하다)"
else
  echo " ⛔ GATE 2-a 미통과 — apply 금지. 위 ❌ 항목 확인"
fi
echo " 전체 plan 로그: $OUT/plan.log"
echo "=============================================="
