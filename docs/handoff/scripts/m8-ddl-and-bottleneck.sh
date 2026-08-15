#!/usr/bin/env bash
# M8 ③ concept_links 프로덕션 DDL 적용 + ④ 링크 시드 대상(병목 상위 50) 추출
#
#   점검만 (읽기 전용, 기본):  bash ~/mmt-m8.sh
#   실제 적용 + 목록 추출:     bash ~/mmt-m8.sh --apply
#
# RDS 는 publicly_accessible=false 라 맥에서 직접 못 붙는다 → EC2 호스트 경유.
# DDL 은 브랜치(feat/m8-concept-links)의 api/sql/m8-apply-concept-links-ddl-prod.sql 과
# 동일한 내용을 여기 인라인으로 담았다 — 그 브랜치가 origin 에 없어 raw.githubusercontent
# 로는 못 받기 때문. (푸시된 뒤에는 curl 방식을 써도 된다.)
#
# 성질: additive · 멱등(CREATE TABLE IF NOT EXISTS) · 구 경로 미참조라 구 기능 무영향.
#       테이블이 비어 있어도 결과 API 는 정상(링크 결측이 계약 — spec-03 §2.2).
#       롤백 = 테이블 방치. 링크만 즉시 감추려면 UPDATE concept_links SET alive=FALSE.
# ⚠️ MySQL DDL 은 문장별 암묵 커밋 — 트랜잭션 롤백 불가. 그래서 PREFLIGHT 를 먼저 본다.
#
# 끝나면 지워도 됨: rm ~/mmt-m8.sh

set -uo pipefail

MODE="${1:-check}"
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
OUT=~/mmt-bottleneck-top50.tsv

echo "=============================================="
if [ "$MODE" = "--apply" ]; then
  echo " M8 ③ DDL 적용 + ④ 병목 목록 추출 — ⚠️  적용 모드"
else
  echo " M8 ③④ — 점검 모드 (읽기 전용)"
fi
echo "=============================================="

# ---------- SSH 도달 선검사 (증상으로 판정) ----------
echo
echo "[0] SSH 도달 확인 (공인 IP 드리프트 = 재발성 함정)"
if ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 \
       -o BatchMode=yes "$SSHU@$HOST" true 2>/dev/null; then
  echo "  ✅ SSH 도달"
else
  echo "  ❌ SSH 실패 — SG 인그레스가 옛 공인 IP 를 물고 있을 가능성이 높다"
  echo "     먼저:  bash ~/sync-my-ip.sh          (진단)"
  echo "     그 다음: bash ~/sync-my-ip.sh --apply  (SG 규칙 1건 적용)"
  echo "  ⛔ 중단 — 아무것도 바꾸지 않았다"
  exit 1
fi

ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 -o LogLevel=ERROR \
    "$SSHU@$HOST" "bash -s -- $MODE" <<'REMOTE'
set -u
MODE=${1:-check}
pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

# ---------- 1. RDS 접속 ----------
echo
echo "[1] RDS 접속 정보 (백엔드 env-file)"
ENVF=""
for c in /home/ec2-user/mmt-backend.env /etc/mmt/backend.env /home/ec2-user/backend.env; do
  [ -f "$c" ] && { ENVF="$c"; break; }
done
[ -n "$ENVF" ] || { bad "env-file 을 못 찾음 — ls -1 ~/ | grep -i env"; echo "⛔ 중단"; exit 1; }
set -a; . "$ENVF"; set +a
[ -n "${RDS_HOST:-}" ] && ok "env-file = $ENVF" || { bad "RDS_HOST 없음"; echo "⛔ 중단"; exit 1; }

export MYSQL_PWD="$RDS_PASSWORD"
DB() { mysql -h "$RDS_HOST" -P "${RDS_PORT:-3306}" -u "$RDS_USERNAME" "$RDS_NAME" "$@"; }
command -v mysql >/dev/null || { bad "mysql 클라이언트 없음 — 먼저: bash ~/mmt-install-mysql.sh --apply"; echo "⛔ 중단"; exit 1; }
DB -e "SELECT 1" >/dev/null 2>&1 && ok "RDS 접속 성공 ($RDS_NAME)" || { bad "RDS 접속 실패"; echo "⛔ 중단"; exit 1; }

