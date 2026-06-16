# Spec 04: 👤 사람이 직접 수행하는 AWS 프로비저닝 핸드오프

> **⚠️ 이 spec 은 Claude Code(하네스)가 실행하는 spec 이 아니다.**
> spec-01/02/03 은 어시스턴트가 코드·설정을 작성하는 실행 지시지만, **이 문서는 *사람*이 손으로
> 수행해야 하는 비가역 작업을 모은 체크리스트**다. 자율 진행이 *여기서 멈추는* 이유이자, 사람이
> 다시 진입할 때 펴 보는 작업 목록이다.

**상위 마일스톤:** Milestone 4 (배포 무중단화)
**성격:** 👤 **인간 실행 스펙(human-run)** — 가역성상 사람만 가능한 비가역 작업(spec-02 §2 정렬축)
**근거 spec:** [spec-01 §9](spec-01-zero-downtime-deployment.md)(리소스 정의), [spec-02 §5](spec-02-harness-handoff-gates.md)(게이트 G1~G3)
**상태:** 🔴 미착수 — M4 자율 진행이 이 핸드오프 대기에서 멈춰 있음

---

## 0. 이 문서를 언제 펴는가

전체 M4 작업 순서에서 사람이 끼는 단 한 구간이다:

```
①  테라폼·무중단 학습자료 읽기 (개념 적재)
         │
②  ▶ 이 문서(spec-04) 읽으며 손으로 AWS 셋업 ◀   ← 지금 여기서 막혀 있음
         │
③  클코용 spec(01 C·D / 03 Phase B·C) 이어서 진행 → 검증
```

→ 사람이 ②를 끝내 G1·G2 를 풀어주면, 어시스턴트가 ③(switch-backend.sh·워크플로·real plan/apply·
유실률 검증)을 이어받는다.

---

## 1. 왜 사람만 가능한가 (가역성 정렬)

spec-02 는 "정체성/결제/시크릿이라서"가 아니라 **되돌릴 수 있는가**로 사람/하네스를 가른다.
아래 G1·G2 는 **비가역**이라 줄일 수 없는 사람 몫이고, G3 는 가역이라 원칙상 하네스로 강등 가능하나
**G1(과금) 뒤에야 `apply` 가 가능**해서 순서상 사람 핸드오프에 묶인다.

| 게이트 | 가역성 | 왜 사람인가 |
|---|---|---|
| **G1** 정체성/결제/소유 | 비가역 | AWS 계정·결제수단·도메인·DockerHub 소유는 되돌릴 수 없는 신원 행위 |
| **G2** 시크릿 값 최초 발급·주입 | 비가역 | 시크릿 read 는 T0(영구 차단). 하네스는 `gh secret set` *명령 템플릿*만 주고 값은 안 넣음 |
| **G3** infra apply | 가역(이나 G1 의존) | `apply` 트리거가 곧 과금(G1) → G1 풀리기 전엔 LocalStack plan-only 까지만 |

---

## 2. 체크리스트

> ☐=사람만 가능(비가역) · ◑=사람 또는 하네스(가역, 권한 부여 시). 시크릿은 **커밋 절대 금지**(루트 CLAUDE.md).

### G1 — 정체성/결제/소유 (비가역, 1회성)

- [ ] ☐ **AWS 계정 생성 + 결제수단(카드) 등록**
  - 가입 자체가 카드를 요구한다 → 계정 생성 = G1 의 절반. **이걸 미루고 싶으면 Terraform Phase A(LocalStack, spec-03 §2)를 먼저** 무계정으로 경험 가능.
- [ ] ☐ **DockerHub 계정·토큰 확보** (현행 레지스트리 유지, ECR 전환은 범위 밖)
- [ ] ☐ *(HTTPS 추진 시에만)* 도메인 소유 + OAuth 앱 등록 — R2 로 후속 분리. **무중단 검증 자체엔 불필요**(HTTP 80 으로 증명)

### G2 — 시크릿 값 최초 발급·주입 (비가역, 1회성)

- [ ] ☐ **GH Secrets 설정**: `EC2_HOST`(=EIP) · SSH private key · `DOCKERHUB_*`
  - 어시스턴트가 `gh secret set …` 명령 템플릿을 줄 수 있으나 **값 주입은 사람**. 재개 검증은 `gh secret list`(값 미열람).
- [ ] ☐ **비커밋 `docker-compose.yml` env 작성**: `RDS_HOST`·RDS 자격 · 더미 `GDB_*`(`localhost`/`7687`/`neo4j`/`dummy`) · `mmt.migration.use-mysql-cte-for-graph=true` · 백엔드 `mem_limit`(spec-01 §9.4)
  - 더미 `GDB_*` 면 Neo4j 없이도 기동됨(R1 종결). 시크릿은 이 비커밋 파일/환경변수로만.

### G3 — infra apply (가역, G1 후)

- [ ] ◑ **RDS(MySQL) 프리티어** db.t3.micro Single-AZ 생성 + **M2 스키마·인덱스·시드 적재**
  - ⚠️ 인덱스·시드 누락 시 CTE 가 느려지거나 결과 비정상(R4). 적재가 EC2 띄우기보다 먼저.
- [ ] ◑ **EC2 t3.micro**(AL2023, gp3 30GB) + **Elastic IP** + **SG**(80/443 공개 · 22 내 IP만 · **8080 비공개**) + 키페어
- [ ] ◑ **EC2 초기화**: 2GB 스왑 + Docker/compose 설치
- [ ] ◑ `deploy/active-backend.conf` + `deploy/switch-backend.sh` 배치
- [ ] ◑ **최초 1회 수동 배포**(`workflow_dispatch`) → 유실률 검증(spec-01 §4)

> ◑ 항목은 Terraform(spec-03 Phase C = `apply`)으로 코드화 가능하나 `apply` 트리거 = 과금(G1) 뒤.
> 그 전까지 G3 는 **LocalStack plan-only(spec-03 Phase A)까지만** 진행된다.

---

## 3. 끝낸 뒤 — 어시스턴트로 다시 넘기기

G1·G2 가 풀리면 아래가 자율로 진행 가능해진다(클코용 spec):

- spec-01 **C** `switch-backend.sh` · **D** immutable 태그 워크플로
- spec-03 **Phase B/C** real `plan`/`apply`(G3)
- spec-01 **§4** 유실률 0% 검증
- spec-02 **§6.1 개입 원장** M4 행 기록(이번 핸드오프에서 실제로 든 사람 개입 횟수 = risk-forced 기준선)

---

## 4. 참조

- spec-01 §9 — 리소스 사양·셋업 순서·RAM 예산·비용 주의
- spec-02 §5(게이트)·§3(permission 티어 T0~T2)·§6(개입 카운트)
- spec-03 — Terraform plan-only(무계정 진입 = LocalStack)
- 마일스톤: [`milestone-4-zero-downtime-deployment.md`](../../milestones/milestone-4-zero-downtime-deployment.md)
