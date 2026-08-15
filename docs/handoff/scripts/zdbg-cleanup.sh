#!/usr/bin/env bash
# 프로덕션 zdbg 테스트 계정 정리 (백로그 m7-diagnostic-test-accounts-cleanup)
#
#   조사만 (읽기 전용, 기본):  bash ~/zdbg-cleanup.sh
#   실제 삭제:                 bash ~/zdbg-cleanup.sh --delete
#
# RDS 는 publicly_accessible=false 라 맥에서 직접 못 붙는다 → EC2 호스트 경유로 실행한다.
# 대상: user_email 또는 user_name 이 'zdbg' 로 시작하는 계정 (zdbg142854 · zdbg2143235)
# 끝나면 지워도 됨: rm ~/zdbg-cleanup.sh
#
# ⚠️ 스키마에 ON DELETE CASCADE 가 하나도 없다 → 자식 테이블부터 수동 삭제.
#    users_tests.diagnosis_id 는 자기참조 FK 라 NULL 로 끊은 뒤 삭제한다.

MODE=${1:-inspect}
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user

echo "=============================================="
if [ "$MODE" = "--delete" ]; then
  echo " zdbg 테스트 계정 — ⚠️  실제 삭제 모드"
else
  echo " zdbg 테스트 계정 — 조사 모드 (읽기 전용)"
fi
echo "=============================================="

ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -o LogLevel=ERROR "$SSHU@$HOST" "bash -s -- $MODE" <<'REMOTE'
set -u
MODE=${1:-inspect}

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

# ---------- 0. RDS 접속 정보 ----------
echo
echo "[0] RDS 접속 정보 (백엔드 env-file)"
ENVF=""
for c in /home/ec2-user/mmt-backend.env /etc/mmt/backend.env /home/ec2-user/backend.env; do
  [ -f "$c" ] && { ENVF="$c"; break; }
done
if [ -z "$ENVF" ]; then
  bad "env-file 을 못 찾음 — 호스트에서 확인:  ls -1 ~/ | grep -i env"
  echo "⛔ 중단"; exit 1
fi
set -a; . "$ENVF"; set +a
[ -n "${RDS_HOST:-}" ] && ok "env-file = $ENVF (RDS_HOST=$RDS_HOST)" \
  || { bad "env-file 에 RDS_HOST 없음 ($ENVF)"; echo "⛔ 중단"; exit 1; }

export MYSQL_PWD="$RDS_PASSWORD"
DB() { mysql -h "$RDS_HOST" -P "${RDS_PORT:-3306}" -u "$RDS_USERNAME" "$RDS_NAME" "$@"; }

command -v mysql >/dev/null || { bad "호스트에 mysql 클라이언트 없음 — 먼저: bash ~/mmt-install-mysql.sh --apply"; echo "⛔ 중단"; exit 1; }
DB -e "SELECT 1" >/dev/null 2>&1 && ok "RDS 접속 성공" || { bad "RDS 접속 실패"; echo "⛔ 중단"; exit 1; }

# ---------- 1. 대상 계정 ----------
echo
echo "[1] 대상 계정 (user_email 또는 user_name 이 'zdbg' 로 시작)"
DB -t -e "
SELECT user_id, user_email, user_name, auth_provider, activated
  FROM users
 WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%';" | sed 's/^/      /'

N=$(DB -N -B -e "SELECT COUNT(*) FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%';")
echo "      계정 수 = $N"
if [ "$N" = "0" ]; then
  ok "zdbg 계정 없음 — 이미 정리됐거나 스냅샷 복원분에 미포함. 할 일 없음"
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " ✅ 정리 불필요 — 백로그 닫아도 됨"
  echo "=============================================="
  exit 0
fi
[ "$N" = "2" ] && ok "예상대로 2개" || bad "예상 2개인데 $N 개 — 위 목록에 실사용자가 섞였는지 눈으로 확인할 것"

# ---------- 2. 자식 행 조사 (FK 영향) ----------
echo
echo "[2] 딸린 행 수 (자식-우선 삭제 순서대로)"
DB -t -e "
SET @u := 'zdbg%';
SELECT 'users_tests' AS tbl, COUNT(*) AS rows_ FROM users_tests
  WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u)
UNION ALL SELECT 'answers', COUNT(*) FROM answers
  WHERE user_test_id IN (SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u))
UNION ALL SELECT 'self_report_answers', COUNT(*) FROM self_report_answers
  WHERE user_test_id IN (SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u))
UNION ALL SELECT 'probabilities(by user_test_id)', COUNT(*) FROM probabilities
  WHERE user_test_id IN (SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u))
UNION ALL SELECT 'probabilities(by answer_id)', COUNT(*) FROM probabilities p JOIN answers a ON p.answer_id=a.answer_id
  WHERE a.user_test_id IN (SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u))
UNION ALL SELECT 'learning_queues', COUNT(*) FROM learning_queues
  WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u)
UNION ALL SELECT 'learning_queue_items', COUNT(*) FROM learning_queue_items
  WHERE queue_id IN (SELECT queue_id FROM learning_queues WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u))
UNION ALL SELECT 'user_authority', COUNT(*) FROM user_authority
  WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE @u OR user_name LIKE @u);" | sed 's/^/      /'

