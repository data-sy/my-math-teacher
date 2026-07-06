# Milestone 4 — 배포/무중단 측정 런 리포트

> 큐레이션 정본. raw 로그는 `infra/terraform/run-logs/<UTC>/`(gitignored)에서 이 문서로 옮겨 적는다.
> 텔레메트리 수집: `infra/terraform/run-log.sh` (init/mark/tf-apply/ec2/cost). 헤드라인 목표 = spec-01 §4 유실률 0%.
> 진단 서사(오류를 어떻게 좁혔는지)는 짝 문서 `milestone-4-troubleshooting-2026-07-05.md`.

## 측정 런 이력

| 런 UTC | 성격 | 상태 |
|---|---|---|
| 2026-07-05T05:35:20Z | 1차 라이브(SSM 첫 검증, §4 Before 실측) | After 는 **Redis 캐시 결함으로 오염** — 아래 §5 로 대체 |
| **2026-07-05T08:30:49Z** | **2차 = §4 확정 측정**(캐시픽스 반영, After 3단 규명) | ✅ **본 리포트 정본** |
| 2026-07-06T01:35:41Z | 3차 = **backlog ①② 라이브 재검증**(워크플로 CPU_LIMIT 배선·데이터 smoke 게이트) | ✅ 아래 "3차 재검증" |

두 런 모두 인프라는 apply→측정→destroy 로 짧게 닫았다(크레딧 방어). **Before(60.3%)는 1차에서 실측·유효**,
**After 클린 수치는 2차에서 확정.** 아래 수치는 2차(08:30) 기준이며, Before 만 1차 실측을 재인용한다.

## 런 메타 (2차, 확정)

| 항목 | 값 |
|---|---|
| 런 UTC | 2026-07-05T08:30:49Z |
| branch | feat/m4-spec-01-zero-downtime-deployment |
| 배포 이미지 | `mmt2024/mmt-backend:1944230c2fdb148…` (github.sha, **캐시픽스 포함**) |
| 리전 / 계정 | ap-northeast-2 / 471934607256 |
| 인스턴스 / DB | EC2 **t3.micro (1 vCPU)** / RDS db.t3.micro (Single-AZ, 20GB) |
| 비용 모델 | 신규 크레딧(유료 플랜, $200 선차감) |

## 1. 시간 (timeline)

| phase | UTC (approx) | 비고 |
|---|---|---|
| apply-start → complete | 08:30:49 → ~08:37 | 18 리소스, RDS 생성 **5m16s** 병목 |
| RDS 재시드 | ~08:44 | chapters **647**·concepts **1631**·knowledge_space **3446** (유실0) |
| EC2 재프로비저닝 + compose up | ~08:49 | front(nginx)+redis, `/`=200·`/health`=502 콜드부트 OK |
| SSM 배포(feat ref, skip_tests) | 08:52:27 → 09:03:45 | build+deploy, **blue 부트스트랩**, deploy=Success |
| smoke | ~09:05 | `/health`=200 · `/nodes/7925`=200 **body 20520B(non-empty)** |
| steady 게이트 (15s) | ~09:07 | 151/151 200, **401=0**(캐시픽스 확증) |
| **컷오버 R1 (blue→green, 무제한)** | 09:08:29 → reload ~09:08:57 | 헬스 5/30(~25s) 뒤 flip |
| **컷오버 R2 (green→blue2, 무제한)** | 09:16:27 → reload ~09:16:57 | 넉넉마진 재측정 → **경합 노출** |
| **컷오버 R3 (blue→green2, `CPU_LIMIT=0.5`)** | 09:24:03 → reload ~09:25:03 | 헬스 12/30(~60s, 느린부팅) 뒤 flip |
| destroy | ~09:2X (18 destroyed) | 크레딧 방어 |

## 2. 비용 (추정 → 실제 대조)

- 인프라 가동 구간: 약 08:30 → 09:2X UTC ≈ **1.0h** (EC2 t3.micro + RDS db.t3.micro + EIP).
- **실제**: 다음날 Cost Explorer / Billing 대조 필요 ← 24h+ 지연. (`run-log.sh cost`는 리소스-시간 추정만.)
- destroy 확인: **18 destroyed, 잔여 managed 리소스 0** → EIP IPv4 상시 차감 정지.

## 3. 용량 / 스토리지

| 대상 | 설정 | 실사용 (컷오버 스냅샷) |
|---|---|---|
| 메모리 (호스트) | 916MB + swap 2047MB | used ~547–563 / avail ~209–225, swap used ~315–318MB |
| RDS 스토리지 | 20GB | 재시드 데이터 소량 |
| Docker 컨테이너 | — | front ~2MB · redis ~2MB · backend(아래 §4) |

## 4. 메모리 · CPU ★ (§9.4 핵심 — 1 vCPU + 1GiB + 2GB swap)

