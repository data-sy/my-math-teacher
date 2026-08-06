#!/usr/bin/env bash
# M7 재런치 Phase 4a — SSH 열기 (SG 의 my_ip 갱신)
# 실행: bash ~/phase4a.sh
# 전제: 같은 셸에 임시자격(source ./tf-assume.sh)
# 끝나면 지워도 됨: rm ~/phase4a.sh
#
# tfvars 의 my_ip 는 이미 112.223.174.134 로 갱신돼 있다(어시스턴트가 수정, 백업 =
# ~/mmt-relaunch/terraform.tfvars.bak.20260805). 이 스크립트는 그 변경을 AWS 에 반영한다.
# 바뀌는 것: aws_vpc_security_group_ingress_rule.ssh 1개만 replace (network.tf:54 유일 사용처).

TFDIR=~/my-math-teacher/infra/terraform
OUT=~/mmt-relaunch
PLAN=$OUT/sg.tfplan
HOST_IP=15.164.145.106

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

mkdir -p "$OUT" && chmod 700 "$OUT"
cd "$TFDIR" || { echo "⛔ $TFDIR 없음"; exit 1; }

echo "=============================================="
echo " Phase 4a — SSH 접근 열기 (SG my_ip 갱신)"
echo "=============================================="

echo
echo "[1/4] 현재 공인 IP 가 tfvars 와 맞나"
NOW=$(curl -s --max-time 5 https://checkip.amazonaws.com | tr -d '[:space:]')
TFV=$(grep -E '^[[:space:]]*my_ip' terraform.tfvars | sed -E 's/.*=[[:space:]]*"?([^"]*)"?[[:space:]]*$/\1/')
echo "      현재 IP : $NOW"
echo "      tfvars  : $TFV"
[ "$NOW" = "$TFV" ] && ok "일치" || { bad "불일치 — tfvars my_ip 를 $NOW 로 고쳐야 한다"; echo; echo "⛔ 중단"; exit 1; }

echo
echo "[2/4] plan — ssh 규칙 1개만 바뀌어야 한다"
if ! terraform plan -var use_localstack=false -out="$PLAN" > "$OUT/sg-plan.log" 2>&1; then
  bad "plan 실패"; tail -25 "$OUT/sg-plan.log"; echo; echo "⛔ 중단"; exit 1
fi
chmod 600 "$PLAN"
terraform show -json "$PLAN" > "$OUT/sg.tfplan.json" && chmod 600 "$OUT/sg.tfplan.json"

CHANGED=$(jq -r '[.resource_changes[]|select(.change.actions!=["no-op"])|.address]|sort|.[]' "$OUT/sg.tfplan.json")
NCH=$(printf "%s\n" "$CHANGED" | grep -c .)
echo "      변경 대상 ($NCH):"; [ "$NCH" -gt 0 ] && printf "        - %s\n" $CHANGED
ACTIONS=$(jq -r '.resource_changes[]|select(.address=="aws_vpc_security_group_ingress_rule.ssh")|.change.actions|join("+")' "$OUT/sg.tfplan.json")
echo "      ssh 규칙 액션: ${ACTIONS:-(변경 없음)}"

if [ "$NCH" -eq 1 ] && [ "$CHANGED" = "aws_vpc_security_group_ingress_rule.ssh" ]; then
  ok "ssh 규칙 1개만 변경 — 다른 리소스 무영향"
else
  bad "예상 밖 변경 — 위 목록 확인 후 중단할 것"
  echo; echo "⛔ 중단 (apply 안 함)"; exit 1
fi

echo
echo "[3/4] apply"
terraform apply "$PLAN" 2>&1 | tail -15

echo
echo "[4/4] SSH 도달 확인 (최대 60초)"
for i in 1 2 3 4 5 6; do
  if nc -z -G 5 "$HOST_IP" 22 2>/dev/null; then
    ok "22/tcp 열림"; SSHOK=1; break
  fi
  echo "      [$i] 아직 닫힘 — 10초 후 재시도"; sleep 10
done
[ -n "$SSHOK" ] || bad "22/tcp 여전히 닫힘 — SG 반영 지연이거나 인스턴스 부팅 중"

if [ -n "$SSHOK" ]; then
  echo
  echo "      실제 로그인 확인:"
  ssh -i ~/.ssh/mmt-ec2 -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
      ec2-user@"$HOST_IP" 'echo "      ✔ 로그인 성공 — $(hostname), docker $(docker --version 2>/dev/null || echo 미설치)"' \
    && ok "SSH 로그인 성공" || bad "포트는 열렸으나 로그인 실패 (키/유저 확인)"
fi

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 4a 통과 — 다음: bash ~/mkenv.sh (env-file 생성)"
else
  echo " ⛔ Phase 4a 미통과 — 로그: $OUT/sg-plan.log"
fi
echo "=============================================="
