#!/usr/bin/env bash
# TLS 인증서 자동갱신 재등록 (Let's Encrypt 90일)
#
#   점검만 (읽기 전용, 기본):  bash ~/mmt-tls.sh
#   실제 등록:                 bash ~/mmt-tls.sh --apply
#
# 왜: 2026-08-05 재런치가 인증서는 재발급했지만 **갱신 경로를 안 옮겼다**.
# 실측(2026-08-15) 결과 호스트에 systemd 타이머 0·유닛 0·cron 0·certbot 바이너리 없음 —
# 갱신을 돌릴 주체가 존재하지 않는다. 만료 2026-11-03 에 사이트 전체가 브라우저 경고로 막힌다.
# 정본 = docs/backlog/tls-cert-renewal-timer-after-relaunch.md
#
# 방식 = 백로그 A안(권장): docker certbot + systemd 타이머.
#   호스트에 패키지를 안 늘린다(minimal AMI 유지) + 재런치 때 실제로 통한 명령과 동일.
#   M6 이 깔아 둔 webroot 경로(/var/www/certbot + front nginx 의 acme-challenge 예외)를
#   그대로 재사용하므로 **무중단 갱신**이다(80 을 내리지 않는다).
#   근거 절차 = docs/specs/m6/first-deploy-runbook.md §갱신(R5)
#
# ⚠️ 갱신 후 nginx 를 reload 해야 새 인증서를 집는다 — 유닛에 포함했다(구 M6 절차가 하던 것).
# 되돌리기: sudo systemctl disable --now certbot-renew.timer && sudo rm /etc/systemd/system/certbot-renew.*
# 끝나면 지워도 됨: rm ~/mmt-tls.sh

set -uo pipefail
MODE="${1:-check}"
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
DOMAIN=www.my-math-teacher.com

echo "=============================================="
if [ "$MODE" = "--apply" ]; then
  echo " TLS 자동갱신 등록 — ⚠️  변경 모드"
else
  echo " TLS 자동갱신 — 점검 모드 (읽기 전용)"
fi
echo "=============================================="

ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 -o LogLevel=ERROR \
    "$SSHU@$HOST" "bash -s -- $MODE $DOMAIN" <<'REMOTE'
set -u
MODE=${1:-check}
DOMAIN=${2:-www.my-math-teacher.com}
pass=0; fail=0
ok(){ echo "  ✅ $1"; pass=$((pass+1)); }
bad(){ echo "  ❌ $1"; fail=$((fail+1)); }
note(){ echo "     $1"; }

echo
echo "[1] 현재 인증서"
CERT=/etc/letsencrypt/live/$DOMAIN/fullchain.pem
if sudo test -f "$CERT"; then
  ok "있음 — 만료 $(sudo openssl x509 -enddate -noout -in "$CERT" | cut -d= -f2)"
  sudo openssl x509 -checkend $((30*86400)) -noout -in "$CERT" >/dev/null 2>&1 \
    && note "30일 내 만료 아님" || bad "30일 내 만료 — 등록 후 즉시 1회 갱신 필요"
else
  bad "인증서 파일 없음 ($CERT)"; echo "⛔ 중단"; exit 1
fi

echo
echo "[2] 무중단 갱신 전제 (webroot 경로)"
sudo test -d /var/www/certbot && ok "/var/www/certbot 존재" || bad "/var/www/certbot 없음 — webroot 갱신 불가"
docker ps --format '{{.Names}}' | grep -qx mmt-front && ok "mmt-front 컨테이너 가동 중" || bad "mmt-front 없음 — reload 대상 부재"
# nginx 가 acme-challenge 를 리다이렉트 예외로 서빙하는지 = 갱신 성패의 핵심
CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://$DOMAIN/.well-known/acme-challenge/probe-$$" 2>/dev/null)
[ "$CODE" = "404" ] && ok "acme-challenge 경로가 80 에서 서빙됨 (404 = 리다이렉트 예외 정상)" \
                    || bad "acme-challenge 응답 $CODE — 301 이면 리다이렉트에 먹혀 갱신 실패한다"

