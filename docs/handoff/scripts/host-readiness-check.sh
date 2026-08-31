#!/usr/bin/env bash
# 프로덕션 EC2 호스트 결손 점검 — "minimal AMI 가 무엇을 빼먹었나"를 한 번에 본다
#
#   점검만 (읽기 전용, 기본):  bash ~/mmt-host.sh
#   mysql 클라이언트 설치:     bash ~/mmt-host.sh --apply
#
# 왜: 2026-08-05 재런치가 al2023-ami-minimal 을 집었고(백로그 ⭐
# ami-filter-picks-minimal-no-ssm-agent), 그 결손이 하나씩 시차를 두고 터지고 있다.
#   ① SSM 에이전트 부재  → CD 가 08-05~08-15 열흘간 사망 (dnf install 로 해소)
#   ② mysql 클라이언트 부재 → M8 ②③④ 전부 차단 (이 스크립트가 설치)
#   ③ certbot 갱신 타이머? → TLS 만료 2026-11-03. 미확인 (여기서 확인만 한다)
# 하나씩 터질 때마다 대응하지 말고 지금 전수로 본다.
#
# [갱신 2026-08-31] ①②③ 모두 해소됐고 근인(AMI 필터)도 제거됐다 —
#   ③ 은 2026-08-19 주간 certbot-renew.timer 등록으로 해소.
#   근인 = 백로그 ami-filter-picks-minimal-no-ssm-agent (✅ 해결).
#   이 스크립트는 "터진 걸 쫓는 용도"에서 **재런치 후 전수 점검 루틴**으로 성격이 바뀌었다.
#
# --apply 가 바꾸는 것은 mysql 클라이언트 설치 하나뿐이다(~15MB, 서버 아님).
# certbot·SSM 은 조치에 판단이 필요해 report-only 로 둔다 — 결과 보고 따로 정한다.
#
# 되돌리기: sudo dnf remove -y mariadb105
# 끝나면 지워도 됨: rm ~/mmt-host.sh

set -uo pipefail

MODE="${1:-check}"
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
DOMAIN=www.my-math-teacher.com

echo "=============================================="
if [ "$MODE" = "--apply" ]; then
  echo " 호스트 점검 + mysql 설치 — ⚠️  설치 모드"
else
  echo " 호스트 결손 점검 — 읽기 전용"
fi
echo "=============================================="

ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 -o LogLevel=ERROR \
    "$SSHU@$HOST" "bash -s -- $MODE $DOMAIN" <<'REMOTE'
set -u
MODE=${1:-check}
DOMAIN=${2:-www.my-math-teacher.com}
warn=0
ok()   { echo "  ✅ $1"; }
bad()  { echo "  ❌ $1"; warn=$((warn+1)); }
note() { echo "     $1"; }

# ---------- 1. 호스트 기본 ----------
echo
echo "[1] 호스트"
note "OS      : $(. /etc/os-release; echo "$PRETTY_NAME")"
note "패키지수: $(rpm -qa | wc -l) 개  (minimal 은 full 대비 확연히 적다)"
note "디스크  : $(df -h / | awk 'NR==2{print $4, "여유 ("$5" 사용)"}')"
note "메모리  : $(free -h | awk 'NR==2{print $7, "available / "$2" total"}')"
note "가동시간: $(uptime -p 2>/dev/null || uptime)"

# ---------- 2. mysql 클라이언트 (M8 ②③④ 차단 요인) ----------
echo
echo "[2] mysql 클라이언트 — M8 ②③④ 의 전제"
if command -v mysql >/dev/null 2>&1; then
  ok "있음 — $(mysql --version)"
  HAVE_MYSQL=1
else
  bad "없음 — ②zdbg 정리·③DDL·④시드 적재가 전부 막힌다"
  HAVE_MYSQL=0
fi

# ---------- 3. SSM 에이전트 (CD 의 전제) ----------
echo
echo "[3] SSM 에이전트 — CD deploy job 의 전제"
if rpm -q amazon-ssm-agent >/dev/null 2>&1; then
  ok "설치됨 — $(rpm -q amazon-ssm-agent)"
  A=$(systemctl is-active amazon-ssm-agent 2>/dev/null || echo unknown)
  E=$(systemctl is-enabled amazon-ssm-agent 2>/dev/null || echo unknown)
  [ "$A" = "active" ] && ok "실행 중 (active)" || bad "실행 중 아님 ($A)"
  # enabled 가 핵심: 2026-08-07 에 손으로 설치했다. disabled 면 재부팅 한 번에 CD 가 다시 죽는다.
  [ "$E" = "enabled" ] && ok "부팅 시 자동시작 (enabled) — 재부팅해도 CD 유지" \
                       || bad "자동시작 아님 ($E) — 재부팅하면 CD 가 다시 죽는다"
else
  bad "미설치 — CD deploy 가 다시 죽는다"
fi

# ---------- 4. TLS 인증서 + 갱신 경로 (만료 2026-11-03) ----------
echo
echo "[4] TLS 인증서와 갱신 경로 — 만료가 다가오는 유일한 기한 항목"
CERT=/etc/letsencrypt/live/$DOMAIN/fullchain.pem
if sudo test -f "$CERT"; then
  END=$(sudo openssl x509 -enddate -noout -in "$CERT" 2>/dev/null | cut -d= -f2)
  ok "인증서 있음 — 만료 $END"
  if sudo openssl x509 -checkend $((30*86400)) -noout -in "$CERT" >/dev/null 2>&1; then
    note "30일 내 만료 아님"
  else
    bad "30일 내 만료 — 갱신 시급"
  fi
