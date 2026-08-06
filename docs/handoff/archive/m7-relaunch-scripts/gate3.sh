#!/usr/bin/env bash
# M7 재런치 GATE 3 — DNS 전파 확인 (읽기 전용, AWS 자격 불요)
# 실행: bash ~/gate3.sh
# A레코드를 만든 뒤 돌린다. 전파 대기 중이면 몇 분 간격으로 재실행.
# 끝나면 지워도 됨: rm ~/gate3.sh

TARGET=15.164.145.106
HOST=www.my-math-teacher.com
APEX=my-math-teacher.com

pass=0; fail=0
ok()  { echo "  ✅ $1"; pass=$((pass+1)); }
bad() { echo "  ❌ $1"; fail=$((fail+1)); }

echo "=============================================="
echo " GATE 3 — DNS 재연결 확인"
echo " 목표: $HOST → $TARGET"
echo "=============================================="

# ---------- 권위 네임서버에 직접 (캐시 우회 — 가장 빠른 진실) ----------
echo
echo "[1/3] 권위 네임서버 직접 조회 (캐시 우회)"
NS=$(dig +short NS "$APEX" | head -1)
if [ -z "$NS" ]; then
  bad "네임서버를 못 찾음 — 도메인 설정 확인"
else
  echo "      권위 NS: $NS"
  AUTH=$(dig +short @"$NS" "$HOST" A | tail -1)
  echo "      응답   : ${AUTH:-(레코드 없음)}"
  if [ "$AUTH" = "$TARGET" ]; then
    ok "권위 서버가 새 EIP 를 반환 — 레코드 생성 완료"
  elif [ -z "$AUTH" ]; then
    bad "권위 서버에 A레코드 없음 — 아직 안 만들었거나 이름이 다름"
  else
    bad "권위 서버가 다른 IP 반환: $AUTH (기대 $TARGET)"
  fi
fi

# ---------- 공개 리졸버 (실사용자 관점) ----------
echo
echo "[2/3] 공개 리졸버 전파 (실사용자·certbot 관점)"
for R in 8.8.8.8 1.1.1.1 168.126.63.1; do
  V=$(dig +short @"$R" "$HOST" A 2>/dev/null | tail -1)
  printf "      %-15s → %s\n" "$R" "${V:-(없음)}"
done
LOCAL=$(dig +short "$HOST" A | tail -1)
echo "      (로컬 리졸버)    → ${LOCAL:-(없음)}"
[ "$LOCAL" = "$TARGET" ] \
  && ok "로컬 리졸버까지 전파됨" \
  || bad "아직 전파 중 — 몇 분 뒤 재실행 (권위 서버가 맞으면 시간 문제)"

# ---------- 포트 도달 (참고용, 게이트 아님) ----------
echo
echo "[3/3] 참고 — 호스트 포트 상태 (Phase 4 전이라 닫혀 있는 게 정상)"
for P in 80 443 22; do
  if nc -z -G 3 "$TARGET" "$P" 2>/dev/null; then
    echo "      $P/tcp: 열림"
  else
    echo "      $P/tcp: 닫힘/무응답 (Phase 4 에서 nginx 올리면 80·443 열림)"
  fi
done

# ---------- 판정 ----------
echo
echo "=============================================="
echo " 통과 $pass · 실패 $fail"
if [ "$fail" -eq 0 ]; then
  echo " ✅ GATE 3 통과 — Phase 4(호스트 복원) 진행 가능"
  echo "    certbot 이 이 DNS 를 요구하므로 여기가 선행 조건이었다"
else
  echo " ⛔ GATE 3 미통과"
  echo "    권위 NS 가 맞는데 리졸버만 틀리면 → 전파 대기(수 분~수십 분), 재실행"
  echo "    권위 NS 부터 틀리면   → 레코드 생성 자체를 다시 확인"
fi
echo "=============================================="
