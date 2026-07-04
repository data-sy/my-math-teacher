#!/usr/bin/env bash
#
# infra/terraform/run-log.sh — M4 배포/측정 런 텔레메트리 하네스
#
# 각 phase 에서 호출해 시간·용량·메모리·부하 메트릭을 run-logs/<UTC>/ 에 쌓는다.
# 나중에 docs/benchmark/milestone-4-run-report.md 로 큐레이션할 원천 로그.
#
# ⚠️ 비용은 실시간 정확값이 없다(Cost Explorer 24h+ 지연). 여기선 리소스-시간
#    원장(launch/destroy 타임스탬프)으로 *추정*하고, 다음날 Cost Explorer 로 실제 대조.
# ⚠️ 미검증(작성만) — switch-backend.sh 처럼 동작 검증은 실제 런 때.
# 🔒 시크릿 0: EIP/endpoint 는 로그에 남지만 AWS 자격·DB 비번은 남기지 않는다.
#
# 사용:
#   ./run-log.sh init                 # 런 디렉토리 생성 + 메타(git sha, plan 리소스 수)
#   ./run-log.sh mark <phase>         # 타임라인에 타임스탬프 한 줄 (예: seed-start)
#   ./run-log.sh note "<text>"        # 임의 메모 한 줄
#   ./run-log.sh tf-apply             # time terraform apply + outputs/state 캡처 (원장 launch 기록)
#   ./run-log.sh tf-destroy           # time terraform destroy (원장 destroy 기록 → 비용 창 닫기)
#   ./run-log.sh ec2 <EIP> [label]    # SSH 스냅샷: free/swap/df/docker stats/부팅시간 (컷오버 땐 label=cutover)
#   ./run-log.sh cost                 # 원장 × 서울 공시요율 → 추정 크레딧 소모
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGROOT="$HERE/run-logs"
CURPTR="$LOGROOT/.current"
SSH_KEY="${MMT_SSH_KEY:-$HOME/.ssh/mmt-ec2}"

# --- ap-northeast-2 (서울) on-demand 근사 요율 (USD/hr, storage 는 /GB-월) --------
# ⚠️ 근사값 — 정확 크레딧은 Cost Explorer 로 대조. 요율은 변동하므로 참고선.
RATE_EC2_T3_MICRO="0.0130"   # t3.micro
RATE_RDS_T3_MICRO="0.026"    # db.t3.micro MySQL Single-AZ
RATE_IPV4="0.005"            # 공인 IPv4 (연결 여부 무관, 2024-02~)
RATE_EBS_GP3_GB_MO="0.0912"  # gp3 스토리지 /GB-월
RATE_RDS_STORAGE_GB_MO="0.131" # RDS gp2 스토리지 /GB-월

ts()  { date -u +%Y-%m-%dT%H:%M:%SZ; }
epoch() { date -u +%s; }

curdir() {
  [ -f "$CURPTR" ] || { echo "run-log: init 먼저 실행하라 (활성 런 없음)" >&2; exit 1; }
  cat "$CURPTR"
}

cmd_init() {
  local slug; slug="$(ts | tr ':' '-')"
  local d="$LOGROOT/$slug"
  mkdir -p "$d"
  echo "$d" > "$CURPTR"
  {
    echo "run_start_utc=$(ts)"
    echo "git_sha=$(git -C "$HERE" rev-parse --short HEAD 2>/dev/null || echo unknown)"
    echo "git_branch=$(git -C "$HERE" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    echo "region=ap-northeast-2"
    echo "host_user=$(whoami)"
  } > "$d/meta.txt"
  printf 'utc\tphase\n' > "$d/timeline.tsv"
  printf '%s\tinit\n' "$(ts)" >> "$d/timeline.tsv"
  echo "run-log: 새 런 → $d"
}

cmd_mark() {
  local d; d="$(curdir)"; local phase="${1:?phase 인자 필요}"
  printf '%s\t%s\n' "$(ts)" "$phase" >> "$d/timeline.tsv"
  echo "run-log: mark [$phase] @ $(ts)"
}

cmd_note() {
  local d; d="$(curdir)"; local text="${1:?메모 텍스트 필요}"
  printf '%s\t%s\n' "$(ts)" "$text" >> "$d/notes.txt"
  echo "run-log: note 기록"
}

cmd_tf_apply() {
  local d; d="$(curdir)"
  cmd_mark apply-start
  printf 'launch_epoch=%s\nlaunch_utc=%s\n' "$(epoch)" "$(ts)" >> "$d/cost-ledger.txt"
  # time 을 파일로 — apply 출력은 tee 로 로그+화면 동시.
  { time terraform apply ; } 2> "$d/tf-apply.time" | tee "$d/tf-apply.log"
  cmd_mark apply-end
  terraform output -json         > "$d/tf-outputs.json"  2>/dev/null || echo "run-log: outputs 캡처 실패(무시)"
  terraform state list           > "$d/tf-state.txt"     2>/dev/null || true
  echo "run-log: apply 캡처 완료 → $d (outputs/state/time)"
}