> 컷오버 순간 blue+green JVM 2개 공존이 이 마일스톤의 스트레스 지점. **이번 측정의 핵심 발견은 메모리가 아니라 CPU** 였다.

| 컷오버 스냅샷 | 부팅 컨테이너 CPU | 부팅 MEM | 서빙 컨테이너 CPU |
|---|---|---|---|
| R1 (blue→green, 무제한) | green 0.22%* | 221MiB / 350MiB (63%) | blue 11.28% |
| **R2 (green→blue2, 무제한)** | **blue2 148.72%** ← 1 vCPU 독점 | 154.6MiB (44%) | green 12.43% |
| **R3 (blue→green2, `CPU_LIMIT=0.5`)** | **green2 55.39%** ← 반코어 캡 | 67.96MiB (19%) | blue 15.50% |

\* R1 스냅샷은 부팅 CPU 스파이크가 지난 직후(green 헬시 +4s)에 찍혀 스파이크를 놓침 — R2 가 부팅 한복판(Up 13s)을 포착.

- **메모리 예산은 충분**: 2-JVM 공존 피크가 개별 ~245MiB(=`MaxRAMPercentage=70`)로 `mem_limit 350m` 안에 들고, 호스트 swap 여유도 큼. 1차 런의 green 피크 247.8MiB(70.8%)와 정합.
- **병목은 단일 vCPU**: 무제한이면 신규 JVM 부팅(JIT·클래스로딩·Spring 컨텍스트)이 **148% CPU** 로 코어를 독점 → 서빙 JVM 을 굶긴다. `--cpus=0.5` 캡이 이걸 **55%** 로 눌러 서빙 컨테이너에 CPU 여유(15%)를 남긴다.

## 5. 부하 / 유실률 ★ (헤드라인 — spec-01 §4)

부하도구: **k6** (로컬 → EIP:80), 대표 GET `/api/v1/concepts/nodes/7925` (~20KB, DB+CTE), `constant-arrival-rate` **10rps**.
프로브 `loss-probe2.js`(상태분해): 정본 지표 = `status_502 + transport_err == 0`(전송 유실). 401(앱)·502(전송갭)·transport(연결/타임아웃) 분리 집계.
RATE=10 근거: 30rps 는 t3.micro(1 vCPU)에서 붕괴 → steady 실패 0 인 최대치로 페어니스 확보.

| 구성 | req | 502 | transport_err | 401 | http_req_failed | 판정 |
|---|---|---|---|---|---|---|
| ① steady (15s) | 151 | 0 | 0 | **0** | 0% | 기준 정상성 ✅ (캐시픽스로 401 소멸) |
| **② Before** (단일 `docker restart`)¹ | 900 | 543 | — | — | **60.3%** | 구식 재배포 다운타임 ✅ 실측 |
| ③ After · 무제한 · **R1**(컷오버 창끝) | 1800 | **0** | **0** | 0 | 0% | 클린이나 **마진 얇음**(운) |
| **④ After · 무제한 · R2**(넉넉마진) | 1162² | **0** | **133** | 0 | 11.4% | ⚠️ **지연 타임아웃**(용량) |
| **⑤ After · `CPU_LIMIT=0.5` · R3** | 1189² | **0** | **0** | 0 | **0%** | ✅ **완전 무중단** |

¹ Before 는 1차 런(05:35) 실측 재인용. ² dropped_iterations: R2=39·R3=11(느린응답에 VU 묶임).

**지연 분포 (컷오버 창 포함):**

| | median | avg | p95 | max |
|---|---|---|---|---|
| ④ 무제한 R2 | 76ms | 1662ms | **9984ms** (≈타임아웃) | 9997ms |
| ⑤ CPU캡 R3 | 64ms | 428ms | **3108ms** | 7427ms |

- **502·연결드롭은 전 런 0** = nginx 원자적 upstream 전환(blue 서비스 유지 → green 헬시 → fragment flip + `nginx -s reload` → 구버전 `docker stop -t 30` 드레인)은 **전송 레이어에서 무중단**. R2 의 133 실패는 502 가 아니라 **status 0(10s 클라 타임아웃)** — 요청이 드롭된 게 아니라 서빙 JVM 이 CPU 를 굶겨 지연이 타임아웃까지 밀린 것.
- **R1=0% 는 운**: 컷오버가 부하창 끝(reload 09:08:57, 창 종료 ~09:09:0X)에 얹혀 경합 구간에 걸린 요청이 적었다. 넉넉마진 R2 가 진실(11.4%)을 드러냄.
- **R3 이 완화 확증**: 부팅 컨테이너를 반코어로 캡하니 서빙 JVM 이 안 굶겨져 `transport_err 133→0`, p95 `9984→3108ms`. 트레이드오프 = 부팅 ~2배 느림(헬스 5/30→12/30, ~25s→~60s).

