# RDS MySQL 8.0 표준 지원 종료 — Extended Support 과금

**상태: ✅ 해소 (2026-08-31).** `mmt-db` 를 **8.0.46 → 8.4.11(LTS)** 로 메이저 업그레이드했다.
아래는 착수 전 분석과 **실행 후 실측**을 함께 남긴 것이다 — 추정이 어디서 틀렸는지가 이 문서의 핵심이다.

## 사실관계 (AWS Health, 2026-07-30 수신)

- 계정 `471934607256` / 리전 `ap-northeast-2` / 이벤트 `AWS_RDS_PLANNED_LIFECYCLE_EVENT`
- MySQL 8.0 커뮤니티 EOL 2026-04 → **RDS 표준 지원 종료 2026-07-31**
- **2026-08-01 부터 Extended Support 요금 부과 시작** (실행 중인 8.0 마이너 버전과 무관)
- 미업그레이드 시 AWS 가 유지보수 윈도우에 임의로 업그레이드한다 → 예정에 없던 다운타임

## 비용 — 추정이 맞았다. 크레딧이 그걸 가리고 있었을 뿐

착수 전 이 문서는 `2 vCPU × $0.10/vCPU-hr × 730h ≈ $146/월` 을 가정했다.
**실측 결과 이 추정은 사실상 정확했다** — 다만 콘솔 첫 화면은 그 숫자를 보여주지 않는다.

### 8월 청구 구조 (콘솔 청구서 + Cost Explorer, 2026-08-31 확인)

| 항목 | 금액 |
|---|---|
| Usage (세전 gross) | **$201.92** |
| 세금 | $8.70 |
| 크레딧 적용 | **−$114.90** |
| **실청구액** | **$95.71** |

### gross 기준 RDS 분해

| usage type | 금액 | 사용량 |
|---|---|---|
| `APN2-ExtendedSupport:Yr1-Yr2:MySQL8.0` | **$146.77** | 1,223.1 vCPU-hour |
| `APN2-InstanceUsage:db.t3.micro` | $15.90 | 611.55 Hrs |
| `APN2-RDS:GP2-Storage` | $2.15 | 16.4 GB-Month |
| `APN2-RDS:ChargedBackupUsage` | $0.007 | 0.0775 GB-Month |
| **RDS 합계(gross)** | **$164.83** | |

- **실단가 = $0.1200/vCPU-hour** (ap-northeast-2, Yr1-Yr2). 가정한 $0.10 과 같은 자릿수다.
- 8월은 인스턴스가 8/5 재런치라 611h(≈25.5일)만 돌았다. **만근 환산 = $175.20/월.**
- **ES 하나가 8월 전체 usage($201.92)의 72.7%** 였다. 인스턴스 자체($15.90)의 **9.2배**다.
- 🔴 **크레딧이 소진됐다.** 발행 $160 중 $45.10 사용·$114.90 잔여였는데, 8월 청구가 잔여 전액을 태워
  **예상 잔여 $0.00** 이 됐다. **9월부터는 gross 가 그대로 카드로 나간다** — 방치했으면
  ES 만으로 월 $175 였다.

> **왜 못 봤나 — 크레딧이 실측을 가렸다.**
> Cost Explorer 기본 `UnblendedCost` 는 크레딧(−$114.90)이 이미 반영된 값이라 ES 가 **$31.87** 로 보인다.
> 예산 두 개가 서로 다른 숫자를 보인 것도 같은 이유다 —
> `My Zero-Spend Budget`(크레딧 포함) = $95.72 vs `My Monthly Cost Budget`(RECORD_TYPE 에서
> Credit·Refund 제외) = **$210.62**. 후자가 실사용량이다.
> **교훈: 크레딧이 있는 계정에서 "얼마 나가나"를 볼 때는 `RECORD_TYPE ≠ Credit` 필터를 걸어야 한다.**
> 안 걸면 크레딧이 떨어지는 순간 청구액이 갑자기 3배가 된다.

## 실행 기록 (2026-08-31)