# ---------- 3. 타 사용자 오염 검사 (삭제 안전성의 핵심) ----------
echo
echo "[3] 다른 사용자가 zdbg 데이터를 참조하나 (둘 다 0 이어야 삭제 안전)"
X1=$(DB -N -B -e "
SELECT COUNT(*) FROM users_tests o
 WHERE o.diagnosis_id IN (SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%'))
   AND o.user_id NOT IN (SELECT user_id FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%');")
X2=$(DB -N -B -e "
SELECT COUNT(*) FROM learning_queues q
 WHERE q.user_test_id IN (SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%'))
   AND q.user_id NOT IN (SELECT user_id FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%');")
echo "      타 유저 users_tests.diagnosis_id → zdbg 세션 : $X1"
echo "      타 유저 learning_queues.user_test_id → zdbg 세션 : $X2"
[ "$X1" = "0" ] && [ "$X2" = "0" ] && ok "타 사용자 오염 없음 — 삭제해도 참조 무결성 안전" \
  || bad "타 사용자가 zdbg 데이터를 참조 중 — 삭제 금지, 사람 판단 필요"

# ---------- 4. 삭제 ----------
if [ "$MODE" != "--delete" ]; then
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " 🔍 조사만 함 — 아무것도 지우지 않았다"
  echo "    위 [3] 이 둘 다 0 이고 [1] 목록에 실사용자가 없으면:"
  echo "      bash ~/zdbg-cleanup.sh --delete"
  echo "=============================================="
  exit 0
fi

if [ "$X1" != "0" ] || [ "$X2" != "0" ]; then
  echo; echo "⛔ [3] 오염 검사 실패 — 삭제하지 않고 중단"; exit 1
fi

echo
echo "[4] 삭제 실행 (단일 트랜잭션 · 자식 우선)"
DB -v <<'SQL' 2>&1 | grep -iE "affected|error" | sed 's/^/      /'
CREATE TEMPORARY TABLE zdbg_ids AS
  SELECT user_id FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%';
CREATE TEMPORARY TABLE zdbg_uts AS
  SELECT user_test_id FROM users_tests WHERE user_id IN (SELECT user_id FROM zdbg_ids);

START TRANSACTION;
DELETE FROM learning_queue_items WHERE queue_id IN
  (SELECT queue_id FROM learning_queues WHERE user_id IN (SELECT user_id FROM zdbg_ids));
DELETE FROM learning_queues WHERE user_id IN (SELECT user_id FROM zdbg_ids);
DELETE p FROM probabilities p JOIN answers a ON p.answer_id = a.answer_id
  WHERE a.user_test_id IN (SELECT user_test_id FROM zdbg_uts);
DELETE FROM probabilities WHERE user_test_id IN (SELECT user_test_id FROM zdbg_uts);
DELETE FROM self_report_answers WHERE user_test_id IN (SELECT user_test_id FROM zdbg_uts);
DELETE FROM answers WHERE user_test_id IN (SELECT user_test_id FROM zdbg_uts);
UPDATE users_tests SET diagnosis_id = NULL WHERE user_id IN (SELECT user_id FROM zdbg_ids);
DELETE FROM users_tests WHERE user_id IN (SELECT user_id FROM zdbg_ids);
DELETE FROM user_authority WHERE user_id IN (SELECT user_id FROM zdbg_ids);
DELETE FROM users WHERE user_id IN (SELECT user_id FROM zdbg_ids);
COMMIT;
SQL

# ---------- 5. 사후 검증 ----------
echo
echo "[5] 사후 검증"
R=$(DB -N -B -e "SELECT COUNT(*) FROM users WHERE user_email LIKE 'zdbg%' OR user_name LIKE 'zdbg%';")
[ "$R" = "0" ] && ok "zdbg 계정 0개 — 삭제 완료" || bad "아직 $R 개 남음"

ORPHAN=$(DB -N -B -e "
SELECT (SELECT COUNT(*) FROM users_tests t LEFT JOIN users u ON t.user_id=u.user_id WHERE u.user_id IS NULL AND t.user_id IS NOT NULL)
     + (SELECT COUNT(*) FROM learning_queues q LEFT JOIN users u ON q.user_id=u.user_id WHERE u.user_id IS NULL)
     + (SELECT COUNT(*) FROM learning_queue_items i LEFT JOIN learning_queues q ON i.queue_id=q.queue_id WHERE q.queue_id IS NULL)
     + (SELECT COUNT(*) FROM self_report_answers s LEFT JOIN users_tests t ON s.user_test_id=t.user_test_id WHERE t.user_test_id IS NULL);")
echo "      고아 행 합계 = $ORPHAN"
[ "$ORPHAN" = "0" ] && ok "고아 행 0 — 참조 무결성 유지" || bad "고아 행 $ORPHAN 개 — 확인 필요"

echo "      남은 전체 사용자 수 = $(DB -N -B -e 'SELECT COUNT(*) FROM users;')"

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ zdbg 테스트 계정 정리 완료"
else
  echo " ⛔ 미완 — 위 ❌ 확인 (트랜잭션이라 부분 삭제는 없음)"
fi
echo "=============================================="
REMOTE
