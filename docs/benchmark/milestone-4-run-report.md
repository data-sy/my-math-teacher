# Milestone 4 — 배포/무중단 측정 런 리포트

> 큐레이션 정본. raw 로그는 `infra/terraform/run-logs/<UTC>/`(gitignored)에서 이 문서로 옮겨 적는다.
> 텔레메트리 수집: `infra/terraform/run-log.sh` (init/mark/tf-apply/ec2/cost). 헤드라인 목표 = spec-01 §4 유실률 0%.

## 런 메타

| 항목 | 값 |
|---|---|
| 런 UTC | _(run-log init 의 run_start_utc)_ |
| git sha / branch | _(meta.txt)_ |
| 리전 | ap-northeast-2 |
| 비용 모델 | 신규 크레딧(유료 플랜, $200 선차감) |

## 1. 시간 (timeline.tsv)

| phase | UTC | 소요 |
|---|---|---|
| apply-start → apply-end | | _(tf-apply.time)_ |
| RDS available | | ~10분 예상 |
| cloud-init 부팅 완료 | | _(systemd-analyze / cloud-init-output.log)_ |
| seed | | |
| 첫 배포(blue 기동) | | |
| 컷오버(blue→green) | | |
| destroy | | _(tf-destroy.time)_ |

## 2. 비용 (추정 → 실제 대조)

- **추정**(`run-log.sh cost`, 서울 요율 근사): 인스턴스-시간 $____ (구간 ____h)
- **실제**(다음날 Cost Explorer / Billing): $____ ← 24h+ 지연, 반드시 사후 대조
- 크레딧 잔액: 런 전 $____ → 런 후 $____

## 3. 용량 / 스토리지

| 대상 | 설정 | 실사용 |
|---|---|---|
| EBS 루트 (gp3) | 30GB | _(df -h)_ |
| RDS 스토리지 | 20GB | _(CloudWatch FreeStorageSpace)_ |
| Docker 이미지/볼륨 | — | _(docker system df)_ |

## 4. 메모리 ★ (§9.4 핵심 — 1GiB + 2GB swap)

> 컷오버 순간 blue+green JVM 2개 공존이 이 마일스톤의 스트레스 지점. `run-log.sh ec2 <EIP> cutover` 스냅샷 대조.

| 시점 | used/free (MB) | swap used | 컨테이너별(docker stats) |
|---|---|---|---|
| steady (blue만) | | | |
| **cutover (blue+green)** | | | |
| post (green만) | | | |

## 5. 부하 / 유실률 (헤드라인 — spec-01 §4)

| 구성 | req 총계 | http_req_failed | p99 | 502 버스트 |
|---|---|---|---|---|
| **Before** (단일 재기동) | | (기준선 — 유실 발생 기대) | | |
| **After** (blue-green) | | **0% 목표** | | 0 기대 |

- 부하도구/스크립트: _(hey/k6, 대표 GET 엔드포인트)_
- 판정: G4 유실 grader 가 컷오버 윈도우 가로질러 green → G5 컷오버 GO

## 결론 / 클레임

_(Before 대비 After 유실률, 그리고 그것이 측정 설계로 실제 증명됐는지 — 내부 모순 점검 포함)_