| # | 단계 | 결과 |
|---|---|---|
| 0 | 상태 확인 | `8.0.46` · `EngineLifecycleSupport = open-source-rds-extended-support` (과금 확정) |
| 1 | `mmtadmin` 인증 플러그인 전환 | `mysql_native_password` → `caching_sha2_password` (비번 동일값 재설정) |
| 2 | 라이브 검증 | `/api/v1/chapters?grade=초1&semester=1학기` 200 + 실데이터 |
| 3 | 수동 스냅샷 | `mmt-pre-84-upgrade-20260831` (8.0.46, 20GB) |
| 4 | 메이저 업그레이드 | `modify-db-instance --engine-version 8.4.11 --allow-major-version-upgrade --apply-immediately` |
| 5 | 터라폼 정합 | `var.db_engine_version` `"8.0"` → `"8.4"` + `apply -refresh-only` |
| 6 | 로컬·CI 정렬 | `docker-compose.yml` · `TestcontainersConfig` · `MySqlOnlyTestcontainersConfig` → `mysql:8.4` |

### 사후 검증 (전부 통과)

| 무엇 | 결과 |
|---|---|
| 버전·계정 | `8.4.11` · `mmtadmin@%` = `caching_sha2_password` |
| 데이터 무결성 | chapters 647 · concepts 1631 · knowledge_space 3446 · users 6 — **기준선과 전부 일치** |
| M7 스키마 생존 | `self_report_answers` · `learning_queues` · `learning_queue_items` · `probabilities` |
| 재귀 CTE | 서버 직접 실행 55 ✅ |
| 그래프 경로(라이브) | `/concepts/2619` · `/prerequisite` · `/nodes`(19KB) · `/edges`(4.9KB) 전부 200 |
| 진단 경로(라이브) | `POST /diagnosis/frontier {"scope":"full","schoolLevel":"초등"}` → 200, 31KB |
| 로컬 볼륨 | 8.0 볼륨을 8.4 로 인플레이스 업그레이드 — 647/1631/3446 무손실 |
| 다운타임 | 약 6분 (10:32:29 `upgrading` → 10:38 대 `available`) |

> **과금 종료는 아직 실증되지 않았다.** 8.4 는 표준 지원 구간이라 ES 요금이 발생하지 않아야 하지만,
> Cost Explorer 는 일 단위 반영이다. **2026-09-01 이후 `APN2-ExtendedSupport` 항목이 0 인지 확인할 것.**

> 곁가지 발견: `/api/v1/concepts/{id}` 에 없는 id(예: 1)를 주면 404 가 아니라 **500** 이 난다.
> 업그레이드와 무관한 기존 동작(에러 처리 누락)이며, 이번 검증 중 오탐을 유발했다.

## 함정 — 무엇이 실재했나

- ✅ **`mysql_native_password` 는 실재했다.** 사전 점검에서 `mmtadmin@%` 가 정확히 그 플러그인이었다.
  8.4 는 이 플러그인을 제거(`plugin_status = DISABLED`)했으므로 **전환 없이 올렸으면 앱 인증이 즉사**했다.
  전환은 8.0 에서 미리 했고(8.0 도 caching_sha2 지원) 비번을 그대로 재설정해 EC2 env·tfvars 수정이 없었다.
- ❌ **JDBC 드라이버 걱정은 기우였다.** `mysql-connector-j 8.0.33`(Spring Boot 3.1.6 관리 버전)으로
  `mysql:8.4.11` 컨테이너에 **앱의 실제 URL 파라미터 그대로** 붙여 검증했다 — 접속·재귀 CTE·`caching_sha2`
  전부 통과. `useSSL`·`allowPublicKeyRetrieval` 를 URL 에 추가할 필요가 없었다
  (드라이버 기본 `sslMode=PREFERRED` 가 TLS 를 협상 → `TLS_AES_256_GCM_SHA384`).