# ---------- 2. PREFLIGHT ----------
echo
echo "[2] PREFLIGHT — 현재 상태 (read-only)"
EXISTS=$(DB -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES
                       WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links';")
CONCEPTS=$(DB -N -B -e "SELECT COUNT(*) FROM concepts;")
KS=$(DB -N -B -e "SELECT COUNT(*) FROM knowledge_space;")
echo "      concept_links 테이블 : $([ "$EXISTS" = 0 ] && echo MISSING || echo EXISTS)"
echo "      concepts / knowledge_space : $CONCEPTS / $KS"
[ "$CONCEPTS" -gt 0 ] && ok "선행 테이블(concepts) 정상" || { bad "concepts 비어 있음"; echo "⛔ 중단"; exit 1; }

if [ "$MODE" != "--apply" ]; then
  echo
  echo "=============================================="
  echo " 통과 $pass · 실패 $fail"
  echo " 🔍 점검만 함 — 아무것도 바꾸지 않았다"
  [ "$EXISTS" = 0 ] && echo "    concept_links 없음 → 적용하려면:  bash ~/mmt-m8.sh --apply" \
                    || echo "    concept_links 이미 존재 → --apply 해도 멱등(무해)"
  echo "=============================================="
  exit 0
fi

# ---------- 3. APPLY (멱등) ----------
echo
echo "[3] APPLY — concept_links 생성 (멱등)"
DB <<'SQL'
CREATE TABLE IF NOT EXISTS concept_links (
	concept_link_id BIGINT auto_increment,
	concept_id      INT          NOT NULL,
	title           VARCHAR(120) NOT NULL,
	url             VARCHAR(500) NOT NULL,
	provider        VARCHAR(50)  NOT NULL,
	display_order   INT          NOT NULL DEFAULT 0,
	alive           BOOLEAN      NOT NULL DEFAULT TRUE,
	last_checked_at TIMESTAMP    NULL,
	created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (concept_link_id),
	CONSTRAINT fk_cl_concept FOREIGN KEY (concept_id) REFERENCES concepts (concept_id),
	INDEX idx_cl_concept (concept_id)
);
SQL
[ $? -eq 0 ] && ok "DDL 실행 완료" || bad "DDL 실행 실패"

# ---------- 4. POSTFLIGHT (3줄 전부 OK 여야 성공) ----------
echo
echo "[4] POSTFLIGHT"
T=$(DB -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links';")
F=$(DB -N -B -e "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links' AND CONSTRAINT_NAME='fk_cl_concept';")
I=$(DB -N -B -e "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='concept_links' AND INDEX_NAME='idx_cl_concept';")
[ "$T" -gt 0 ] && ok "concept_links (table)" || bad "concept_links (table)"
[ "$F" -gt 0 ] && ok "fk_cl_concept (fk)"    || bad "fk_cl_concept (fk)"
[ "$I" -gt 0 ] && ok "idx_cl_concept (idx)"  || bad "idx_cl_concept (idx)"
echo "      현재 링크 행 수 = $(DB -N -B -e 'SELECT COUNT(*) FROM concept_links;') (시드 전이면 0 — 정상)"

# ---------- 5. ④ 링크 시드 대상 = 병목 상위 50 ----------
# 앱의 countBlockedDescendants 와 동일한 재귀(depth 3·같은 조인 방향·자기 제외).
echo
echo "[5] 링크 시드 대상 추출 — 병목(blockedDescendants) 상위 50"
DB -N -B > /tmp/mmt-bottleneck-top50.tsv <<'SQL'
WITH RECURSIVE blocked_path AS (
    SELECT concept_id AS root, concept_id, 0 AS depth FROM concepts
    UNION ALL
    SELECT bp.root, c.concept_id, bp.depth + 1
    FROM blocked_path bp
    JOIN knowledge_space ks ON bp.concept_id = ks.to_concept_id
    JOIN concepts c          ON ks.from_concept_id = c.concept_id
    WHERE bp.depth < 3
)
SELECT bp.root, c.concept_name, COUNT(DISTINCT bp.concept_id) - 1 AS blocked_descendants,
       (SELECT COUNT(*) FROM concept_links cl WHERE cl.concept_id = bp.root AND cl.alive = TRUE)
FROM blocked_path bp
JOIN concepts c ON c.concept_id = bp.root
GROUP BY bp.root, c.concept_name
HAVING blocked_descendants > 0
ORDER BY blocked_descendants DESC, bp.root ASC
LIMIT 50;
SQL
ROWS=$(wc -l < /tmp/mmt-bottleneck-top50.tsv)
[ "$ROWS" -gt 0 ] && ok "상위 $ROWS 개 추출 → /tmp/mmt-bottleneck-top50.tsv" || bad "추출 실패"
echo "      상위 10 미리보기 (concept_id · 이름 · 막히는 후수 수 · 기존 링크 수):"
head -10 /tmp/mmt-bottleneck-top50.tsv | sed 's/^/        /'

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ ③ DDL 적용 완료 · ④ 시드 대상 50개 추출 완료"
else
  echo " ⛔ 미완 — 위 ❌ 확인"
fi
echo "=============================================="
REMOTE

RC=$?

# ---------- 목록 회수 ----------
if [ "$MODE" = "--apply" ] && [ $RC -eq 0 ]; then
  echo
  echo "[6] 목록을 맥으로 회수"
  if scp -i "$KEY" -o StrictHostKeyChecking=accept-new \
         "$SSHU@$HOST:/tmp/mmt-bottleneck-top50.tsv" "$OUT" 2>/dev/null; then
    echo "  ✅ $OUT ($(wc -l < "$OUT" | tr -d ' ') 줄)"
    echo "     → 이 목록이 ④ 콘텐츠 큐레이션의 대상이다."
  else
    echo "  ❌ scp 실패 — 호스트의 /tmp/mmt-bottleneck-top50.tsv 를 직접 가져올 것"
  fi
fi

exit $RC
