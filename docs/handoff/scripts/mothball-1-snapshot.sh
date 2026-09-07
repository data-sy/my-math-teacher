#!/usr/bin/env bash
# mothball 1/3 — RDS 최종 스냅샷 확보 + 재런치 앵커 캡처 (파괴 전 필수 선행)
#
# 런북 §4-1·§4-3 (docs/handoff/🤖-M7-인프라-티어다운-재런치.md).
# 이 스크립트는 **아무것도 지우지 않는다.** 스냅샷 생성 + read-only 조회뿐.
#
# 선행: bash ~/mmt-aws-session.sh <MFA 6자리>   (mmt-session 프로파일, 1시간)
# 실행: bash docs/handoff/scripts/mothball-1-snapshot.sh

set -uo pipefail

PROFILE=mmt-session
REGION=ap-northeast-2
DB_ID=mmt-db
SNAP=mmt-mothball-2026-09-08
OUT_DIR="$HOME/mmt-mothball-2026-09-08"
FAIL=0

aws() { command aws --profile "$PROFILE" --region "$REGION" "$@"; }
ok()   { echo "✅ $*"; }
bad()  { echo "❌ $*"; FAIL=1; }
warn() { echo "⚠️  $*"; }

mkdir -p "$OUT_DIR"
echo "=== 0. AWS 세션 ==="
ARN=$(aws sts get-caller-identity --query Arn --output text 2>&1)
if [[ "$ARN" == arn:* ]]; then ok "$ARN"; else
  bad "세션 없음/만료 — 먼저 'bash ~/mmt-aws-session.sh <MFA코드>' ($ARN)"; exit 1
fi

echo
echo "=== 1. 현재 RDS 상태 ==="
read -r STATUS ENGINE VER STORAGE < <(aws rds describe-db-instances \
  --db-instance-identifier "$DB_ID" \
  --query 'DBInstances[0].[DBInstanceStatus,Engine,EngineVersion,AllocatedStorage]' \
  --output text 2>/dev/null)
if [ -z "${STATUS:-}" ]; then bad "$DB_ID 조회 실패 — 이미 삭제됐나?"; exit 1; fi
ok "$DB_ID: $STATUS · $ENGINE $VER · ${STORAGE}GB"
case "$VER" in
  8.4*) ok "엔진 8.4 — Extended Support 대상 아님 (이 스냅샷으로 복원해야 안전)" ;;
  8.0*) warn "엔진이 8.0 이다 — 이 스냅샷으로 복원하면 Extended Support 재편입 (USD 149 사건 재연). 업그레이드 후 스냅샷 권장" ;;
  *)    warn "예상 밖 엔진 버전: $VER" ;;
esac

echo
echo "=== 2. 재런치 앵커 캡처 (host 상태는 terraform 밖 · best-effort) ==="
INST=$(aws ec2 describe-instances \
  --filters Name=tag:Project,Values=mmt Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null)
if [ -n "$INST" ]; then
  CMD=$(aws ssm send-command --instance-ids $INST \
    --document-name AWS-RunShellScript \
    --parameters 'commands=[
      "echo === docker ps ===", "docker ps --format \"{{.Names}}\t{{.Image}}\t{{.Status}}\"",
      "echo === images ===", "docker images --format \"{{.Repository}}:{{.Tag}}\"",
      "echo === backend.env KEYS (값 미출력) ===", "cut -d= -f1 /home/ec2-user/mmt-backend.env 2>/dev/null | sort",
      "echo === active-backend.conf ===", "cat /home/ec2-user/active-backend.conf 2>/dev/null",
      "echo === cert lineage ===", "ls -1 /etc/letsencrypt/live 2>/dev/null || sudo ls -1 /etc/letsencrypt/live 2>/dev/null"
    ]' --query Command.CommandId --output text 2>/dev/null)
  if [ -n "${CMD:-}" ]; then
    sleep 8
    aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INST" \
      --query StandardOutputContent --output text > "$OUT_DIR/host-state.txt" 2>/dev/null
    if [ -s "$OUT_DIR/host-state.txt" ]; then ok "호스트 상태 → $OUT_DIR/host-state.txt"
    else warn "SSM 응답 비어 있음 — 호스트 상태 수동 확인 필요"; fi
  else warn "SSM send-command 실패 — 에이전트 상태 확인(ssm-recover.sh)"; fi
