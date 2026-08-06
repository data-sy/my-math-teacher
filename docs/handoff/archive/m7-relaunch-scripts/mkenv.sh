#!/usr/bin/env bash
# M7 재런치 Phase 4b — 호스트 env-file 생성 (로컬에서 조립, 값은 화면에 안 찍는다)
# 실행: bash ~/mkenv.sh
# 출력: ~/mmt-relaunch/mmt-backend.env (chmod 600, 리포 밖)
# 끝나면 지워도 됨: rm ~/mkenv.sh
#
# 출처: 로컬 gitignored docker-compose.yml 의 mmt-backend environment 블록
#       + terraform.tfvars 의 db_password (RDS 비번은 compose 의 로컬 MySQL 값이 아니다)
# 근거: docs/specs/m6/first-deploy-runbook.md §시크릿·env-file

COMPOSE=~/my-math-teacher/docker-compose.yml
TFVARS=~/my-math-teacher/infra/terraform/terraform.tfvars
OUT=~/mmt-relaunch
ENVFILE=$OUT/mmt-backend.env

# 프로덕션 고정값 (2026-08-05 재런치 실측)
RDS_HOST_P=mmt-db.c7qu444ug8bf.ap-northeast-2.rds.amazonaws.com
RDS_PORT_P=3306
RDS_NAME_P=mmt
RDS_USER_P=mmtadmin

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

mkdir -p "$OUT" && chmod 700 "$OUT"
[ -f "$COMPOSE" ] || { echo "⛔ $COMPOSE 없음"; exit 1; }
[ -f "$TFVARS" ]  || { echo "⛔ $TFVARS 없음"; exit 1; }

# compose 의 `- KEY=value` 에서 값만 뽑는다 (값은 변수에만 담고 절대 echo 안 함)
cval() { grep -m1 -E "^[[:space:]]*-[[:space:]]*$1=" "$COMPOSE" | sed -E "s/^[[:space:]]*-[[:space:]]*$1=//" | sed -E 's/[[:space:]]+$//'; }

echo "=============================================="
echo " Phase 4b — ~/mmt-backend.env 조립"
echo "=============================================="
echo
echo "[1/3] compose 에서 시크릿 추출 (값 미출력)"

COMPOSE_KEYS="REDIS_PASSWORD GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET NAVER_CLIENT_ID NAVER_CLIENT_SECRET KAKAO_CLIENT_ID KAKAO_CLIENT_SECRET JWT_SECRET"
missing=0
for k in $COMPOSE_KEYS; do
  v=$(cval "$k")
  if [ -z "$v" ]; then printf "      %-22s ❌ 없음\n" "$k"; missing=1
  else printf "      %-22s ✔ len=%-4s sha=%s\n" "$k" "${#v}" "$(printf %s "$v" | shasum -a 256 | cut -c1-8)"; fi
done
[ "$missing" -eq 0 ] && ok "compose 키 8종 전부 확보" || bad "compose 에서 못 찾은 키가 있다"

RDS_PASS=$(grep -E '^[[:space:]]*db_password' "$TFVARS" | sed -E 's/.*=[[:space:]]*"?([^"]*)"?[[:space:]]*$/\1/')
if [ -n "$RDS_PASS" ]; then
  printf "      %-22s ✔ len=%-4s sha=%s  (tfvars)\n" "RDS_PASSWORD" "${#RDS_PASS}" "$(printf %s "$RDS_PASS" | shasum -a 256 | cut -c1-8)"
  ok "RDS 비번은 tfvars 에서 (compose 로컬 MySQL 값 아님)"
else
  bad "tfvars 에 db_password 없음"
fi

