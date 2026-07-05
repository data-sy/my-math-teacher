#!/usr/bin/env bash
#
# switch-backend.sh — blue-green 무중단 전환 (M4 spec-01 §3.4 / Task C)
#
# 현재 활성 색을 감지 → 반대(idle) 색으로 신버전 컨테이너 기동 → 헬스 통과까지 폴링 →
# nginx upstream fragment 를 신버전 색으로 재작성 → `nginx -s reload`(무중단 컷오버) →
# 구버전을 graceful 드레인(`docker stop -t`) 후 제거.
#
# 어느 순간에도 트래픽을 받는 정상 백엔드가 최소 1개 존재 = 다운타임 0 (spec-01 §2.2).
#
# 호출(워크플로 deploy job, spec-01 §3.5 / ADR 0008):
#   deploy/switch-backend.sh <new-image-tag>     # 예: github.sha (immutable)
#
#   배포 채널은 SSM Run Command(SSH 아님, ADR 0008). SSM 명령은 기본 root 로
#   실행되나 이 스크립트는 ec2-user 홈의 파일(compose·deploy·env-file)·상대경로·
#   docker 그룹에 의존한다 → 워크플로가 `runuser -l ec2-user -c 'cd ~ && … bash
#   deploy/switch-backend.sh <sha>'` 로 ec2-user 정체성·작업디렉토리·그룹을 태워
#   호출한다. 따라서 이 스크립트는 "cwd=ec2-user 홈, 실행자=ec2-user(docker 그룹)"를
#   전제한다 — root 로 직접 돌리지 말 것.
#
# ⚠️ 동작 검증은 배포 시점에 한다(EC2 미생성). 본 스크립트는 *형태*를 고정하며,
#    문법은 `bash -n` / shellcheck 로 검증한다.
#
# 시크릿(RDS·JWT·OAuth 자격)은 이 파일에 담지 않는다 — 비커밋 env-file(BACKEND_ENV_FILE)로만
# 주입한다. 여기서 -e 로 박는 값은 전부 비밀이 아닌 배포 토글(프로파일·CTE 플래그·더미 GDB·JVM)뿐.

set -euo pipefail

# ── 입력 ────────────────────────────────────────────────────────────────────
NEW_TAG="${1:-}"
if [[ -z "$NEW_TAG" ]]; then
  echo "usage: $0 <new-image-tag>   (예: github.sha)" >&2
  exit 2
fi

# ── 설정(전부 env 로 오버라이드 가능, 기본값은 §9 프리티어 배치 기준) ────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

IMAGE_REPO="${IMAGE_REPO:?IMAGE_REPO 필요 — 예: <dockerhub-user>/mmt-backend}"   # :TAG 제외
COMPOSE_NET="${COMPOSE_NET:?COMPOSE_NET 필요 — front 와 같은 user-defined 네트워크명 (R3)}"
FRONT_CONTAINER="${FRONT_CONTAINER:-mmt-front}"
# nginx 가 include 하는 fragment 의 *호스트* 경로(컨테이너 /etc/nginx/active-backend.conf 로 마운트됨).
FRAGMENT_HOST_FILE="${FRAGMENT_HOST_FILE:-$SCRIPT_DIR/active-backend.conf}"

BACKEND_PORT="${BACKEND_PORT:-8080}"
HEALTH_PATH="${HEALTH_PATH:-/api/v1/health}"

# 비커밋 env-file — RDS_*/REDIS_*/JWT_SECRET/*_CLIENT_SECRET/EC2_DOMAIN_NAME* 등 비밀을 담는다.
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/home/ec2-user/mmt-backend.env}"

# 메모리 예산(spec-01 §9.4): 2-JVM 공존 피크를 1 GiB 안에 가두는 핵심.
MEM_LIMIT="${MEM_LIMIT:-350m}"
JVM_OPTS="${JVM_OPTS:--XX:MaxRAMPercentage=70}"   # JAVA_TOOL_OPTIONS 로 주입 (CMD 미오버라이드)