else warn "running 인스턴스 없음 — 호스트 캡처 건너뜀"; fi

# terraform 밖 자산 기록
{ echo "# mothball 2026-09-08 재런치 앵커"
  echo "snapshot        : $SNAP ($ENGINE $VER · ${STORAGE}GB)"
  echo "old_snapshot    : mmt-mothball-2026-07-31 (MySQL 8.0.45 — ⚠️ 복원 시 Extended Support 재편입)"
  echo "eip_at_teardown : $(aws ec2 describe-addresses --query 'Addresses[?Tags[?Value==`mmt-app-eip`]].PublicIp' --output text 2>/dev/null)"
  echo "dns_www_A       : $(dig +short www.my-math-teacher.com | tr '\n' ' ')"
  echo "instance_id     : ${INST:-none}"
  echo "captured_at     : $(date -u +%FT%TZ)"
} > "$OUT_DIR/anchors.txt"
ok "앵커 기록 → $OUT_DIR/anchors.txt"

echo
echo "=== 3. 스냅샷 생성 ==="
EXIST=$(aws rds describe-db-snapshots --db-snapshot-identifier "$SNAP" \
  --query 'DBSnapshots[0].Status' --output text 2>/dev/null)
if [ -n "${EXIST:-}" ] && [ "$EXIST" != "None" ]; then
  ok "이미 존재 ($SNAP · $EXIST) — 생성 건너뜀"
else
  if aws rds create-db-snapshot --db-instance-identifier "$DB_ID" \
       --db-snapshot-identifier "$SNAP" --output text >/dev/null 2>&1; then
    ok "생성 요청 완료 ($SNAP)"
  else
    bad "생성 실패 — 위 에러 확인"; exit 1
  fi
fi

echo
echo "=== 4. available 대기 (수 분 소요) ==="
if aws rds wait db-snapshot-available --db-snapshot-identifier "$SNAP" 2>/dev/null; then
  ok "wait 반환"
else
  bad "wait 실패/타임아웃 — 아래 상태 확인"
fi

echo
echo "=== 5. 검증 (exit code 말고 실제 상태로) ==="
read -r S_STATUS S_TYPE S_ENGINE S_VER S_GB S_TIME < <(aws rds describe-db-snapshots \
  --db-snapshot-identifier "$SNAP" \
  --query 'DBSnapshots[0].[Status,SnapshotType,Engine,EngineVersion,AllocatedStorage,SnapshotCreateTime]' \
  --output text 2>/dev/null)
[ "$S_STATUS" = "available" ] && ok "Status=available" || bad "Status=$S_STATUS (available 아님 — 2단계로 넘어가지 말 것)"
[ "$S_TYPE" = "manual" ]      && ok "SnapshotType=manual (인스턴스 삭제해도 남음)" || bad "SnapshotType=$S_TYPE"
ok "내용: $S_ENGINE $S_VER · ${S_GB}GB · $S_TIME"

echo
echo "=== 보유 수동 스냅샷 전체 ==="
aws rds describe-db-snapshots --snapshot-type manual \
  --query 'DBSnapshots[].[DBSnapshotIdentifier,EngineVersion,AllocatedStorage,Status]' --output table

echo
if [ "$FAIL" -eq 0 ]; then
  echo "판정: ✅ PASS — 스냅샷 확보 완료. 다음 = mothball-2-destroy.sh"
else
  echo "판정: ❌ FAIL — 위 ❌ 항목 해결 전 파괴 금지"
fi
exit "$FAIL"