else
  note "호스트 파일로는 확인 불가(경로 다름 or 컨테이너 내부) — 라이브 핸드셰이크로 확인할 것"
fi

echo "     — 자동갱신 경로 —"
T=$(systemctl list-timers --all 2>/dev/null | grep -ci certbot || true)
U=$(systemctl list-unit-files 2>/dev/null | grep -ci certbot || true)
C=$( (sudo crontab -l 2>/dev/null; cat /etc/crontab 2>/dev/null; cat /etc/cron.d/* 2>/dev/null) | grep -ci certbot || true)
note "systemd 타이머 : $T 건"
note "systemd 유닛   : $U 건"
note "cron 항목      : $C 건"
if [ "$T" -gt 0 ] || [ "$C" -gt 0 ]; then
  ok "자동갱신 경로 있음"
  systemctl list-timers --all 2>/dev/null | grep -i certbot | sed 's/^/       /'
else
  bad "자동갱신 경로 없음 — 만료일에 사이트 전체가 브라우저 경고로 막힌다"
  note "재런치(2026-08-05)가 인증서는 재발급했지만 갱신 타이머를 안 옮긴 것으로 보인다"
fi
command -v certbot >/dev/null 2>&1 && note "certbot 바이너리: 있음" \
  || note "certbot 바이너리: 없음 (재런치 때 docker certbot/certbot 으로 발급했다면 정상)"

# ---------- 5. 그 밖의 minimal 결손 후보 ----------
echo
echo "[5] 그 밖의 도구 (없으면 다음 작업에서 또 막힌다)"
for t in docker jq curl git openssl tar rsync; do
  if command -v "$t" >/dev/null 2>&1; then printf "  ✅ %-8s\n" "$t"; else printf "  ❌ %-8s (없음)\n" "$t"; warn=$((warn+1)); fi
done

# ---------- 6. 서비스 실체 ----------
echo
echo "[6] 실행 중 컨테이너"
if command -v docker >/dev/null 2>&1; then
  docker ps --format '  {{.Names}}  {{.Status}}' 2>/dev/null | sed 's/^/  /' || note "docker ps 권한 없음"
else
  bad "docker 없음"
fi

# ---------- 7. 설치 (--apply 전용, mysql 하나만) ----------
if [ "$MODE" = "--apply" ] && [ "$HAVE_MYSQL" = "0" ]; then
  echo
  echo "[7] mysql 클라이언트 설치"
  CAND=""
  for p in mariadb105 mariadb1011 mariadb mysql; do
    sudo dnf -q list --available "$p" >/dev/null 2>&1 && { CAND="$p"; break; }
  done
  if [ -z "$CAND" ]; then
    bad "설치 후보 없음 — sudo dnf search mariadb 로 직접 확인"
  else
    note "설치 대상 = $CAND"
    if sudo dnf install -y "$CAND" >/tmp/mmt-dnf.log 2>&1; then
      ok "dnf install 완료"
      command -v mysql >/dev/null 2>&1 && ok "mysql 사용 가능 — $(mysql --version)" \
        || bad "설치했는데 바이너리 없음 — rpm -ql $CAND | grep bin/"
      # RDS 까지 실제로 붙는지 (읽기 전용)
      ENVF=""
      for c in /home/ec2-user/mmt-backend.env /etc/mmt/backend.env /home/ec2-user/backend.env; do
        [ -f "$c" ] && { ENVF="$c"; break; }
      done
      if [ -n "$ENVF" ] && command -v mysql >/dev/null 2>&1; then
        set -a; . "$ENVF"; set +a
        export MYSQL_PWD="${RDS_PASSWORD:-}"
        mysql -h "$RDS_HOST" -P "${RDS_PORT:-3306}" -u "$RDS_USERNAME" "$RDS_NAME" -e "SELECT 1" >/dev/null 2>&1 \
          && ok "RDS 접속 성공 ($RDS_NAME) — ②③④ 준비 완료" || bad "RDS 접속 실패 — SG 3306 인그레스 확인"
      fi
    else
      bad "설치 실패"; tail -5 /tmp/mmt-dnf.log | sed 's/^/       /'
    fi
  fi
elif [ "$MODE" = "--apply" ]; then
  echo
  echo "[7] mysql 이미 있음 — 설치 생략"
fi

echo
echo "=============================================="
if [ "$warn" -eq 0 ]; then
  echo " ✅ 결손 없음"
else
  echo " ⚠️  결손/주의 $warn 건 — 위 ❌ 확인"
fi
if [ "$MODE" != "--apply" ]; then
  echo " 🔍 읽기 전용 — 아무것도 바꾸지 않았다"
  echo "    mysql 설치하려면:  bash ~/mmt-host.sh --apply"
else
  echo " 변경한 것 = mysql 클라이언트 설치뿐 (certbot·SSM 은 report-only)"
fi
echo "=============================================="
REMOTE