# 부팅 중 신규 JVM 이 단일 vCPU 를 독점해 서빙 중인 구버전을 굶기는 것을 막는 선택적 CPU 상한.
# §4 측정에서 t3.micro(1 vCPU) co-located JVM 부팅이 서빙 JVM 지연을 클라 타임아웃까지 밀어내
# 컷오버 창에서 요청 타임아웃이 발생함을 확인(502 아님, 전송갭 아님 — 순수 CPU 경합).
# 빈 값(기본)=무제한(기존 동작 보존). 예: CPU_LIMIT=0.5 → 부팅 컨테이너를 반 코어로 제한.
CPU_LIMIT="${CPU_LIMIT:-}"

# graceful 드레인(spec-01 §2.2-5 / A.5): timeout-per-shutdown-phase(30s) 이상이어야
# docker 가 SIGKILL 로 graceful 을 자르지 않는다. 30 은 하한.
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

# 헬스 폴(R6): 스왑 지연으로 첫 헬스 응답이 늦어지는 것을 거짓 실패로 만들지 않게 넉넉히.
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"     # 시도 횟수
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"    # 시도 간격(초) → 기본 ~150s 윈도우
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-3}"      # 개별 요청 타임아웃(초)
PROBE_IMAGE="${PROBE_IMAGE:-curlimages/curl:8.11.0}"   # 앱 이미지에 curl 가정 안 함 → 일회용 프로브

SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-secure}"

log() { echo "[switch-backend] $*"; }

# ── 1. 활성/유휴 색 감지 ─────────────────────────────────────────────────────
# fragment 가 green 을 가리키면 active=green, 아니면 blue(기본·최초값).
if grep -q 'mmt-backend-green' "$FRAGMENT_HOST_FILE" 2>/dev/null; then
  ACTIVE_COLOR="green"
else
  ACTIVE_COLOR="blue"
fi

# fragment 가 가리키는 색의 컨테이너가 실제로 떠 있어야 "정상 컷오버". 안 떠 있으면 부트스트랩
# (최초 배포) — idle 로 넘기지 말고 그 색에 바로 올리고, 드레인할 구버전도 없다.
if docker ps --format '{{.Names}}' | grep -qx "mmt-backend-$ACTIVE_COLOR"; then
  if [[ "$ACTIVE_COLOR" == "blue" ]]; then NEW_COLOR="green"; else NEW_COLOR="blue"; fi
  OLD_CONTAINER="mmt-backend-$ACTIVE_COLOR"
  log "활성=$ACTIVE_COLOR(가동중) → 신버전을 idle=$NEW_COLOR 로 전환"
else
  NEW_COLOR="$ACTIVE_COLOR"
  OLD_CONTAINER=""
  log "활성 컨테이너 부재 → 부트스트랩 배포: $NEW_COLOR (드레인할 구버전 없음)"
fi

NEW_CONTAINER="mmt-backend-$NEW_COLOR"
NEW_IMAGE="$IMAGE_REPO:$NEW_TAG"

# ── 2. idle 색으로 신버전 기동 ───────────────────────────────────────────────
# 같은 색의 잔존 컨테이너(이전 실패 흔적 등) 정리 후 기동.
docker rm -f "$NEW_CONTAINER" >/dev/null 2>&1 || true

log "기동: $NEW_CONTAINER ($NEW_IMAGE) on net=$COMPOSE_NET"
docker run -d \
  --name "$NEW_CONTAINER" \
  --network "$COMPOSE_NET" \
  --restart unless-stopped \
  --memory "$MEM_LIMIT" \
  ${CPU_LIMIT:+--cpus="$CPU_LIMIT"} \
  --env-file "$BACKEND_ENV_FILE" \
  -e SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
  -e MMT_MIGRATION_USE_MYSQL_CTE_FOR_GRAPH=true \
  -e GDB_URL=localhost -e GDB_PORT=7687 -e GDB_USERNAME=neo4j -e GDB_PASSWORD=dummy \
  -e JAVA_TOOL_OPTIONS="$JVM_OPTS" \
  "$NEW_IMAGE" >/dev/null