- ❌ **utf8mb3 잔재는 앱과 무관했다.** `mysql` 시스템 스키마에만 있고 앱 테이블 18개는 전부 InnoDB.
- 🔴 **문서에 없던 함정이 하나 더 있었다 — 터라폼 apply 자체가 위험했다.**
  이 문서는 "터라폼이 업그레이드를 되돌리려 든다"만 경고했는데, 실제 `terraform plan` 은
  **EC2 와 EIP 연결을 교체하려 들었다**(`Plan: 2 to add, 0 to change, 2 to destroy`).
  원인은 별건인 [AMI 필터 지뢰](ami-filter-picks-minimal-no-ssm-agent.md) — `most_recent = true` 가
  새로 나온 AL2023 AMI 를 집었다. 그래서 업그레이드를 **터라폼이 아니라 RDS API 로** 실행하고,
  터라폼은 값 갱신 + `apply -refresh-only`(리소스 무변경)로만 맞췄다.
  → **가역성 판단을 "이 리소스" 가 아니라 "이 apply 가 건드리는 전체"로 해야 한다.**
- ✅ `snapshot_identifier = "mmt-mothball-2026-07-31"`(ForceNew)은 끝까지 건드리지 않았다.

## 부수 발견 — 자동 백업이 꺼져 있었다 (✅ 같은 날 해소)

`BackupRetentionPeriod = 0` · `skip_final_snapshot = true` 라 **PITR 이 아예 없었다.**
업그레이드 직전까지 유일한 복원점이 7/31 스냅샷(= M7 DDL 이전, 8월 데이터 없음)이었다.

**비용을 이유로 끄고 있었는데, 실측하니 비용이 없었다:**

- 백업 스토리지 실단가 **$0.095/GB-월** (8월 `ChargedBackupUsage` $0.0074 / 0.0775 GB-Month 역산)
- DB 실사용 **2.52GB** (CloudWatch `FreeStorageSpace` 로 역산, 할당 20GB)
- RDS 는 **할당 스토리지(20GB)까지 백업 저장 무료** → 2.52GB 는 그 안이다.
  8월에 $0.0074 가 찍힌 건 7/31~8/5 **인스턴스가 없던 mothball 기간**이라 무료 한도가 적용 안 됐기 때문.
- 무료 한도를 아예 무시해도 2.52 × $0.095 = **월 $0.24**. ES 가 $146.77 이었던 것과 비교할 값이 아니다.

**적용(2026-08-31):** `backup_retention_period = 7` · 백업 창 `18:00-18:30` UTC(=**03:00 KST**,
구 22:09 KST 는 서비스 활성 시간대였다). 터라폼 `database.tf` 에도 명시했다.
검증: `LatestRestorableTime` 생성 + 자동 백업 `rds:mmt-db-2026-08-31-01-58` available.

> ⚠️ **retention 0 ↔ 비영 전환은 인스턴스를 재시작시킨다**(AWS 명세). 되돌릴 때도 다운타임이 붙는다.
> 7일을 고른 이유: 진짜 가치는 스냅샷이 아니라 PITR 이고, 1인 프로젝트는 "며칠 뒤에야
> 데이터가 이상한 걸 알아채는" 실패 모드가 현실적이라 1~3일은 촘촘하다. 쓰기량 대비 7일을 넘기면 얻는 게 급감한다.

## 남은 결정

- **`EngineLifecycleSupport` 를 `open-source-rds-standard-support` 로 바꿀 것인가.**
  8.4 는 표준 지원 구간이라 **지금은 과금되지 않는다.** 다만 이 값이 `extended-support` 로 남아 있으면
  8.4 가 표준 지원을 끝낼 때 **같은 일이 자동으로 반복**된다. 지금 바꾸면 그때 AWS 가 강제 업그레이드한다.
  급하지 않으나(수년 뒤) **의식적으로 정하지 않으면 또 당한다.**
- **`skip_final_snapshot = true` 를 유지할 것인가** — `terraform destroy` 가 최종 스냅샷 없이
  RDS 를 지운다. 자동 백업(7일)은 인스턴스 삭제 시 함께 사라지므로 **이 플래그는 여전히 데이터 소멸 경로**다.
