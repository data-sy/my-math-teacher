#!/usr/bin/env bash
# M7 재런치 Phase 4d — nginx fragment 배치 + TLS 인증서 발급 (certbot standalone)
# 실행: bash ~/phase4d.sh
# 전제: Phase 3(DNS 전파) · 4c 통과. 포트 80 이 비어 있어야 한다(front 미기동 상태 = 지금).
# 끝나면 지워도 됨: rm ~/phase4d.sh
#
# standalone 은 최초 부트스트랩용(런북 §갱신). 이후 90일 갱신은 front 80 의 webroot 경로로 한다.
# 인증서 lineage: www.my-math-teacher.com (apex 는 A레코드가 없어 챌린지 실패하므로 제외)

HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
DOMAIN=www.my-math-teacher.com
EMAIL=data.sy.2@gmail.com          # Let's Encrypt 만료 통지용. 바꾸려면 이 줄 수정.
REPO=~/my-math-teacher
OUT=~/mmt-relaunch
SSHOPT="-i $KEY -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

mkdir -p "$OUT" && chmod 700 "$OUT"

echo "=============================================="
echo " Phase 4d — nginx fragment + TLS 인증서"
echo "=============================================="

# ---------- 1. fragment 를 홈 루트에도 ----------
echo
echo "[1/5] active-backend.conf 를 홈 루트에 배치"
echo "      (CI 의 FRAGMENT_HOST_FILE 기본값 = /home/ec2-user/active-backend.conf)"
scp $SSHOPT -q "$REPO/deploy/active-backend.conf" "$SSHU@$HOST:~/active-backend.conf"
FRAG=$(ssh $SSHOPT "$SSHU@$HOST" 'cat ~/active-backend.conf 2>/dev/null | grep -v "^#" | grep -v "^$"')
echo "      내용: $FRAG"
case "$FRAG" in
  *mmt-backend-blue:8080*) ok "홈 루트 fragment 배치 (blue 기본값)" ;;
  *) bad "fragment 내용 이상" ;;
esac
# ⚠️ rw 필요 — switch-backend.sh 가 제자리 truncate 한다 (런북 §미해결)
ssh $SSHOPT "$SSHU@$HOST" 'chmod 644 ~/active-backend.conf'

# ---------- 2. 포트 80 비었나 ----------
echo
echo "[2/5] 포트 80 점유 확인 (standalone 은 80 을 직접 바인딩한다)"
P80=$(ssh $SSHOPT "$SSHU@$HOST" 'docker ps --format "{{.Names}} {{.Ports}}" | grep ":80->" || echo FREE')
echo "      $P80"
[ "$P80" = "FREE" ] && ok "80 비어 있음" || bad "80 을 쓰는 컨테이너가 있다 — 먼저 중지할 것"

# ---------- 3. DNS 가 이 호스트를 가리키나 (챌린지 전제) ----------
echo
echo "[3/5] DNS 재확인 (챌린지는 이 도메인으로 들어온다)"
D=$(dig +short "$DOMAIN" A | tail -1)
echo "      $DOMAIN → $D"
[ "$D" = "$HOST" ] && ok "DNS 가 이 호스트를 가리킴" || bad "DNS 불일치 — 발급 실패한다"

if [ "$fail" -gt 0 ]; then
  echo; echo "⛔ 전제 미충족 — 발급 시도 안 함"; exit 1
fi

# ---------- 4. dry-run (rate limit 보호) ----------
echo
echo "[4/5] certbot dry-run (Let's Encrypt 주간 발급 한도를 태우지 않고 검증)"
ssh $SSHOPT "$SSHU@$HOST" "sudo docker run --rm -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot certonly --standalone --dry-run \
  -d $DOMAIN --email $EMAIL --agree-tos --no-eff-email --non-interactive" 2>&1 | tail -12

read -r -p "  위 dry-run 이 'The dry run was successful' 이면 Enter, 아니면 Ctrl-C: " _

# ---------- 5. 실제 발급 ----------
echo
echo "[5/5] 실제 발급"
ssh $SSHOPT "$SSHU@$HOST" "sudo docker run --rm -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot certonly --standalone \
  -d $DOMAIN --email $EMAIL --agree-tos --no-eff-email --non-interactive" 2>&1 | tail -15

echo
echo "      발급 결과 확인:"
CERTINFO=$(ssh $SSHOPT "$SSHU@$HOST" "sudo ls -1 /etc/letsencrypt/live/$DOMAIN/ 2>/dev/null; \
  sudo openssl x509 -in /etc/letsencrypt/live/$DOMAIN/fullchain.pem -noout -subject -enddate 2>/dev/null")
echo "$CERTINFO" | sed 's/^/      /'
case "$CERTINFO" in
  *fullchain.pem*privkey.pem*|*privkey.pem*) ok "인증서 파일 존재" ;;
  *) bad "인증서 파일 없음" ;;
esac
case "$CERTINFO" in
  *notAfter*) ok "만료일 확인됨 (위 notAfter)" ;;
  *) bad "인증서 파싱 실패" ;;
esac

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 4d 통과 — TLS 준비 완료"
  echo "    다음 = 5-2(백엔드 blue 손기동) → 4e(프론트 기동) → Phase 6"
else
  echo " ⛔ Phase 4d 미통과"
fi
echo "=============================================="
