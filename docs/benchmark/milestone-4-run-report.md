# Milestone 4 — 배포/무중단 측정 런 리포트

> 큐레이션 정본. raw 로그는 `infra/terraform/run-logs/<UTC>/`(gitignored)에서 이 문서로 옮겨 적는다.
> 텔레메트리 수집: `infra/terraform/run-log.sh` (init/mark/tf-apply/ec2/cost). 헤드라인 목표 = spec-01 §4 유실률 0%.
> 진단 서사(오류를 어떻게 좁혔는지)는 짝 문서 `milestone-4-troubleshooting-2026-07-05.md`.

## 런 메타

| 항목 | 값 |
|---|---|
| 런 UTC | 2026-07-05T05:35:20Z |
| branch | feat/m4-spec-01-zero-downtime-deployment |
| 배포 이미지 | `mmt2024/mmt-backend:1b221d5…` (github.sha) |
| 리전 / 계정 | ap-northeast-2 / 471934607256 |
| 인스턴스 / DB | EC2 t3.micro / RDS db.t3.micro (Single-AZ, 20GB) |
| 비용 모델 | 신규 크레딧(유료 플랜, $200 선차감) |

## 1. 시간 (timeline.tsv)

| phase | UTC (approx) | 비고 |
|---|---|---|
| apply-start → complete | 05:35:20 → ~05:41 | 18 리소스, RDS가 병목(~5분) |
| RDS 재시드 | ~05:49 → 05:52 | 647·1631·3446 (유실0) |
| EC2 재프로비저닝 + compose up | ~05:57 | front+redis, nginx 콜드부트 OK |
| 첫 배포 트리거(SSM) | 06:05:23 | feat ref, skip_tests=true |
| deploy job success | ~06:06 (deploy 48s) | blue 부트스트랩 |
| nginx Host 버그 수정·reload | ~06:20 | 커밋 `8dc97eb` |
| **컷오버(blue→green)** | 06:36:54 → flip 06:37:25 | 헬스 OK 5/30(~25s) 뒤 flip |
| destroy | ~06:59 (18 destroyed) | ⚠️ 06:54 1차 시도는 자격 만료로 실패 → MFA 갱신 후 성공 |

## 2. 비용 (추정 → 실제 대조)

- 인프라 가동 구간: 약 05:35 → 06:59 UTC ≈ **1.4h** (EC2 t3.micro + RDS db.t3.micro + EIP).
- **실제**: 다음날 Cost Explorer / Billing 대조 필요 ← 24h+ 지연. (`run-log.sh cost`는 리소스-시간 추정만.)
- destroy 확인: **18 destroyed, 잔여 managed 리소스 0** → EIP IPv4 상시 차감 정지.

## 3. 용량 / 스토리지

| 대상 | 설정 | 실사용 (steady 스냅샷) |
|---|---|---|
| 메모리 (호스트) | 916MB + swap 2047MB | used 293 / avail 478, swap used 283 |
| RDS 스토리지 | 20GB | 재시드 데이터 소량 |
| Docker 컨테이너 | — | front 2MB · redis 1.8MB · backend(아래 §4) |

## 4. 메모리 ★ (§9.4 핵심 — 1GiB + 2GB swap)

> 컷오버 순간 blue+green JVM 2개 공존이 이 마일스톤의 스트레스 지점.

| 시점 | 백엔드 컨테이너 메모리 | 비고 |
|---|---|---|
| steady (blue만) | **78.6MiB / 350MiB (22%)** | 단일 JVM 여유 큼 |
| **cutover (2-JVM 공존)** | green 부팅 피크 **247.8MiB / 350MiB (70.8%)** | `MaxRAMPercentage=70` 정확히 일치. blue(~78MiB)와 공존 t≈2–38s |
| post (green만) | 200 정상 서비스 | mem_limit 350m 내 |

→ **2-JVM 공존이 1GiB + swap 예산 안에서 성립.** mem_limit 350m가 개별 JVM을 ~245MiB(=70%)로 가둬 컷오버 피크를 방어.

## 5. 부하 / 유실률 (헤드라인 — spec-01 §4)

부하도구: **k6** (로컬 → EIP:80), 대표 GET `/api/v1/concepts/nodes/7925` (30KB, DB+CTE), `constant-arrival-rate` **10rps**.
RATE=10 채택 근거: 30rps는 t3.micro(1 vCPU)에서 붕괴 → steady 실패 0인 최대치로 페어니스 확보.

| 구성 | req 총계 | http_req_failed | 판정 |
|---|---|---|---|
| ① steady | 301 | **0%** | 기준 정상성 ✅ |
| **② Before** (단일 `docker restart`) | 900 | **60.3%** (543 = **502**) | 구식 재배포 다운타임 ✅ 실측 |
| **③ After** (blue-green) | 1301 | 100% (**401**, 502 아님) | ⚠️ **오염** — 아래 |

- **컷오버 메커니즘 자체는 전송 갭 0**: switch 로그상 blue가 green 헬시까지 서비스 후 fragment flip+reload+drain. After의 실패는 **401(green이 응답)**이지 502/커넥션 드롭이 아님 → 요청이 드롭되지 않음.
- **③ After 오염 원인**: Redis 크로스컨테이너 캐시 역직렬화 결함(green이 blue의 캐시 List를 String으로 읽어 `ClassCastException`→401). FLUSHALL 후 green steady는 **991/991 200**(자기일관성). 상세 = 짝 문서 §3.
- 지연: 콜드 첫 요청 1.64s(웜업), 웜 ~60ms, p95 137ms@10rps.

## 결론 / 클레임

- **무중단 배포 인프라(SSM→runuser→blue-green)는 라이브에서 완주**했고 **컷오버는 전송 갭 없이(요청 드롭 0)** 동작했다. 구식 in-place 재배포는 같은 부하에서 **60.3% 유실(502)** — 무중단 방식의 이득은 방향상 명확.
- 그러나 **"After 유실 0%"의 클린 수치는 아직 확정하지 못했다.** 데이터 엔드포인트가 **Redis 크로스컨테이너 캐시 결함**으로 401을 응답해 측정이 오염됐기 때문이다(전송 갭이 아니라 앱 결함). 이 결함은 `/health`만 보는 스모크 게이트가 놓치는 지점이기도 하다.
- **다음**: 캐시 결함 수정(로컬 Testcontainers) → 스모크 게이트를 데이터 엔드포인트로 강화 → Before/After를 상태코드 분해 프로브로 재측정. 상세 계획은 루트 `m4-worklog.md`.
