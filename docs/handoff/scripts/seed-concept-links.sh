#!/usr/bin/env bash
# M8 ④ 링크 시드 적재 — CSV → SQL 생성 → EC2 경유로 프로덕션 RDS 적재
#
#   미리보기 (읽기 전용, 기본):  bash ~/mmt-seed-links.sh
#   실제 적재:                  bash ~/mmt-seed-links.sh --apply
#
# 입력: ~/mmt-pilot-seed.csv   (concept_id,title,url,provider,display_order)
#       CSV 를 고치면 그대로 반영된다 — 스크립트가 매번 SQL 을 새로 만든다.
#
# 멱등: CSV 에 등장하는 concept_id 들의 기존 행을 지우고 다시 넣는다(= 그 개념의 링크를
#       CSV 상태로 덮어쓴다). 스키마에 (concept_id,url) 유니크 키가 없어 ON DUPLICATE KEY 를
#       못 쓰기 때문 — spec-03 §2.1 스키마를 안 바꾸려는 의도적 선택.
#       ⚠️ CSV 밖에서 손으로 넣은 링크가 그 개념에 있으면 함께 지워진다.
# 전체가 단일 트랜잭션이라 중간 실패 시 아무것도 반영되지 않는다.
#
# 되돌리기: 링크만 즉시 감추려면  UPDATE concept_links SET alive=FALSE;
#           완전 제거는          DELETE FROM concept_links;
#           결측이 계약이라 링크가 0이어도 결과 API·UI 는 정상 동작한다(섹션 생략).
#
# 끝나면 지워도 됨: rm ~/mmt-seed-links.sh ~/mmt-pilot-seed.csv

set -uo pipefail

MODE="${1:-preview}"
CSV="${CSV:-$HOME/mmt-pilot-seed.csv}"
SQL=/tmp/mmt-concept-links-seed.sql
HOST=15.164.145.106
KEY=~/.ssh/mmt-ec2
SSHU=ec2-user
SSHOPT=(-i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 -o LogLevel=ERROR)

echo "=============================================="
if [ "$MODE" = "--apply" ]; then
  echo " M8 ④ 링크 시드 적재 — ⚠️  적재 모드"
else
  echo " M8 ④ 링크 시드 — 미리보기 (읽기 전용)"
fi
echo "=============================================="

# ---------- 1. CSV → SQL ----------
echo
echo "[1] CSV 읽기: $CSV"
[ -f "$CSV" ] || { echo "  ❌ CSV 없음"; exit 1; }

python3 - "$CSV" "$SQL" <<'PY'
import csv, sys, collections
csv_path, sql_path = sys.argv[1], sys.argv[2]
rows, bad = [], []
LIM = {"title": 120, "url": 500, "provider": 50}
with open(csv_path, newline="") as f:
    for i, r in enumerate(csv.DictReader(f), 2):
        cid, t, u, p = r["concept_id"].strip(), r["title"].strip(), r["url"].strip(), r["provider"].strip()
        o = (r.get("display_order") or "0").strip() or "0"
        if not cid.isdigit():            bad.append((i, "concept_id 가 정수 아님")); continue
        if not u.startswith(("http://", "https://")): bad.append((i, "url 이 http(s) 아님")); continue
        for name, val in (("title", t), ("url", u), ("provider", p)):
            if len(val) > LIM[name]:     bad.append((i, f"{name} {len(val)}자 > {LIM[name]}")); break
        else:
            rows.append((int(cid), t, u, p, int(o)))
for i, why in bad: print(f"  ⚠️  {i}행 제외 — {why}")
if not rows:
    print("  ❌ 유효한 행이 0 — 적재할 것이 없다"); sys.exit(1)
esc = lambda s: s.replace("\\", "\\\\").replace("'", "''")
ids = sorted({r[0] for r in rows})
with open(sql_path, "w") as f:
    f.write("-- M8 concept_links 시드 (seed-concept-links.sh 생성 — 직접 수정 금지)\n")
    f.write("START TRANSACTION;\n")
    f.write("DELETE FROM concept_links WHERE concept_id IN (%s);\n" % ",".join(map(str, ids)))
    f.write("INSERT INTO concept_links (concept_id, title, url, provider, display_order, alive) VALUES\n")
    vals = [f"  ({c}, '{esc(t)}', '{esc(u)}', '{esc(p)}', {o}, TRUE)" for c, t, u, p, o in rows]
    f.write(",\n".join(vals) + ";\n")
    f.write("COMMIT;\n")
per = collections.Counter(r[0] for r in rows)
print(f"  ✅ 유효 {len(rows)}링크 / {len(ids)}개념 → {sql_path}")
print("     개념별: " + "  ".join(f"{c}={n}" for c, n in sorted(per.items())))
PY
[ $? -eq 0 ] || { echo "  ⛔ 중단"; exit 1; }

