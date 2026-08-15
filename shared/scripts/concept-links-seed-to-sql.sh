#!/usr/bin/env bash
# M8 spec-03 §2.2 — 링크 시드 CSV 를 적재용 SQL 로 변환한다 (stdout).
#
#   bash shared/scripts/concept-links-seed-to-sql.sh > /tmp/seed.sql
#   mysql -h <HOST> -P <PORT> -u <USER> -p mmt < /tmp/seed.sql
#
# 입력: shared/data/concept-links-seed.csv
#   concept_id,title,url,provider,display_order
#   컬럼에 쉼표가 필요하면 큰따옴표로 감싼다 (예: "무료 강의, 1강").
#
# 재실행 안전: CSV 에 등장하는 concept_id 들의 기존 행을 먼저 지우고 다시 넣는다
#   (= 그 개념들의 링크를 CSV 상태로 "덮어쓴다"). 스키마에 (concept_id,url) 유니크 키가
#   없어 ON DUPLICATE KEY 를 못 쓰기 때문 — spec §2.1 스키마를 바꾸지 않으려는 의도적 선택이다.
#   ⚠️ 따라서 CSV 밖에서 손으로 넣은 링크가 그 개념에 있으면 함께 지워진다.
# 전체가 한 트랜잭션이라 중간 실패 시 아무것도 반영되지 않는다.
#
# 큐레이션(어떤 개념에 어떤 자료를 붙일지)은 사람 몫이다. 대상 개념 고르기 =
# shared/scripts/select-bottleneck-concepts.sql (병목 상위 30~50).

set -euo pipefail

CSV="${1:-$(dirname "$0")/../data/concept-links-seed.csv}"

[ -f "$CSV" ] || { echo "시드 CSV 없음: $CSV" >&2; exit 1; }

awk -F',' '
function trim(s) { sub(/^[ \t\r]+/, "", s); sub(/[ \t\r]+$/, "", s); return s }
function unquote(s) {
  s = trim(s)
  if (s ~ /^".*"$/) { s = substr(s, 2, length(s) - 2); gsub(/""/, "\"", s) }
  return s
}
function esc(s) { gsub(/\\/, "\\\\", s); gsub(/'"'"'/, "'"'"''"'"'", s); return s }

BEGIN { rows = 0 }
NR == 1 { next }                      # 헤더
/^[ \t]*$/ { next }                   # 빈 줄
{
  # 큰따옴표 안의 쉼표를 보호하려고 필드를 직접 파싱한다
  line = $0; n = 0; field = ""; inq = 0
  for (i = 1; i <= length(line); i++) {
    ch = substr(line, i, 1)
    if (ch == "\"") { inq = !inq; field = field ch }
    else if (ch == "," && !inq) { n++; f[n] = field; field = "" }
    else field = field ch
  }
  n++; f[n] = field

  if (n < 4) { printf("-- SKIP(컬럼 부족 %d개): %s\n", n, line); next }

  cid = unquote(f[1]); title = unquote(f[2]); url = unquote(f[3])
  prov = unquote(f[4]); ord = (n >= 5 ? unquote(f[5]) : "0")
  if (ord == "") ord = "0"

  if (cid !~ /^[0-9]+$/) { printf("-- SKIP(concept_id 가 정수 아님): %s\n", line); next }
  if (url !~ /^https?:\/\//) { printf("-- SKIP(url 이 http(s) 아님): %s\n", line); next }

  ids[cid] = 1
  rows++
  vals[rows] = sprintf("  (%s, '"'"'%s'"'"', '"'"'%s'"'"', '"'"'%s'"'"', %s, TRUE)", cid, esc(title), esc(url), esc(prov), ord)
}
END {
  if (rows == 0) {
    print "-- 시드 CSV 에 유효한 행이 없다 — 큐레이션 먼저. 아무것도 반영하지 않는다."
    print "SELECT '"'"'seed rows = 0 (no-op)'"'"' AS note;"
    exit 0
  }
  idlist = ""
  for (id in ids) idlist = (idlist == "" ? id : idlist "," id)

  print "-- M8 concept_links 시드 (concept-links-seed-to-sql.sh 생성 — 직접 수정 금지)"
  print "START TRANSACTION;"
  printf("DELETE FROM concept_links WHERE concept_id IN (%s);\n", idlist)
  print "INSERT INTO concept_links (concept_id, title, url, provider, display_order, alive) VALUES"
  for (i = 1; i <= rows; i++) printf("%s%s\n", vals[i], (i == rows ? ";" : ","))
  print "COMMIT;"
  print ""
  print "-- 적재 확인"
  print "SELECT COUNT(*) AS link_rows, COUNT(DISTINCT concept_id) AS covered_concepts FROM concept_links WHERE alive = TRUE;"
}
' "$CSV"
