# Milestone 4: 배포 무중단화 (Zero-Downtime Deployment)

> **상태: ✅ 완료(2026-07-06) — PR [#45](https://github.com/data-sy/my-math-teacher/pull/45) 머지, main `4706398`.** 라이브 실측: in-place 재배포 60.3% 유실 → blue-green + `CPU_LIMIT=0.5` 0% 유실. 결과 정본 = `docs/benchmark/milestone-4-run-report.md`. 아래 설계·핸드오프 서술은 착수 당시 기준(이력 보존).

**브랜치 정책:** spec 단위 분할(M2·M3 와 동일). 단, 현재 진행 브랜치 `feat/m4-spec-01-zero-downtime-deployment` 는
설계 3건(spec-01/02/03)과 기반 코드 조각(A·A.5·R1·B)을 함께 담고 있다 — 첫 기반 작업이 spec-01 에서 출발해
한 브랜치로 묶였기 때문. 후속 구현(switch-backend.sh, 워크플로, 프로비저닝)을 별 PR 로 쪼갤지는 착수 시 결정.

**예상 소요:** 설계 ✅ 완료 · 기반 코드 일부 ✅ · 구현(스크립트·워크플로) 1~2일 · 프로비저닝(사람 핸드오프 후) 0.5~1일 · 검증 0.5일
**의존성:** M2(Neo4j → MySQL CTE 검증 완료) — CTE-only 로 실서버를 구성하는 전제. M1(성능 측정 관습) — "유실률 N%→0%" 측정의 기반.
**위험 수준:** 중간 — 인프라/배포 레이어 변경 + 신규 AWS 프로비저닝. 단 앱 로직 변경은 거의 없고(헬스 엔드포인트·graceful 설정뿐), blue-green 구조 자체가 즉시 롤백 안전망.
**선행 조건:** 비가역 사람 핸드오프(아래 §사람 핸드오프 G1·G2). **G1~G3 전부 완료(2026-07-03 G1 계정·MFA·IAM → G2 시크릿·OIDC → G3 `apply`/SSM 배포/유실률 측정/destroy). 라이브 검증 종료·PR #45 머지.**

---

## 목표

단일 EC2 위에서 Spring Boot 백엔드를 재배포할 때 발생하는 다운타임(502/요청 유실)을 제거한다.
쿠버네티스·ECS·ALB 같은 큰 전환 없이, **기존 프론트 nginx 를 전환 지점으로 재사용한 blue-green**
(신버전 health 통과 → nginx graceful reload 로 원자적 전환 → 구버전 드레인)으로 "어느 순간에도
트래픽 받는 정상 백엔드 최소 1개"를 보장한다. EC2 가 아직 없으므로 **AWS 프리티어(t3.micro 1 GiB)
프로비저닝**도 본 마일스톤에 포함하며, 1 GiB RAM 에 blue-green(전환 구간 JVM 2개 공존)을 맞추는 것이
프로비저닝 설계의 핵심이다.

대상은 **백엔드 컨테이너 한정**. 프론트(정적 SPA) 무중단, DB 무중단 스키마 마이그레이션, 자동 롤백,
HTTPS/인증서는 의도적으로 범위 밖(§비범위).

---

## 왜 새 마일스톤인가

- **M2/M3 와 관심사가 다름.** M2·M3 는 그래프 탐색의 저장소(Neo4j→MySQL CTE) 마이그레이션이고,
  M4 는 그 결과를 **실서버에 무중단으로 올리는 배포 메커니즘** 문제다.
- **M3 와의 관계 = M4 → M3 (역방향·비차단).** M4 가 단일 인스턴스를 처음 띄울 때 "MySQL/CTE-only 가
  실서버 환경에서 정상 동작하는지"를 검증하게 되고(§9 프로비저닝·§검증), 이 결과가 **M3 의 Neo4j 폐기
  go/no-go 근거**가 된다. 즉 M3 의 코드·인프라 삭제가 M4 의 선행조건이 아니라, M4 의 bring-up 이 M3 에
  입력을 준다. Neo4j 코드·`neo4j-deprecated/`·compose 의 `mmt-neo4j` 실제 삭제는 그대로 M3 잔여 작업.

---

## 구성 spec

세 spec 으로 구성된다. spec-01 은 "무중단을 *어떻게 만드나*"(굳는 설계 문서), spec-02 는 "그걸 *누가
실행·검증하나*"(살아있는 운영 문서), spec-03 은 "프로비저닝을 *IaC 로 어떻게 학습하나*"(선택 경로).

### spec-01: 무중단 배포 기반 구축 — [`specs/m4/spec-01-zero-downtime-deployment.md`](../specs/m4/spec-01-zero-downtime-deployment.md)

- 현행 다운타임 원인 5가지 분석(겹침 0 · 이미지 태그 고정 · nginx upstream 하드코딩 · container_name 고정 · 헬스 신호 부재)
- 타깃 아키텍처: 프론트 nginx 재사용 + 백엔드 blue/green + `nginx -s reload` 원자적 전환 + graceful drain
- 변경 파일: 헬스 컨트롤러 · nginx upstream fragment · `switch-backend.sh` · immutable 태그 워크플로
- **§4 검증**: "유실률 N%→0%" 측정 설계(대표 GET 엔드포인트 · k6 `http_req_failed==0` · Before/After 대조군)
- **§7 결정 확정**(사용자 위임): D1 blue-green · D2 커스텀 헬스 · D3 프론트 nginx 재사용 · D4 백엔드만 스크립트 `docker run`
- **§9 EC2 프로비저닝**(프리티어): RAM 예산 · 상태저장소 배치(MySQL=RDS, Neo4j 미구동, Redis 로컬) · 셋업 순서
- **§10 리스크 레지스터** R1~R8

### spec-02: 하네스 핸드오프 게이트 — [`specs/m4/spec-02-harness-handoff-gates.md`](../specs/m4/spec-02-harness-handoff-gates.md)

- 🔄 **살아있는 문서** — 마일스톤마다 사람 개입 카운트(§6.1 원장)를 추적
- 정렬축 = **가역성**: 비가역(G1 정체성/결제/소유 · G2 시크릿 주입)만 사람 전용, 나머지는 게이트 강등 가능
- 검수자 2층(동치성 오라클 = CI · deploy smoke grader = non-empty 기대 shape) — 사람을 per-deploy 판정에서 뺌
- pause/resume 게이트 G1~G6, permission 티어 T0~T2, **per-deploy 사람 개입 0** 이 성숙도 목표

### spec-03: Terraform plan-only IaC 샌드박스 — [`specs/m4/spec-03-terraform-plan-only-iac-sandbox.md`](../specs/m4/spec-03-terraform-plan-only-iac-sandbox.md)

- 성격 = **학습(chosen)** 경로. spec-01 §9 프로비저닝을 IaC 로 표현해 "Terraform 이 뭔지" 경험
- **무과금의 진짜 선 = `apply` 를 안 누르는 것.** 계정 없는 현 시점은 **LocalStack** 으로 무계정 진입
- Phase A(LocalStack, 무계정·무과금) → B(계정 후 real `plan`) → C(`apply` = G1 사람 게이트, spec-01 §9 실행)
- 승격 출처 백로그 `terraform-iac-for-m4-provisioning` = **2026-08-06 흡수·폐기**(✅ M4 에서 소진: 최종 apply 18 리소스 → destroy 후 잔여 0, state+AWS 이중검증). 배경·경계는 위 3줄과 [spec-03](../specs/m4/spec-03-terraform-plan-only-iac-sandbox.md) 머리말이 승계

---

## 진행 현황 (2026-07-03)

| 조각 | 내용 | 상태 |
|---|---|---|
| 설계 3건 | spec-01(배포) · spec-02(게이트) · spec-03(terraform) | ✅ 작성·커밋 |
| **A** | `GET /api/v1/health` 200 (의존성 검사 없음, 전환 게이트용) | ✅ 커밋 `025daf1` |
| **A.5** | `server.shutdown=graceful` + `timeout-per-shutdown-phase: 30s` (R5 드레인) | ✅ 커밋 `5da8d9b` |
| **R1** | Neo4j 부재 + CTE flag ON + 더미 `GDB_*` 로 풀 컨텍스트 기동 로컬 증명 | ✅ 커밋 `1cc6efd` |
| **B** | nginx blue-green 전환 구조 + spec-01 §3.2 fragment 경로 정정(`nginx -t` 통과) | ✅ 커밋 `3491a55` |
| **C** | `switch-backend.sh` blue-green 로직(`--network` join · 헬스 폴 재시도 · `docker stop -t ≥30s`) | ✅ 커밋 `ce3ccc7`(동작 검증은 배포 때) |
| **D** | 워크플로 `${github.sha}` immutable 태그 + deploy job → 스크립트 호출 | ✅ 커밋 `abd08af`(동작 검증은 배포 때) |
| **§9 프로비저닝** | EC2 / RDS / EIP / SG + 더미 `GDB_*` + RDS 시드 | 🟢 G1 완료·Phase B `plan` 성공(12 리소스), `apply`(G3) 대기 |
| **Terraform Phase A/B** | LocalStack plan-only(A) → real AWS `plan`(B) | ✅ A 4슬라이스 완주 · **B `plan` 성공(2026-07-03, 12 리소스)**: provider 실 AWS 전환·키페어·RDS 3306 SG(커밋 `dec0139`→`f4e5c56`), 런북 `infra/terraform/README.md` |

> 재개 메모는 `docs/specs/m4/m4-worklog.md` 에 있다(Carry-forward 결정 포함: R1 종결 · 더미 GDB 면 기동 → 폴백 불필요 · **AI 트레일러 제거(2026-07-03 정책 override)** · Terraform D1~D3 잠금).

---

## 사람 핸드오프 — AWS 체크리스트 (지금 막힌 지점)

자율 진행이 여기서 멈춘 이유. spec-02 가 "비가역(G1·G2)만 사람 전용"으로 선을 그어놨고, 그 비가역
작업이 곧 AWS 셋업이다. **사람이 손으로 수행하는 작업이라 별도 👤 사람용 spec 으로 분리**했다(마일스톤
안에 묻으면 찾기 어려움):

> 👉 **[spec-04: 사람이 직접 수행하는 AWS 프로비저닝 핸드오프](../specs/m4/spec-04-human-aws-provisioning-handoff.md)**
> — G1(정체성/결제/소유)·G2(시크릿 주입)·G3(infra apply) 체크리스트. 사람이 다시 진입할 때 펴 보는 작업 목록.

---

## 롤백 안전망

| 단계 | 롤백 방법 | 소요 |
|---|---|---|
| 전환 **전** 신버전 헬스 실패 | fragment 미변경, 구버전 그대로 서비스(영향 0 = abort no-op) | 즉시 |
| 전환 **후** 신버전 이상 | fragment 를 직전 색으로 되돌리고 `nginx -s reload`(수동 flip-back) | 1분 |
| 워크플로 차원 | 직전 git sha 태그로 재배포 | ~10분 |
| nginx.conf/compose 변경 자체 | 본 브랜치 revert(설정 baked 이미지 재빌드) | ~30분 |
| Terraform plan-only | 리소스 0 → 파일 삭제/`git revert`, LocalStack 컨테이너 폐기 | 즉시 |

> flip-back 트리거 판정은 **사람 직감이 아니라 G4 유실 grader**(spec-02 §5.1) — 거짓 양성(멀쩡한 배포 되돌림) 차단.

---

## 완료 기준

- [x] spec-01/02/03 설계 작성·커밋
- [x] 헬스 엔드포인트(A) · graceful shutdown(A.5) · 기동 무결성 증명(R1) · nginx 전환 구조(B)
- [x] `switch-backend.sh`(C) — 커밋 `ce3ccc7`(blue-green 전환 로직; 동작 검증은 배포 때)
- [x] 워크플로(D) — 커밋 `abd08af`(`${github.sha}` immutable 태그 + 전환 스크립트 호출)
- [x] AWS 프로비저닝(§사람 핸드오프 G1~G3) 완료 — G1·G2·G3 전부(apply 18 리소스 → 측정 → destroy, 2026-07-05·07-06)
- [x] **§검증: 부하 도중 배포 → `http_req_failed==0`(유실률 0%)** — blue-green + `CPU_LIMIT=0.5` 로 502=0·transport_err=0 확증(in-place 60.3% 대조). 정본 = `docs/benchmark/milestone-4-run-report.md`
- [x] 배포 전략(blue-green) ADR 작성 — [ADR 0007](../adr/0007-blue-green-zero-downtime-deployment.md) (+ 배포 채널 [ADR 0008](../adr/0008-m4-ci-deploy-channel-ssh-to-ssm-run-command.md))
- [x] spec-02 §6.1 개입 원장 M4 행 채움 — 커밋 `e32963e`
- [x] roadmap.md 에 M4 완료 표시 — Done 섹션 이동(2026-07-06)

### 검증 (완료 기준의 핵심)

부하 도구(hey/k6)로 배포 **도중** 트래픽을 계속 쏘며 non-2xx 를 카운트한다. **타깃은 `permitAll` +
DB 왕복이 있는 대표 GET**(`/api/v1/concepts/**` 등) — sub-ms `/health` 단독 측정은 드레인 경계의 502 를
못 드러내는 거짓 확신(R5). **Before**(단일 백엔드 stop/rm→run) vs **After**(blue-green), 스토리지 고정.
프로토콜 = HTTP(80). 성공 = `http_req_failed: rate==0`.

---

## 비범위 (의도적으로 이번에 안 하는 것)

- readiness/liveness 프로브 철학, Actuator 기반 정교한 헬스(헬스는 200 ping 수준만)
- **자동 롤백** 오케스트레이션(수동 flip-back 만)
- 의존성(DB/Redis) 게이팅, 쿠버네티스/ECS/ALB/오토스케일링
- 프론트(정적 SPA) 무중단 배포, 무중단 DB 스키마 마이그레이션
- HTTPS/인증서·도메인 전환(R2 후속), 관측성·알림 연동
- Neo4j 코드·인프라 실제 삭제 → **M3**
- Terraform real `apply`(과금) → spec-03 Phase C(= spec-01 §9 + spec-02 G3, G1 준비 후)

---

## 참조

- spec-01/02/03: 위 구성 spec
- M2: Neo4j → MySQL CTE 마이그레이션(CTE-only 실서버 구성의 전제)
- M3: 그래프 인프라 폐기(M4 → M3, M4 bring-up 이 폐기 go/no-go 입력)
- M1: 성능 측정 관습(유실률 측정 기반)
- ~~backlog: `terraform-iac-for-m4-provisioning.md`~~ — 2026-08-06 흡수·폐기(위 §Terraform 로 이관)
- ADR(예정): 배포 전략(blue-green) — 착수 시점 디스크 다음 빈 번호