# ---------- 2. 미리보기 ----------
if [ "$MODE" != "--apply" ]; then
  echo
  echo "[2] 생성된 SQL 앞부분"
  head -6 "$SQL" | cut -c1-150 | sed 's/^/      /'
  echo "      …"
  echo
  echo "=============================================="
  echo " 🔍 미리보기만 함 — 프로덕션은 건드리지 않았다"
  echo "    적재하려면:  bash ~/mmt-seed-links.sh --apply"
  echo "=============================================="
  exit 0
fi

# ---------- 3. SSH 도달 ----------
echo
echo "[2] SSH 도달 확인"
if ssh "${SSHOPT[@]}" -o BatchMode=yes "$SSHU@$HOST" true 2>/dev/null; then
  echo "  ✅ SSH 도달"
else
  echo "  ❌ SSH 실패 — 공인 IP 가 바뀌었을 수 있다"
  echo "     먼저:  bash ~/sync-my-ip.sh  →  bash ~/sync-my-ip.sh --apply"
  echo "  ⛔ 중단 — 아무것도 바꾸지 않았다"
  exit 1
fi

# ---------- 4. 전송 + 적재 ----------
echo
echo "[3] SQL 전송"
scp "${SSHOPT[@]}" "$SQL" "$SSHU@$HOST:/tmp/mmt-seed.sql" >/dev/null 2>&1 \
  && echo "  ✅ 전송 완료" || { echo "  ❌ 전송 실패"; exit 1; }

echo
echo "[4] 적재 + 검증"
ssh "${SSHOPT[@]}" "$SSHU@$HOST" 'bash -s' <<'REMOTE'
set -u
pass=0; fail=0
ok(){ echo "  ✅ $1"; pass=$((pass+1)); }
bad(){ echo "  ❌ $1"; fail=$((fail+1)); }

ENVF=""
for c in /home/ec2-user/mmt-backend.env /etc/mmt/backend.env /home/ec2-user/backend.env; do
  [ -f "$c" ] && { ENVF="$c"; break; }
done
[ -n "$ENVF" ] || { bad "env-file 없음"; exit 1; }
set -a; . "$ENVF"; set +a
export MYSQL_PWD="$RDS_PASSWORD"
DB(){ mysql -h "$RDS_HOST" -P "${RDS_PORT:-3306}" -u "$RDS_USERNAME" "$RDS_NAME" "$@"; }
command -v mysql >/dev/null || { bad "mysql 없음 — bash ~/mmt-host.sh --apply"; exit 1; }

BEFORE=$(DB -N -B -e "SELECT COUNT(*) FROM concept_links;")
echo "      적재 전 링크 행 = $BEFORE"

if DB < /tmp/mmt-seed.sql; then ok "적재 트랜잭션 커밋"; else bad "적재 실패 — 롤백됨(부분 반영 없음)"; exit 1; fi

AFTER=$(DB -N -B -e "SELECT COUNT(*) FROM concept_links WHERE alive=TRUE;")
CONC=$(DB -N -B -e "SELECT COUNT(DISTINCT concept_id) FROM concept_links WHERE alive=TRUE;")
[ "$AFTER" -gt 0 ] && ok "alive 링크 $AFTER 개 / $CONC 개념" || bad "적재됐는데 alive 행이 0"

# FK 무결성 — 존재하지 않는 concept_id 가 들어갔으면 애초에 FK 가 막지만 재확인
ORPH=$(DB -N -B -e "SELECT COUNT(*) FROM concept_links cl LEFT JOIN concepts c ON cl.concept_id=c.concept_id WHERE c.concept_id IS NULL;")
[ "$ORPH" = "0" ] && ok "고아 링크 0 (FK 무결성)" || bad "고아 링크 $ORPH 개"

echo
echo "      개념별 적재 결과:"
DB -t -e "
SELECT cl.concept_id, c.concept_name, COUNT(*) AS links
  FROM concept_links cl JOIN concepts c ON c.concept_id = cl.concept_id
 WHERE cl.alive = TRUE
 GROUP BY cl.concept_id, c.concept_name
 ORDER BY links DESC, cl.concept_id;" | sed 's/^/      /'

echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ 링크 시드 적재 완료"
  echo "    라이브 확인: 진단 완주 후 결과 카드에 링크 섹션이 뜨는지 본다"
  echo "    되돌리기:    UPDATE concept_links SET alive=FALSE;"
else
  echo " ⛔ 미완 — 위 ❌ 확인"
fi
echo "=============================================="
REMOTE
