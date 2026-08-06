#!/usr/bin/env bash
# M7 재런치 Phase 4c — 자산 전송 + 인프라 컨테이너 기동 (redis · mmt-ai · mmt-net)
# 실행: bash ~/phase4c.sh
# 전제: Phase 4a(SSH 열림) · 4b(env-file 생성) 통과
# 끝나면 지워도 됨: rm ~/phase4c.sh
#
# 근거: docs/specs/m6/first-deploy-runbook.md §Phase 0~1
# 시크릿은 scp 로만 이동한다 — 화면·SSM 로그·명령행에 값이 남지 않는다.

HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
REPO=~/my-math-teacher
ENVFILE=~/mmt-relaunch/mmt-backend.env
REMOTE=~/mmt-relaunch/remote-phase4c.sh

SSHOPT="-i $KEY -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

[ -f "$ENVFILE" ] || { echo "⛔ env-file 없음 — 먼저 bash ~/mkenv.sh"; exit 1; }

echo "=============================================="
echo " Phase 4c — 전송 + 인프라 컨테이너"
echo "=============================================="

# ---------- 전송 ----------
echo
echo "[1/3] 자산 전송 (scp)"
if scp $SSHOPT -q "$ENVFILE" "$SSHU@$HOST:~/mmt-backend.env"; then
  ssh $SSHOPT "$SSHU@$HOST" 'chmod 600 ~/mmt-backend.env'
  ok "env-file 전송 (chmod 600)"
else
  bad "env-file 전송 실패"; echo "⛔ 중단"; exit 1
fi

ssh $SSHOPT "$SSHU@$HOST" 'mkdir -p ~/deploy'
if scp $SSHOPT -q "$REPO/deploy/switch-backend.sh" "$REPO/deploy/active-backend.conf" "$SSHU@$HOST:~/deploy/"; then
  ok "deploy/ 자산 전송 (switch-backend.sh · active-backend.conf)"
else
  bad "deploy/ 전송 실패"
fi

# ---------- 원격 스크립트 생성 후 전송·실행 (따옴표 깨짐 회피) ----------
cat > "$REMOTE" <<'REMOTE_EOF'
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
REMOTE_EOF

echo
echo "[2/3] 호스트에서 인프라 컨테이너 기동"
scp $SSHOPT -q "$REMOTE" "$SSHU@$HOST:~/remote-phase4c.sh"
if ssh $SSHOPT "$SSHU@$HOST" 'bash ~/remote-phase4c.sh'; then
  ok "원격 단계 전부 통과"
else
  bad "원격 단계에 실패 항목 있음 (위 출력 참조)"
fi
ssh $SSHOPT "$SSHU@$HOST" 'rm -f ~/remote-phase4c.sh'

# ---------- RDS 도달 확인 (호스트에서만 가능 — 비공개 RDS) ----------
echo
echo "[3/3] 호스트 → RDS 도달 확인 (Phase 5 DDL 의 선행조건)"
RDSCHK=$(ssh $SSHOPT "$SSHU@$HOST" 'set -a; . ~/mmt-backend.env; set +a; \
  docker run --rm mysql:8.0 mysql -h "$RDS_HOST" -P "$RDS_PORT" -u "$RDS_USERNAME" -p"$RDS_PASSWORD" \
  -N -e "SELECT CONCAT(@@version, \" | db=\", DATABASE());" "$RDS_NAME" 2>&1' )
echo "      $RDSCHK"
case "$RDSCHK" in
  *8.0*) ok "RDS 로그인 성공 — 비번·SG·엔드포인트 전부 정상" ;;
  *"Access denied"*) bad "RDS 인증 실패 — tfvars 비번이 스냅샷 비번과 다르다. modify-db-instance 필요" ;;
  *) bad "RDS 접속 실패 — SG/엔드포인트 확인" ;;
esac

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 4c 통과"
  echo "    다음 = Phase 4d(인증서·프론트) 또는 Phase 5-1(DDL) — 어시스턴트 판정 후"
else
  echo " ⛔ Phase 4c 미통과 — 위 ❌ 확인"
fi
echo "=============================================="