cmd_tf_destroy() {
  local d; d="$(curdir)"
  cmd_mark destroy-start
  { time terraform destroy ; } 2> "$d/tf-destroy.time" | tee "$d/tf-destroy.log"
  printf 'destroy_epoch=%s\ndestroy_utc=%s\n' "$(epoch)" "$(ts)" >> "$d/cost-ledger.txt"
  cmd_mark destroy-end
  echo "run-log: destroy 캡처 완료 — 비용 창 닫힘. './run-log.sh cost' 로 추정치 확인."
}

# EC2 호스트 스냅샷: 메모리·swap·디스크·컨테이너. 컷오버(blue+green 2 JVM 공존) 순간이 §9.4 핵심.
cmd_ec2() {
  local d; d="$(curdir)"
  local eip="${1:?EIP 인자 필요}"; local label="${2:-adhoc}"
  local out="$d/ec2-${label}-$(ts | tr ':' '-').txt"
  cmd_mark "ec2-snapshot:$label"
  # 원격 명령은 실패해도 스냅샷 전체를 죽이지 않게 각자 || true. 자격/비번은 안 뽑는다.
  ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
      "ec2-user@${eip}" 'bash -s' > "$out" 2>&1 <<'REMOTE'
    echo "===== uname ====="; uname -a
    echo "===== free -m (메모리 + swap) ====="; free -m
    echo "===== swapon ====="; swapon --show || echo "(swap 없음)"
    echo "===== df -h (디스크) ====="; df -h
    echo "===== docker stats (--no-stream: 컨테이너 메모리/CPU) ====="; docker stats --no-stream 2>/dev/null || echo "(docker 미기동/권한)"
    echo "===== docker system df (이미지/볼륨 용량) ====="; docker system df 2>/dev/null || true
    echo "===== docker images ====="; docker images 2>/dev/null || true
    echo "===== 부팅 시간 (systemd-analyze) ====="; systemd-analyze 2>/dev/null || true
    echo "===== cloud-init 결과 tail ====="; sudo tail -n 20 /var/log/cloud-init-output.log 2>/dev/null || echo "(로그 없음)"
REMOTE
  local rc=$?
  if [ $rc -ne 0 ]; then echo "run-log: EC2 SSH 스냅샷 실패(rc=$rc) — 키($SSH_KEY)/EIP/SG 22 확인. 로그: $out"; fi
  echo "run-log: EC2 스냅샷[$label] → $out"
}

cmd_cost() {
  local d; d="$(curdir)"; local led="$d/cost-ledger.txt"
  [ -f "$led" ] || { echo "run-log: 비용 원장 없음(apply 안 함?)"; exit 1; }
  # shellcheck disable=SC1090
  local launch destroy
  launch="$(grep -oP 'launch_epoch=\K[0-9]+' "$led" | tail -1 || true)"
  destroy="$(grep -oP 'destroy_epoch=\K[0-9]+' "$led" | tail -1 || echo "$(epoch)")"
  [ -n "$launch" ] || { echo "run-log: launch_epoch 없음"; exit 1; }
  local secs=$(( destroy - launch ))
  local hours; hours="$(awk "BEGIN{printf \"%.3f\", $secs/3600}")"
  local hourly; hourly="$(awk "BEGIN{printf \"%.4f\", $RATE_EC2_T3_MICRO+$RATE_RDS_T3_MICRO+$RATE_IPV4}")"
  local est;    est="$(awk "BEGIN{printf \"%.3f\", $hours*$hourly}")"
  {
    echo "# 추정 비용 (근사 — Cost Explorer 로 다음날 대조)"
    echo "구간: $hours 시간 ($secs 초)"
    echo "시간당(EC2+RDS+IPv4): \$$hourly/hr"
    echo "  = EC2 t3.micro \$$RATE_EC2_T3_MICRO + RDS \$$RATE_RDS_T3_MICRO + IPv4 \$$RATE_IPV4"
    echo "인스턴스-시간 추정: \$$est"
    echo "(+ 스토리지: EBS 30GB·RDS 20GB 는 월 단위 프로레이트 — 짧은 런이면 수 센트. 요율 EBS \$$RATE_EBS_GP3_GB_MO/GB-월, RDS \$$RATE_RDS_STORAGE_GB_MO/GB-월)"
    echo "→ 유료 플랜이라 이 금액이 \$200 크레딧에서 선차감."
  } | tee "$d/cost-estimate.txt"
}

case "${1:-}" in
  init)        cmd_init ;;
  mark)        shift; cmd_mark "$@" ;;
  note)        shift; cmd_note "$@" ;;
  tf-apply)    cmd_tf_apply ;;
  tf-destroy)  cmd_tf_destroy ;;
  ec2)         shift; cmd_ec2 "$@" ;;
  cost)        cmd_cost ;;
  *) echo "사용: $0 {init|mark <phase>|note <text>|tf-apply|tf-destroy|ec2 <EIP> [label]|cost}" >&2; exit 2 ;;
esac
