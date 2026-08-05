#!/usr/bin/env bash
# M7 재런치 Phase 5-1 — 결함① DDL 적용 (프로덕션 RDS)
# 실행: bash ~/phase5a.sh
# 전제: Phase 4c 통과 (호스트→RDS 로그인 실증됨)
# 끝나면 지워도 됨: rm ~/phase5a.sh
#
# 스크립트는 멱등(테이블 IF NOT EXISTS, ALTER 3건은 information_schema 가드).
# 재실행 안전 — 이미 적용된 부분은 skip 된다.
# ⚠️ MySQL DDL 은 문장별 암묵 커밋 — 트랜잭션 롤백 불가. PREFLIGHT 로 먼저 상태를 본다.
# 롤백 = MMT_DIAGNOSIS_ENABLED=false (테이블 방치 가능). 전부 additive 라 구 기능 무영향(ADR-0010).

HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
SQL=~/my-math-teacher/api/sql/m7-apply-diagnosis-ddl-prod.sql
OUT=~/mmt-relaunch
SSHOPT="-i $KEY -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

mkdir -p "$OUT" && chmod 700 "$OUT"
[ -f "$SQL" ] || { echo "⛔ $SQL 없음"; exit 1; }

echo "=============================================="
echo " Phase 5-1 — M7 진단 DDL 적용"
echo "=============================================="

echo
echo "[1/4] 스크립트 지문 (검토분과 실행분 동일 확인)"
echo "      md5  : $(md5 -q "$SQL")"
echo "      bytes: $(wc -c < "$SQL" | tr -d ' ')"
echo "      기대 : c00a92c70968e0cdf282aed5d3b2071e / 7504"
[ "$(md5 -q "$SQL")" = "c00a92c70968e0cdf282aed5d3b2071e" ] \
  && ok "지문 일치" || bad "지문 불일치 — 파일이 바뀌었다"

echo
echo "[2/4] 호스트로 전송"
scp $SSHOPT -q "$SQL" "$SSHU@$HOST:~/m7-ddl.sql" \
  && ok "전송 완료" || { bad "전송 실패"; echo "⛔ 중단"; exit 1; }

echo
echo "[3/4] 적용 (PREFLIGHT → APPLY → POSTFLIGHT)"
echo "----------------------------------------------"
ssh $SSHOPT "$SSHU@$HOST" 'set -a; . ~/mmt-backend.env; set +a; \
  docker run --rm -i mysql:8.0 mysql -h "$RDS_HOST" -P "$RDS_PORT" \
    -u "$RDS_USERNAME" -p"$RDS_PASSWORD" --table "$RDS_NAME" < ~/m7-ddl.sql 2>&1 \
  | grep -v "Using a password on the command line"' | tee "$OUT/ddl-output.txt"
echo "----------------------------------------------"

echo
echo "[4/4] POSTFLIGHT 판정 — 6개 전부 OK 여야 한다"
# 출력 마지막의 6행이 POSTFLIGHT. FAIL 이 하나라도 있으면 미통과.
NOK=$(grep -c "| OK " "$OUT/ddl-output.txt" 2>/dev/null)
NFAIL=$(grep -c "| FAIL " "$OUT/ddl-output.txt" 2>/dev/null)
echo "      OK 행: $NOK · FAIL 행: $NFAIL"

if [ "${NFAIL:-1}" -eq 0 ] && [ "${NOK:-0}" -ge 6 ]; then
  ok "POSTFLIGHT 6개 전부 OK — 결함① 해소"
else
  bad "POSTFLIGHT 미통과 — 위 PREFLIGHT/POSTFLIGHT 표 확인"
  echo "      (멱등 스크립트라 원인 교정 후 재실행 안전)"
fi

# 정리
ssh $SSHOPT "$SSHU@$HOST" 'rm -f ~/m7-ddl.sql'

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ Phase 5-1 통과 — 결함① 해소됨"
  echo "    남은 것: 백엔드 재빌드·배포(5-2) → 인증서·프론트(4d) → Phase 6 검증"
else
  echo " ⛔ Phase 5-1 미통과 — 로그: $OUT/ddl-output.txt"
fi
echo "=============================================="
