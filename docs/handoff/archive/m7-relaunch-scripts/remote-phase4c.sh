#!/usr/bin/env bash
# 호스트에서 실행 — 인프라 컨테이너 기동
p=0; f=0
ok(){ echo "  ✅ $1"; p=$((p+1)); }
bad(){ echo "  ❌ $1"; f=$((f+1)); }

echo "[호스트] $(hostname)"

# env-file 로드 (REDIS_PASSWORD 를 명령행에 안 박기 위해)
set -a; . ~/mmt-backend.env; set +a

# 네트워크
docker network inspect mmt-net >/dev/null 2>&1 || docker network create mmt-net >/dev/null
docker network inspect mmt-net >/dev/null 2>&1 && ok "네트워크 mmt-net" || bad "mmt-net 생성 실패"

# certbot webroot
sudo mkdir -p /var/www/certbot && ok "/var/www/certbot 준비" || bad "webroot 생성 실패"

# switch-backend.sh 문법
bash -n ~/deploy/switch-backend.sh 2>/dev/null && ok "switch-backend.sh 문법 OK" || bad "switch-backend.sh 문법 오류/부재"
chmod +x ~/deploy/switch-backend.sh 2>/dev/null

# redis — env-file 의 비번과 반드시 일치해야 백엔드가 붙는다
if ! docker ps -a --format '{{.Names}}' | grep -qx mmt-redis; then
  docker run -d --name mmt-redis --network mmt-net --restart unless-stopped \
    redis redis-server --requirepass "$REDIS_PASSWORD" >/dev/null
fi
docker start mmt-redis >/dev/null 2>&1
sleep 2
PONG=$(docker exec mmt-redis redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | tr -d '\r')
[ "$PONG" = "PONG" ] && ok "mmt-redis 인증 PONG (env-file 비번 일치)" || bad "redis 인증 실패 (PONG 아님: $PONG)"

# TF Serving — 이름 mmt-ai 고정 (ProbabilityService 하드코딩)
if ! docker ps -a --format '{{.Names}}' | grep -qx mmt-ai; then
  docker pull mymathteacher/mmt-ai:serving >/dev/null 2>&1
  docker run -d --name mmt-ai --network mmt-net --restart unless-stopped \
    mymathteacher/mmt-ai:serving >/dev/null
fi
docker start mmt-ai >/dev/null 2>&1
echo "  … TF Serving 기동 대기"
for i in 1 2 3 4 5 6 7 8 9 10; do
  ST=$(docker run --rm --network mmt-net curlimages/curl:8.11.0 -fsS --max-time 3 \
        http://mmt-ai:8501/v1/models/my_model 2>/dev/null)
  case "$ST" in *AVAILABLE*) break;; esac
  sleep 3
done
case "$ST" in
  *AVAILABLE*) ok "mmt-ai 모델 AVAILABLE (시급도 경로 살아있음)" ;;
  *)           bad "mmt-ai 모델 상태 확인 실패 — 응답: ${ST:-(없음)}" ;;
esac

echo
echo "  현재 컨테이너:"
docker ps --format '    {{.Names}}  {{.Status}}  {{.Image}}'

echo
echo "  통과 $p · 실패 $f"
exit $f
