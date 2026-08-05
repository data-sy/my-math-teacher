#!/usr/bin/env bash
# M7 재런치 Phase 4e — 프론트 기동 + 외부(HTTPS) 검증
# 실행: bash ~/phase4e.sh
# 전제: 4d(인증서) · 5-2(backend blue up) 통과
# 끝나면 지워도 됨: rm ~/phase4e.sh
#
# ⚠️ active-backend.conf 는 **rw** 로 마운트한다(:ro 금지) — switch-backend.sh 가
#    제자리 truncate 로 blue↔green 을 재작성한다(런북 §미해결).
# ⚠️ 마운트 원본 = /home/ec2-user/active-backend.conf — CI 의 FRAGMENT_HOST_FILE 기본값과 일치시킨다.

HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
IMAGE=mmt2024/mmt-front:2.0.0
DOMAIN=www.my-math-teacher.com

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
echo " Phase 4e — 프론트 기동 + HTTPS 검증"
echo " 이미지: $IMAGE (결정 B 확정값)"
echo "=============================================="

echo
echo "[1/6] backend blue 가 떠 있나 (front nginx 가 DNS 해석해야 기동된다)"
BLUE=$(ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 "$SSHU@$HOST" \
  'docker ps --format "{{.Names}}" | grep -x mmt-backend-blue || echo NONE')
echo "      $BLUE"
[ "$BLUE" = "mmt-backend-blue" ] && ok "blue 실행 중" || { bad "blue 없음 — 먼저 5-2"; exit 1; }

echo
echo "[2/6] 프론트 기동"
ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 "$SSHU@$HOST" "
  docker pull $IMAGE >/dev/null 2>&1 || exit 1
  docker rm -f mmt-front >/dev/null 2>&1
  docker run -d --name mmt-front --network mmt-net --restart unless-stopped \
    -p 80:80 -p 443:443 \
    -v /etc/letsencrypt:/etc/letsencrypt:ro \
    -v /var/www/certbot:/var/www/certbot \
    -v /home/ec2-user/active-backend.conf:/etc/nginx/active-backend.conf \
    $IMAGE >/dev/null
" && ok "컨테이너 기동" || { bad "기동 실패"; exit 1; }

sleep 3
echo
echo "[3/6] nginx 설정 검사 (upstream 해석 + cert 존재)"
NGT=$(ssh -i "$KEY" -o ConnectTimeout=15 "$SSHU@$HOST" 'docker exec mmt-front nginx -t 2>&1')
echo "$NGT" | sed 's/^/      /'
case "$NGT" in
  *"syntax is ok"*successful*) ok "nginx -t 통과" ;;
  *) bad "nginx -t 실패 — upstream 해석/cert 경로 확인"; ssh -i "$KEY" "$SSHU@$HOST" 'docker logs --tail 20 mmt-front'; ;;
esac

echo
echo "[4/6] fragment 가 rw 로 마운트됐나 (switch-backend.sh 의 전제)"
RW=$(ssh -i "$KEY" -o ConnectTimeout=15 "$SSHU@$HOST" \
  'docker inspect -f "{{range .Mounts}}{{if eq .Destination \"/etc/nginx/active-backend.conf\"}}{{.RW}}{{end}}{{end}}" mmt-front')
echo "      RW=$RW"
[ "$RW" = "true" ] && ok "rw 마운트 — 이후 blue↔green 전환 가능" || bad "ro 마운트 — switch-backend.sh 가 truncate 못 한다"

echo
echo "[5/6] 외부에서 HTTPS 검증 (맥 → 인터넷 → 호스트)"
C1=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "https://$DOMAIN/")
printf "      GET  https://%s/            -> %s (기대 200)\n" "$DOMAIN" "$C1"
[ "$C1" = "200" ] && ok "SPA 200 + TLS 유효" || bad "$C1"

C2=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "http://$DOMAIN/")
printf "      GET  http://%s/             -> %s (기대 301)\n" "$DOMAIN" "$C2"
[ "$C2" = "301" ] && ok "HTTP→HTTPS 리다이렉트" || bad "$C2"

C3=$(curl -s --max-time 15 "https://$DOMAIN/api/v1/health")
printf "      GET  /api/v1/health              -> %s (기대 OK)\n" "$C3"
case "$C3" in *OK*|*UP*) ok "백엔드까지 프록시 관통" ;; *) bad "$C3" ;; esac

echo
echo "[6/6] 🎯 A4 결함② — 프론트 경유로도 마스킹이 사라졌나"
C4=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "https://$DOMAIN/api/v1/hello/no-such-sub")
printf "      permitAll 하위 없는 경로         -> %s (기대 404)\n" "$C4"
[ "$C4" = "404" ] && ok "404 — 라이브 HTTPS 경로에서도 마스킹 제거 확증 ✨" || bad "$C4 — 401 이면 마스킹 잔존"

C5=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "https://$DOMAIN/api/v1/learning-queues/me")
printf "      무토큰 인증필수 (대조군)          -> %s (기대 401)\n" "$C5"
[ "$C5" = "401" ] && ok "401 유지 — 인증 정상" || bad "$C5"

C6=$(curl -s -o /dev/null -w "%{http_code}" --max-time 20 -X POST \
  -H "Content-Type: application/json" -d '{"chapterId":1}' \
  "https://$DOMAIN/api/v1/diagnosis/frontier")
printf "      POST /api/v1/diagnosis/frontier  -> %s (기대 200)\n" "$C6"
[ "$C6" = "200" ] && ok "진단 경로 라이브" || bad "$C6"

echo
echo "      인증서 확인:"
curl -sI --max-time 15 "https://$DOMAIN/" >/dev/null 2>&1 && \
  echo | openssl s_client -servername "$DOMAIN" -connect "$DOMAIN:443" 2>/dev/null \
  | openssl x509 -noout -subject -issuer -enddate 2>/dev/null | sed 's/^/        /'

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 4e 통과 — 🎉 https://$DOMAIN 라이브"
  echo "    다음 = Phase 6 브라우저 검증 (OAuth 로그인 → 진단 완주 → 저장)"
else
  echo " ⛔ Phase 4e 미통과 — 위 ❌ 확인"
fi
echo "=============================================="