# ── 3. 헬스 폴 (전환 게이트) ─────────────────────────────────────────────────
# 같은 네트워크의 일회용 curl 컨테이너로 신버전 헬스 엔드포인트를 폴링.
# 8080 은 외부 비공개(SG)라 호스트가 아니라 docker 네트워크 내부에서 친다.
HEALTH_URL="http://$NEW_CONTAINER:$BACKEND_PORT$HEALTH_PATH"
log "헬스 폴: $HEALTH_URL (최대 $HEALTH_RETRIES회 × ${HEALTH_INTERVAL}s)"

healthy=false
for ((i = 1; i <= HEALTH_RETRIES; i++)); do
  if docker run --rm --network "$COMPOSE_NET" "$PROBE_IMAGE" \
       -fsS --max-time "$HEALTH_TIMEOUT" "$HEALTH_URL" >/dev/null 2>&1; then
    healthy=true
    log "헬스 OK ($i/$HEALTH_RETRIES)"
    break
  fi
  sleep "$HEALTH_INTERVAL"
done

if [[ "$healthy" != true ]]; then
  # 전환 *전* 실패 → fragment 미변경, 구버전 그대로 서비스(영향 0, spec-01 §4.4).
  log "헬스 실패 — 신버전 폐기, 구버전 유지. 최근 로그:" >&2
  docker logs --tail 50 "$NEW_CONTAINER" >&2 2>&1 || true
  docker rm -f "$NEW_CONTAINER" >/dev/null 2>&1 || true
  exit 1
fi

# ── 4. upstream fragment 재작성 (원자적 컷오버 준비) ──────────────────────────
# ⚠️ bind-mount 된 단일 파일이라 `mv` 로 교체하면 inode 가 바뀌어 마운트가 끊긴다.
#    반드시 *제자리 truncate*(`>`)로 내용만 바꿔 inode 를 보존한다.
PREV_FRAGMENT="$(cat "$FRAGMENT_HOST_FILE" 2>/dev/null || true)"
printf 'server %s:%s;\n' "$NEW_CONTAINER" "$BACKEND_PORT" > "$FRAGMENT_HOST_FILE"
log "fragment 재작성 → $NEW_CONTAINER:$BACKEND_PORT"

# ── 5. nginx 검증 + graceful reload (무중단 전환) ────────────────────────────
if ! docker exec "$FRONT_CONTAINER" nginx -t >/dev/null 2>&1; then
  log "nginx -t 실패 — fragment 롤백, 컷오버 중단." >&2
  printf '%s' "$PREV_FRAGMENT" > "$FRAGMENT_HOST_FILE"
  docker rm -f "$NEW_CONTAINER" >/dev/null 2>&1 || true
  exit 1
fi
docker exec "$FRONT_CONTAINER" nginx -s reload
log "nginx reload 완료 — 트래픽이 $NEW_COLOR 로 전환됨"

# ── 6. 구버전 graceful 드레인 후 제거 ────────────────────────────────────────
# docker stop -t N: SIGTERM → 앱의 graceful shutdown(in-flight 흘려보냄) → N초 후 SIGKILL.
# N ≥ timeout-per-shutdown-phase(30s) 라야 graceful 이 중간에 잘리지 않는다(§2.2-5 / A.5).
if [[ -n "$OLD_CONTAINER" ]]; then
  log "구버전 드레인: $OLD_CONTAINER (stop -t ${STOP_TIMEOUT}s)"
  docker stop -t "$STOP_TIMEOUT" "$OLD_CONTAINER" >/dev/null 2>&1 || true
  docker rm "$OLD_CONTAINER" >/dev/null 2>&1 || true
fi

log "전환 완료: 활성=$NEW_COLOR ($NEW_IMAGE)"
