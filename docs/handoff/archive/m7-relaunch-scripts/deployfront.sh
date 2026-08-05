#!/usr/bin/env bash
# M7 프론트 배포 — 로컬 빌드 이미지를 Docker Hub 에 push 후 호스트에서 교체
# 실행: bash ~/deployfront.sh [태그]      (기본 2.0.1)
# 전제: docker buildx build --platform linux/amd64 -t mmt2024/mmt-front:<태그> --load . 완료
# 끝나면 지워도 됨: rm ~/deployfront.sh
#
# ⚠️ 맥(arm64) ≠ EC2(x86_64) — 반드시 amd64 이미지여야 한다. 스크립트가 arch 를 검사한다.
# 롤백 = 이미지 태그 되돌리기 (ADR-0011): docker rm -f mmt-front 후 2.0.0 으로 재기동.

TAG=${1:-2.0.1}
IMAGE=mmt2024/mmt-front:$TAG
PREV=mmt2024/mmt-front:2.0.0
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
DOMAIN=www.my-math-teacher.com

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
echo " 프론트 배포 — $IMAGE"
echo "=============================================="

# ---------- 1. 로컬 이미지 검사 ----------
echo
echo "[1/6] 로컬 이미지 · 아키텍처 검사"
ARCH=$(docker image inspect "$IMAGE" --format '{{.Architecture}}' 2>/dev/null)
if [ -z "$ARCH" ]; then
  bad "로컬에 $IMAGE 없음 — 먼저 buildx 빌드"; echo "⛔ 중단"; exit 1
fi
echo "      arch = $ARCH"
[ "$ARCH" = "amd64" ] && ok "amd64 — EC2(x86_64)와 일치" \
  || { bad "$ARCH — EC2 에서 exec format error 로 죽는다. --platform linux/amd64 로 재빌드"; echo "⛔ 중단"; exit 1; }

# ---------- 2. 수정이 실제로 번들에 들어갔나 ----------
echo
echo "[2/6] 이 빌드에 로그아웃 UI 가 들어있나 (배포 전 확인)"
HIT=$(docker run --rm --entrypoint sh "$IMAGE" -c 'grep -ho "로그아웃" /usr/share/nginx/html/assets/*.js 2>/dev/null | head -1')
[ -n "$HIT" ] && ok "번들에 '로그아웃' 문자열 존재" || bad "번들에 없음 — 구 소스로 빌드된 것"

# ---------- 3. push ----------
echo
echo "[3/6] Docker Hub push"
if docker push "$IMAGE" 2>&1 | tail -3; then
  ok "push 완료"
else
  bad "push 실패 — docker login 필요할 수 있다"; echo "⛔ 중단"; exit 1
fi

# ---------- 4. 호스트 교체 ----------
echo
echo "[4/6] 호스트에서 컨테이너 교체 (구 이미지는 롤백용으로 보존)"
ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 "$SSHU@$HOST" "
  docker pull $IMAGE >/dev/null 2>&1 || exit 1
  docker rm -f mmt-front >/dev/null 2>&1
  docker run -d --name mmt-front --network mmt-net --restart unless-stopped \
    -p 80:80 -p 443:443 \
    -v /etc/letsencrypt:/etc/letsencrypt:ro \
    -v /var/www/certbot:/var/www/certbot \
    -v /home/ec2-user/active-backend.conf:/etc/nginx/active-backend.conf \
    $IMAGE >/dev/null
  sleep 3
  docker exec mmt-front nginx -t
" 2>&1 | grep -viE "post-quantum|store now|may need to be upgraded|^\*\*" | sed 's/^/      /'
RUN=$(ssh -i "$KEY" -o ConnectTimeout=15 "$SSHU@$HOST" "docker inspect -f '{{.Config.Image}}' mmt-front 2>/dev/null")
echo "      실행 중: $RUN"
[ "$RUN" = "$IMAGE" ] && ok "새 이미지로 교체됨" || bad "교체 실패 (실행 중: $RUN)"

# ---------- 5. 외부 검증 ----------
echo
echo "[5/6] 외부 HTTPS 검증"
for chk in "/|200" "/api/v1/health|200"; do
  p=${chk%%|*}; e=${chk#*|}
  c=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "https://$DOMAIN$p")
  printf "      %-18s -> %s (기대 %s)\n" "$p" "$c" "$e"
  [ "$c" = "$e" ] && pass=$((pass+1)) || { echo "        ❌"; fail=$((fail+1)); }
done

# ---------- 6. 배포된 번들에 수정이 실렸나 ----------
echo
echo "[6/6] 라이브 번들에 로그아웃 UI 가 실렸나"
BUNDLE=$(curl -s --max-time 15 "https://$DOMAIN/" | grep -oE '/assets/[^"]+\.js' | head -1)
echo "      번들: $BUNDLE"
if curl -s --max-time 30 "https://$DOMAIN$BUNDLE" | grep -q "로그아웃"; then
  ok "라이브 번들에 '로그아웃' 존재 — 수정 반영 확인 ✨"
else
  bad "라이브 번들에 없음 — 캐시이거나 구 이미지"
fi

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ 프론트 배포 성공 — $IMAGE"
  echo "    롤백이 필요하면:"
  echo "      ssh -i ~/.ssh/mmt-ec2 $SSHU@$HOST"
  echo "      docker rm -f mmt-front && docker run -d --name mmt-front ... $PREV"
else
  echo " ⛔ 배포 미완 — 위 ❌ 확인"
fi
echo "=============================================="