## 3차 재검증 (2026-07-06, backlog ①② — 코드완료분의 라이브 확증)

2차에서 남긴 backlog 두 항목(①워크플로 `CPU_LIMIT=0.5` 배선 커밋 `adc1d12`, ②데이터경로 smoke 게이트 커밋 `dfc22ea`)을 실배포로 e2e 검증. 재-apply(18)→재시드(647·1631·3446 유실0)→**커밋 push**(원격 feat ref 가 stale 하면 디스패치가 구 워크플로를 돌려 ① 미반영 — 이번에 발견·해소)→SSM 배포×2→destroy(state+AWS 이중검증 0). 이미지 `mmt2024/mmt-backend:fe8064d…`.

| 검증 | 방법 | 결과 |
|---|---|---|
| **① CPU_LIMIT 배선 (워크플로→--cpus)** | deploy job env `CPU_LIMIT=0.5` → SSM → `docker run --cpus=0.5` | green `NanoCpus=500000000`(=0.5코어) ✅ e2e |
| **① 컷오버 유실 (CPU캡)** | k6 RATE=10·200s, blue→green 워크플로 컷오버 | `status_502=0`·`transport_err=0`·`401=0`, http_req_failed 0%, p95 312ms, threshold PASS ✅ |
| **② 데이터 smoke GREEN** | 정상배포 시 `/nodes/7925` non-empty 폴 | `데이터 smoke OK` → 컷오버 진행 ✅ |
| **② 데이터 smoke RED→abort** | 결함주입 `SMOKE_PATH=/nodes/999999`(401) | smoke RED → **"신버전 폐기, 구버전 유지"·exit 1**, active fragment=green 불변·blue 폐기·nginx 무영향 ✅ |
| 데이터경로 정합 | cache-hit vs 캐시키 DEL 후 cache-miss(CTE) | 응답 **바이트 동일**(30026·conceptId 36) ✅ |

- dropped_iterations 27/2000 = green 부팅(0.5코어) 지연 스파이크(max 7367ms) 때 k6 VU 포화 = **부하기 한계, 서버측 유실 아님**(1973 전부 200).
- **결론: 2차의 3단 규명이 실배포 파이프라인에서 재현됨.** ①은 이제 노브만이 아니라 **워크플로가 실제로 캡을 적용**하고, ②는 데이터 결함을 컷오버 전에 **abort 로 차단**(구버전 무영향).

## 결론 / 클레임

1. **전송/프록시 레이어 무중단 = 증명됨.** SSM→runuser→blue-green 컷오버가 라이브에서 완주, **502·연결드롭 0(전 런).** 구식 in-place 재배포는 같은 부하에서 **60.3% 하드 유실(502)** → 무중단 방식의 이득 명확.
2. **단, t3.micro(1 vCPU)에서 신규 JVM 부팅이 코어를 독점(148%)해 서빙 JVM 을 굶긴다.** 지속부하 시 요청 지연이 클라 타임아웃(10s)을 넘겨 최대 **11.4% 타임아웃**(전송갭 아님, **용량 아티팩트**).
3. **완화 확증: 부팅 컨테이너 CPU 캡(`CPU_LIMIT=0.5`)으로 완전 무중단 0% 달성.** 원인(부팅 CPU 경합)과 해결을 측정으로 결정적으로 증명. 노브는 `switch-backend.sh` 에 옵트인(기본 무제한)으로 커밋(`12f6931`).

**헤드라인:** *"단일 EC2 nginx blue-green 으로 무중단 배포는 성립한다. 전송 레이어는 조건 없이 무결(502=0), 완전한 0% 유실은 t3.micro 에서 부팅 컨테이너 CPU 캡을 전제로 한다."*

## 후속 (backlog / docs)

- **[✅ 완료]** 배포 워크플로 `CPU_LIMIT=0.5` 기본 배선 — 커밋 `adc1d12`, **3차(2026-07-06) 라이브 e2e 확증**(green `--cpus=0.5`·컷오버 0%).
- **[대안]** t3.small(2 vCPU) 승격 시 CPU 캡 없이 경합 소멸 — 비용 vs 배포속도 트레이드오프. **값 튜닝(0.6/0.75) 재측정 여지**(3차는 0.5 1회).
- **[✅ 완료]** `api/CLAUDE.md` 의 `Optional<MysqlConceptRepository> 스텁` 서술 stale 정정 — 커밋 `2801c2c`.
- **[✅ 완료]** `/health` smoke 데이터경로 미검증 → smoke grader 를 대표 데이터 엔드포인트로 강화(spec-02 G4) — 커밋 `dfc22ea`, **3차 라이브 확증**(green 통과·결함주입 abort).