echo
echo "[3] 현재 갱신 경로"
T=$(systemctl list-timers --all 2>/dev/null | grep -ci certbot || true)
C=$( (sudo crontab -l 2>/dev/null; cat /etc/crontab 2>/dev/null; cat /etc/cron.d/* 2>/dev/null) | grep -ci certbot || true)
note "systemd 타이머 $T 건 · cron $C 건"
# ⚠️ --apply 모드에서 이 항목은 "조치 전 상태"라 실패로 세면 안 된다.
# (2026-08-19: 전부 성공했는데 마지막 판정이 ⛔ 미완으로 뜨는 버그가 있었다)
if [ "$T" -gt 0 ] || [ "$C" -gt 0 ]; then
  ok "이미 갱신 경로 있음"
elif [ "$MODE" = "--apply" ]; then
  note "갱신 경로 없음 → 아래에서 등록한다"
else
  bad "갱신 경로 없음 — 만료일에 사이트 전체가 막힌다"
fi

if [ "$MODE" != "--apply" ]; then
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " 🔍 점검만 함 — 아무것도 바꾸지 않았다"
  echo "    등록하려면:  bash ~/mmt-tls.sh --apply"
  echo "=============================================="
  exit 0
fi

echo
echo "[4] systemd 유닛 작성"
sudo tee /etc/systemd/system/certbot-renew.service >/dev/null <<UNIT
[Unit]
Description=Renew Let's Encrypt certificates (docker certbot, webroot) and reload nginx
Documentation=file:///home/ec2-user/README-tls.md
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
# webroot 갱신 — front 80 이 살아있는 채로 도므로 무중단.
# nginx.conf 의 /.well-known/acme-challenge/ 예외가 이 경로를 서빙한다.
ExecStart=/usr/bin/docker run --rm \\
  -v /etc/letsencrypt:/etc/letsencrypt \\
  -v /var/www/certbot:/var/www/certbot \\
  certbot/certbot renew --webroot -w /var/www/certbot --quiet
# 갱신되어도 nginx 가 reload 하지 않으면 옛 인증서를 계속 물고 있다.
# 갱신이 없었어도 reload 는 무해하므로 무조건 실행한다.
ExecStartPost=-/usr/bin/docker exec mmt-front nginx -s reload
UNIT

sudo tee /etc/systemd/system/certbot-renew.timer >/dev/null <<UNIT
[Unit]
Description=Weekly Let's Encrypt renewal check

[Timer]
# 주 1회. Let's Encrypt 는 만료 30일 전부터 갱신되므로 주간이면 4회 이상 기회가 있다.
OnCalendar=Mon 03:30
# 같은 시각에 몰리지 않게 분산(LE 권장)
RandomizedDelaySec=3600
# 인스턴스가 꺼져 있어 놓친 회차는 부팅 후 실행
Persistent=true

[Install]
WantedBy=timers.target
UNIT
ok "certbot-renew.service · certbot-renew.timer 작성"

echo
echo "[5] 타이머 활성화"
sudo systemctl daemon-reload && ok "daemon-reload" || bad "daemon-reload 실패"
sudo systemctl enable --now certbot-renew.timer >/dev/null 2>&1 \
  && ok "타이머 enable + start" || bad "타이머 활성화 실패"
systemctl is-enabled certbot-renew.timer >/dev/null 2>&1 \
  && ok "부팅 시 자동시작(enabled) — 재부팅해도 유지" || bad "enabled 아님"
note "다음 실행: $(systemctl list-timers certbot-renew.timer --all 2>/dev/null | awk 'NR==2{print $1,$2,$3}')"

echo
echo "[6] dry-run 실증 — 이게 통과해야 진짜 끝난 것이다"
OUT=$(sudo docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot --dry-run 2>&1)
if echo "$OUT" | grep -q "simulated renewal"; then
  ok "dry-run 성공 — \"$(echo "$OUT" | grep -m1 -o 'The dry run was successful.*\|.*simulated renewals succeeded.*' | head -c 80)\""
else
  bad "dry-run 실패 — 아래 출력 확인"
  echo "$OUT" | tail -15 | sed 's/^/       /'
fi

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ TLS 자동갱신 등록 완료 (주 1회 · 무중단 webroot · nginx reload 포함)"
  echo "    확인: systemctl list-timers certbot-renew.timer"
else
  echo " ⛔ 미완 — 위 ❌ 확인. 타이머가 등록됐어도 dry-run 이 실패했다면 갱신은 안 도는 것이다"
fi
echo "=============================================="
REMOTE