echo
echo "[2/3] 파일 작성"
umask 077
{
  echo "# MMT 백엔드 env-file — 2026-08-05 재런치 생성"
  echo "# 출처: 로컬 docker-compose.yml + terraform.tfvars. 절대 커밋 금지."
  echo "# switch-backend.sh 가 -e 로 주입하는 것(SPRING_PROFILES_ACTIVE·CTE 플래그·GDB_*)은 여기 없어도 된다."
  echo
  echo "# --- RDS (프로덕션 실측값) ---"
  echo "RDS_HOST=$RDS_HOST_P"
  echo "RDS_PORT=$RDS_PORT_P"
  echo "RDS_NAME=$RDS_NAME_P"
  echo "RDS_USERNAME=$RDS_USER_P"
  echo "RDS_PASSWORD=$RDS_PASS"
  echo
  echo "# --- Redis (컨테이너명 = mmt-net 내부 DNS) ---"
  echo "REDIS_URL=mmt-redis"
  echo "REDIS_PORT=6379"
  echo "REDIS_PASSWORD=$(cval REDIS_PASSWORD)"
  echo
  echo "# --- Neo4j 더미 (CTE-only. 미기동이지만 배선이 값을 요구한다) ---"
  echo "GDB_URL=localhost"
  echo "GDB_PORT=7687"
  echo "GDB_USERNAME=neo4j"
  echo "GDB_PASSWORD=dummy"
  echo
  echo "# --- OAuth (redirect-uri 는 application.yml secure 문서에 도메인 고정) ---"
  echo "GOOGLE_CLIENT_ID=$(cval GOOGLE_CLIENT_ID)"
  echo "GOOGLE_CLIENT_SECRET=$(cval GOOGLE_CLIENT_SECRET)"
  echo "NAVER_CLIENT_ID=$(cval NAVER_CLIENT_ID)"
  echo "NAVER_CLIENT_SECRET=$(cval NAVER_CLIENT_SECRET)"
  echo "KAKAO_CLIENT_ID=$(cval KAKAO_CLIENT_ID)"
  echo "KAKAO_CLIENT_SECRET=$(cval KAKAO_CLIENT_SECRET)"
  echo
  echo "# --- JWT / CORS ---"
  echo "JWT_SECRET=$(cval JWT_SECRET)"
  echo "EC2_DOMAIN_NAME1=www.my-math-teacher.com"
  echo "EC2_DOMAIN_NAME2=my-math-teacher.com"
  echo
  echo "# --- M7 진단 경로 게이트 (switch-backend.sh 가 주입하지 않는다 — 여기 필수) ---"
  echo "MMT_DIAGNOSIS_ENABLED=true"
} > "$ENVFILE"
chmod 600 "$ENVFILE"
ok "작성됨: $ENVFILE (chmod 600)"

echo
echo "[3/3] 검증 — 키 목록·빈값 점검 (값 미출력)"
REQUIRED="RDS_HOST RDS_PORT RDS_NAME RDS_USERNAME RDS_PASSWORD REDIS_URL REDIS_PORT REDIS_PASSWORD GDB_URL GDB_PORT GDB_USERNAME GDB_PASSWORD GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET NAVER_CLIENT_ID NAVER_CLIENT_SECRET KAKAO_CLIENT_ID KAKAO_CLIENT_SECRET JWT_SECRET EC2_DOMAIN_NAME1 EC2_DOMAIN_NAME2 MMT_DIAGNOSIS_ENABLED"
empty=0
for k in $REQUIRED; do
  line=$(grep -m1 -E "^$k=" "$ENVFILE")
  val=${line#*=}
  if [ -z "$val" ]; then printf "      %-24s ❌ 빈값\n" "$k"; empty=1; fi
done
[ "$empty" -eq 0 ] && ok "필수 22키 전부 비어 있지 않음" || bad "빈값 키 존재 — 위 목록"

echo "      총 라인: $(grep -cE '^[A-Z_]+=' "$ENVFILE") 개 키"
grep -q "^MMT_DIAGNOSIS_ENABLED=true" "$ENVFILE" && ok "MMT_DIAGNOSIS_ENABLED=true" || bad "진단 플래그 누락"

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 4b 통과"
  echo
  echo " 다음 — 호스트로 전송 (env-file + deploy 자산):"
  echo "   bash ~/phase4c.sh"
else
  echo " ⛔ Phase 4b 미통과 — 위 ❌ 확인"
fi
echo " ⚠️ $ENVFILE 은 시크릿이다. 리포로 옮기지 말 것."
echo "=============================================="
