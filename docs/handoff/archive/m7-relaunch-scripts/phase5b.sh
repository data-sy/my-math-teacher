#!/usr/bin/env bash
# M7 재런치 Phase 5-2 — 백엔드 blue 손기동 + A4 결함② 백엔드 레벨 검증
# 실행: bash ~/phase5b.sh
# 전제: CI 가 이미지를 push 완료 · Phase 4c/4d 통과
# 끝나면 지워도 됨: rm ~/phase5b.sh
#
# 왜 손기동인가 (런북 §왜 런북인가): front nginx 의 upstream 은 로드 시점에
# mmt-backend-blue 를 Docker DNS 로 해석한다 → blue 가 없으면 front 가 기동 실패.
# 그런데 switch-backend.sh 는 마지막에 front 를 docker exec 한다 → 닭-달걀.
# 해소: 최초 1회만 blue 를 손으로 먼저 띄운다. 이후는 CI→switch-backend.sh 가 소유.

HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
TAG=ea94a1a6836b9ba9b043436fb774ca3b58b3e63c
IMAGE=mmt2024/mmt-backend:$TAG
OUT=~/mmt-relaunch
SSHOPT="-i $KEY -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"
CURL="docker run --rm --network mmt-net curlimages/curl:8.11.0"

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

mkdir -p "$OUT" && chmod 700 "$OUT"

echo "=============================================="
echo " Phase 5-2 — 백엔드 blue 기동"
echo " 이미지: $IMAGE"
echo "=============================================="

# ---------- 1. 이미지 존재 ----------
echo
echo "[1/6] Docker Hub 에 이미지가 있나"
if docker manifest inspect "$IMAGE" >/dev/null 2>&1; then
  ok "이미지 존재 — CI 빌드 완료"
else
  bad "이미지 없음 — CI 빌드 미완/실패. Actions 로그 확인"
  echo; echo "⛔ 중단"; exit 1
fi

# ---------- 2. pull + 기동 ----------
echo
echo "[2/6] 호스트에서 pull + blue 기동"
ssh $SSHOPT "$SSHU@$HOST" "
  docker pull $IMAGE >/dev/null 2>&1 || exit 1
  docker rm -f mmt-backend-blue >/dev/null 2>&1
  docker run -d --name mmt-backend-blue --network mmt-net --restart unless-stopped \
    --memory 350m \
    --env-file ~/mmt-backend.env \
    -e SPRING_PROFILES_ACTIVE=secure \
    -e MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true \
    -e GDB_URL=localhost -e GDB_PORT=7687 -e GDB_USERNAME=neo4j -e GDB_PASSWORD=dummy \
    -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=70' \
    $IMAGE >/dev/null
" && ok "컨테이너 기동 명령 완료" || { bad "기동 실패"; echo "⛔ 중단"; exit 1; }

# ---------- 3. 배포된 태그가 진짜 새 것인가 ----------
echo
echo "[3/6] ⚠️ 이 작업의 핵심 전제 — 구 이미지(889390a)가 아닌지"
RUNIMG=$(ssh $SSHOPT "$SSHU@$HOST" "docker inspect -f '{{.Config.Image}}' mmt-backend-blue 2>/dev/null")
echo "      실행 중 이미지: $RUNIMG"
case "$RUNIMG" in
  *889390a908a0be63c9935417ce3e92add12e68b4*) bad "구 이미지다 — A4 수정 미포함. 중단할 것" ;;
  *$TAG*) ok "새 이미지 확인 — d0f4c41(마스킹 수정)·1c7c29e(jti) 포함분" ;;
  *) bad "예상 밖 이미지: $RUNIMG" ;;
esac

# ---------- 4. 헬스 ----------
echo
echo "[4/6] 헬스 200 대기 (최대 150초)"
HEALTH=$(ssh $SSHOPT "$SSHU@$HOST" "$CURL -fsS --max-time 3 --retry 30 --retry-delay 5 \
  http://mmt-backend-blue:8080/api/v1/health 2>&1" )
echo "      $HEALTH"
case "$HEALTH" in
  *[Uu][Pp]*|*ok*|*OK*) ok "헬스 200" ;;
  *) bad "헬스 실패 — docker logs mmt-backend-blue 로 원인 확인 (대개 RDS·Redis 자격)" ;;
esac

# ---------- 5. 데이터 경로 smoke ----------
echo
echo "[5/6] 데이터 경로 smoke (CTE + RDS 시드 + Redis 왕복)"
SMOKE=$(ssh $SSHOPT "$SSHU@$HOST" "$CURL -fsS --max-time 10 \
  http://mmt-backend-blue:8080/api/v1/concepts/nodes/7925 2>&1 | head -c 200")
echo "      ${SMOKE:0:160}"
[ -n "$SMOKE" ] && [ "$SMOKE" != "[]" ] && ok "그래프 non-empty — 스냅샷 데이터 살아있음" \
  || bad "smoke 비었음 — 시드/CTE 확인"

# ---------- 6. 🎯 A4 결함② 백엔드 레벨 검증 (front 없이도 가능) ----------
echo
echo "[6/6] 🎯 A4 결함② — /error 마스킹 제거가 실제로 배포됐나"
echo "      (front·TLS 없이 내부 네트워크에서 바로 판정한다)"

code() { ssh $SSHOPT "$SSHU@$HOST" "$CURL -s -o /dev/null -w '%{http_code}' --max-time 5 $1 2>/dev/null"; }

C3=$(code "http://mmt-backend-blue:8080/api/v1/hello/no-such-sub")
echo "      permitAll 하위 없는 경로 → $C3   (기대 404)"
[ "$C3" = "404" ] && ok "404 — 마스킹 제거 배포 확인 ✨" \
  || bad "$C3 — 401 이면 마스킹 수정 미배포(구 이미지), 그 외는 별건"

C4=$(code "http://mmt-backend-blue:8080/api/v1/learning-queues/me")
echo "      무토큰 인증필수 (대조군)   → $C4   (기대 401)"
[ "$C4" = "401" ] && ok "401 유지 — 인증은 정상 작동" \
  || bad "$C4 — 200 이면 인증이 뚫린 것, 즉시 중단"

C5=$(code "http://mmt-backend-blue:8080/api/v1/chapters")
echo "      permitAll 정상 경로        → $C5   (기대 200)"
[ "$C5" = "200" ] && ok "200 — permitAll 정상" || bad "$C5 — 401 이면 마스킹 잔존"

echo
echo "      진단 경로 게이트 확인 (MMT_DIAGNOSIS_ENABLED):"
C6=$(code "http://mmt-backend-blue:8080/api/v1/diagnosis/frontier")
echo "      /api/v1/diagnosis/frontier → $C6   (404 면 플래그 off, 그 외면 on)"
[ "$C6" != "404" ] && ok "진단 경로 활성 (플래그 on)" || bad "404 — MMT_DIAGNOSIS_ENABLED 가 안 먹었다"

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 5-2 통과 — 🎉 A4 결함 ①② 둘 다 프로덕션에서 해소됨"
  echo "    다음 = 4e(프론트 기동) → Phase 6(전체 검증)"
else
  echo " ⛔ Phase 5-2 미통과 — 위 ❌ 확인"
  echo "    로그: ssh -i ~/.ssh/mmt-ec2 ec2-user@$HOST 'docker logs --tail 60 mmt-backend-blue'"
fi
echo "=============================================="
