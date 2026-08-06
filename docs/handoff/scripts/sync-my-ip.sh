#!/usr/bin/env bash
# 공인 IP 가 바뀌어 SSH 가 막혔을 때 SG 의 SSH 인그레스를 현재 IP 로 맞춘다 (재발성 작업).
#
#   진단만 (기본, AWS 자격 불요):  bash ~/sync-my-ip.sh
#   실제 적용:                     bash ~/sync-my-ip.sh --apply
#
# 왜 재발하나: network.tf 의 SSH 인그레스가 var.my_ip/32 ("SSH from my IP only") 라
# 집·카페·테더링 등으로 공인 IP 가 바뀌면 SSH 가 통째로 timeout 난다. 서비스(80/443)는
# 0.0.0.0/0 이라 멀쩡해서, "사이트는 되는데 SSH·배포만 안 되는" 헷갈리는 모습이 된다.
#
# 판정 기준 = tfvars 가 아니라 "SSH 가 실제로 되는가" 다.
#   terraform.tfvars 는 *희망 상태*일 뿐이고, apply 전에는 AWS 의 SG 와 다를 수 있다.
#   (2026-08-07 실제로 이 드리프트가 났다 — tfvars 만 새 IP, SG 는 옛 IP → SSH 계속 막힘.)
#
# 무엇을 바꾸나(--apply): terraform.tfvars 의 my_ip 한 줄 + SG 인그레스 규칙 1건(-target 한정).
# 다른 리소스는 건드리지 않는다. 가역이고 서빙(80/443)과 무관하다.
#
# 관련: SSH 가 열린 뒤 SSM 에이전트·등록까지 복구 = ssm-recover.sh
#       근본 해법(이 규칙 자체를 없애기) = docs/backlog/ssh-ingress-ip-pinning-to-session-manager.md
# 끝나면 지워도 됨: rm ~/sync-my-ip.sh

set -uo pipefail

MODE="${1:-check}"
TFDIR=~/my-math-teacher/infra/terraform
TFVARS="$TFDIR/terraform.tfvars"
TARGET='aws_vpc_security_group_ingress_rule.ssh'
PLANFILE=/tmp/mmt-sync-my-ip.tfplan
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

ssh_works() {
  ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
      "ec2-user@$HOST" 'echo ok' >/dev/null 2>&1
}

echo "=============================================="
echo " 공인 IP ↔ SG SSH 규칙 동기화"
echo "=============================================="

# ---------- 1. 진짜 증상부터 (자격 불요) ----------
echo
echo "[1] SSH 실제 도달 확인 — 이게 판정 기준이다"
if ssh_works; then
  ok "SSH 접속됨 — 할 일 없음"
  echo
  echo "=============================================="
  echo " ✅ 동기화 불필요"
  echo "=============================================="
  exit 0
fi
bad "SSH timeout — SG 가 현재 IP 를 막고 있거나, IP 외 다른 문제"

# ---------- 2. 희망 상태(tfvars) 와 현재 IP 대조 ----------
echo
echo "[2] 현재 공인 IP 와 terraform.tfvars 대조"
[ -f "$TFVARS" ] || { bad "terraform.tfvars 없음: $TFVARS"; exit 1; }
CUR=$(curl -s --max-time 10 https://checkip.amazonaws.com | tr -d '[:space:]')
OLD=$(grep -E '^\s*my_ip' "$TFVARS" | sed 's/.*=//' | tr -d ' "'"'"'')
[ -n "$CUR" ] || { bad "공인 IP 조회 실패 (네트워크 확인)"; exit 1; }

if [ "$CUR" = "$OLD" ]; then
  echo "      tfvars 는 이미 현재 IP 다 → **드리프트**: 갱신은 됐고 apply 만 안 됐다"
  NEED_TFVARS=0
else
  echo "      tfvars 가 현재 IP 와 다르다 (값은 출력하지 않음) → 갱신 + apply 둘 다 필요"
  NEED_TFVARS=1
fi

if [ "$MODE" != "--apply" ]; then
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " 🔍 진단만 했다 — 아무것도 바꾸지 않았다 (tfvars 포함)"
  echo "    적용하려면:  bash ~/sync-my-ip.sh --apply"
  echo "=============================================="
  exit 0
fi

# ---------- 3. tfvars 갱신 (필요할 때만) ----------
echo
echo "[3] terraform.tfvars"
if [ "$NEED_TFVARS" = "1" ]; then
  cp "$TFVARS" "$TFVARS.bak.$(date +%Y%m%d%H%M%S)"
  awk -v ip="$CUR" '
    /^[ \t]*my_ip[ \t]*=/ { printf("my_ip = \"%s\"\n", ip); next }
    { print }
  ' "$TFVARS" > "$TFVARS.tmp" && mv "$TFVARS.tmp" "$TFVARS"
  NEW=$(grep -E '^\s*my_ip' "$TFVARS" | sed 's/.*=//' | tr -d ' "'"'"'')
  [ "$NEW" = "$CUR" ] && ok "my_ip 갱신 (원본은 .bak 으로 보존)" || { bad "갱신 실패 — my_ip 줄 형식 확인"; exit 1; }
else
  ok "이미 현재 IP — 수정 불요"
fi

# ---------- 4. 임시자격 → plan → apply ----------
echo
echo "[4] AWS 임시자격 (MFA 코드 입력)"
cd "$TFDIR" || exit 1
# shellcheck disable=SC1091
source ./tf-assume.sh
aws sts get-caller-identity >/dev/null 2>&1 && ok "자격 획득" || { bad "자격 획득 실패"; exit 1; }

echo
echo "[5] terraform plan/apply — SSH 인그레스 규칙 1건만"
rm -f "$PLANFILE"
terraform plan -target="$TARGET" -out="$PLANFILE" -input=false 2>&1 \
  | grep -E "will be|must be|No changes|Plan:|Error" | sed 's/^/      /'
[ -f "$PLANFILE" ] || { bad "plan 실패"; exit 1; }
if terraform apply -input=false "$PLANFILE" 2>&1 | tail -3 | sed 's/^/      /'; then
  ok "apply 완료"
else
  bad "apply 실패"; exit 1
fi

# ---------- 6. 증상 재확인 ----------
echo
echo "[6] SSH 재확인 (SG 반영까지 잠깐 걸릴 수 있음)"
SSHOK=0
for _ in 1 2 3 4; do
  ssh_works && { SSHOK=1; break; }
  sleep 5
done
if [ "$SSHOK" = "1" ]; then
  ok "SSH 접속 성공 — 복구됨"
else
  bad "SSH 여전히 실패 — IP 문제가 아니었을 수 있다(인스턴스 상태·키·sshd 확인)"
fi

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$SSHOK" = "1" ]; then
  echo " ✅ 동기화 완료. SSM 등록까지 복구하려면:  bash ~/ssm-recover.sh --apply"
else
  echo " ⛔ 미완 — 위 ❌ 확인"
fi
echo "=============================================="
