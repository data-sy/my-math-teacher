# Spec 03: Terraform plan-only IaC 샌드박스 (무계정·무과금 시작)

**상위 마일스톤:** Milestone 4 (배포 무중단화)
**대상 Phase:** 학습(chosen, spec-02 §2.1) — spec-01 §9 프로비저닝을 IaC 로 옮기는 *선택* 경로
**선행:** [spec-01 §9](spec-01-zero-downtime-deployment.md)(리소스 정의), [spec-02 §5 G3](spec-02-harness-handoff-gates.md)(apply 게이트)
**승격 출처:** 백로그 `terraform-iac-for-m4-provisioning`(2026-08-06 흡수·폐기) — **성격 = 학습(chosen) 경로**. 순수 ROI 로는 "한 번 깔고 안 건드리는 솔로 인프라"라 콘솔 30분이 맞지만, *Terraform 경험* 목적이면 좋은 첫 소재라 채택했다. 배경·경계 요약은 [ROADMAP `[Infra] Terraform`](../../../ROADMAP.md) 항목

> ⚠️ **상태: 설계 제안서(spec) — 구현 미착수.** 본 문서는 plan-only 샌드박스의 *경계*를 못박고
> HCL 구성을 제시한다. 갈리는 지점은 §3(결정 대기)에 트레이드오프+추천을 단다. 구현(HCL 작성·
> `terraform plan` 실행)은 §3 사인오프 이후. 본 spec 작성 턴에서는 코드/설정을 변경하지 않는다.

---

## 0. 목적·범위

- **목적**: Terraform "뭔지 경험"(HCL 문법 · 리소스 그래프 · `init`→`plan`→`apply`→`destroy` 멘탈모델)을
  *실제로 쓸 데 있는* 인프라(spec-01 §9)로 달성. 토이 튜토리얼 대신 §9 의 EC2/RDS/EIP/SG 를 HCL 로 표현.
- **시작점**: **AWS 계정 없음** (사용자 확인). 따라서 "무과금"의 정확한 선을 §1 에서 못박고, 계정 없이도
  도는 경로(§2 LocalStack)로 출발한다.
- **범위**: `validate` → `plan` (→ LocalStack mock `apply`/`destroy`) 까지. **real AWS `apply`(과금·G1)는
  범위 밖** — spec-02 §5 **G3** 의 사람 게이트. 본 spec 은 G3 *앞단*(config + plan)을 채운다.

---

## 1. 무과금의 정확한 경계 (여기 헷갈리면 무과금이 깨짐)

| 명령 | provider API 호출 | 자격 필요 | 과금 |
|---|---|---|---|
| `terraform validate` | 없음 (HCL 문법·일관성만) | 없음 | 0 |
| `terraform plan` — **literal 리소스만** (data source 없음) | 거의 없음 (생성 대상은 아직 미존재) | provider init 에 region+자격(더미+skip 플래그 가능) | 0 |
| `terraform plan` — **data source 사용** (AMI 조회·AZ·기존 VPC lookup) | **있음** (plan 시점 read API) | 실제 read 자격(=spec-02 **T1**) 또는 LocalStack mock | 0 (조회) |
| `terraform apply` | 있음 (생성/변경) | 실제 write 자격 (=G1) | **과금 시작** |

- ✓ **무과금의 진짜 선 = `apply` 를 안 누르는 것.** "계정·자격이 0"이어야 하는 게 아니다. `plan` 은 생성을
  안 하므로(설사 read API 를 때려도) 과금 0.
- 계정이 *없는* 지금은 read 자격조차 없으므로 → **LocalStack 으로 자격·API 자체를 로컬 mock** 해서
  `plan`(과 mock `apply`)까지 무계정으로 돈다(§2).

---

## 2. 시작점별 경로 (계정 없음 채택)

| 시작점 | 경로 | 비고 |
|---|---|---|
| **계정 없음 (← 우리)** | **LocalStack** (로컬에서 AWS API mock) | 가입에 카드가 따라붙는 것을 피해 *진짜 무계정*으로 `validate`/`plan`/mock `apply`/`destroy` 사이클을 로컬에서 경험 |
| 계정 있음(나중) | T1 read 자격으로 real `plan` | 제일 깔끔. data source 도 실제 조회. 과금 0 |

> AWS 가입은 결제수단(카드) 등록을 요구한다 → 계정 생성 자체가 G1 의 절반을 건드린다. plan-only 학습을
> 그 전에 시작하려면 LocalStack 이 무계정 진입점이다.

---

## 3. 결정 대기 (트레이드오프 + 추천 — 사인오프 후 구현)

### D1. Phase A 연습 방식 (핵심)

| | **LocalStack (추천)** | validate + plan-only (LocalStack 없이) |
|---|---|---|
| 경험 범위 | `validate`→`plan`→`apply`→`destroy` **전 사이클** | `validate`+`plan` 만 (apply/destroy 못 봄) |
| 무계정·무과금 | ◎ (로컬 mock) | ◎ (skip 플래그 + 더미 자격) |
| data source 현실성 | mock 응답으로 AMI/AZ 조회 됨 | 회피·하드코딩해야 (조회하면 자격 필요) |
| 셋업 비용 | LocalStack(Docker) 설치 | Terraform 만 |
| 한계 | **Community 의 리소스 커버리지 편차**(특히 RDS) ⚠️ | apply/destroy 체험 못 함 = 학습 목적 절반 미달 |

**추천: LocalStack.** 사용자 목적이 "테라폼이 뭔지 경험"이고, 그 핵심은 `apply`→`destroy` 사이클과
리소스 그래프가 실제로 만들어졌다 사라지는 체감인데, real AWS 에선 그게 G1(과금) 뒤에야 가능하다.
LocalStack 은 그 사이클을 **무계정·무과금**으로 당겨준다. RDS 커버리지 한계(아래 R-T1)는 학습 타깃이
RDS 충실도가 아니라 *사이클·그래프*라 감수 가능 — EC2/EIP/SG(분량의 대부분)는 잘 mock 된다.

> ⚠️ **RDS 는 plan/validate 까지만 신뢰.** LocalStack Community 의 RDS 에뮬레이션은 버전·서비스별
> 편차가 크다. RDS mock `apply` 성공 여부는 *사용 중인 LocalStack 버전 문서로 확인*하고, 안 되면
> RDS 는 `plan` diff 읽기까지만 하고 mock-apply 대상에서 뺀다(EC2/EIP/SG 로 사이클 체험).

### D2. Terraform 코드 위치

**추천: `infra/terraform/`** (신규). IaC 는 개념상 인프라 정의라, 런타임 배포 스크립트(`deploy/`:
nginx fragment·switch-backend.sh)와 관심사가 다르다. (`deploy/terraform/` 도 가능하나 `deploy/` 를
"배포 실행 아티팩트"로 좁게 유지.)

### D3. backend·provider 핀

**추천: local backend(`terraform.tfstate` 로컬 파일) + provider 버전 핀.** remote state/locking 의
payoff 는 솔로·일회성 인프라에선 안 드러남(backlog 문서 §3). `required_providers` 로 `hashicorp/aws`
버전을 핀해 드리프트 방지(R-T3).

---

## 4. HCL 구성 (spec-01 §9 → 코드)

> §3 사인오프 후 작성. 시크릿·실측 엔드포인트는 코드/커밋에 미포함(§7).

```
infra/terraform/
  provider.tf      # aws provider + (Phase A) LocalStack endpoints/skip 플래그
  variables.tf     # region, instance_type, db_*, my_ip, 더미 GDB_* 등
  main.tf          # 리소스 본체
  outputs.tf       # public_ip(EIP), rds_endpoint 등
  terraform.tfvars # 비커밋(gitignore) — 실제 값/시크릿
```

| spec-01 §9 | Terraform 리소스 | 메모 |
|---|---|---|
| EC2 t3.micro, AL2023, gp3 30GB (§9.2) | `aws_instance` + `root_block_device` | AMI 는 `data aws_ami`(Phase A 는 LocalStack canned, B 는 실제 조회) |
| RDS db.t3.micro, MySQL, Single-AZ, 20GB (§9.3) | `aws_db_instance` | 비번 = 변수(시크릿, §7). ⚠️ R-T1 |
| EIP 1개 (§9.2) | `aws_eip` (+ `aws_eip_association`) | 실행 인스턴스 연결 시 무료 |
| SG: 80/443 공개, 22 내 IP, 8080 비공개 (§9.2) | `aws_security_group` + `*_rule` | 22 는 `var.my_ip/32`. 8080 inbound 룰 없음 |
| 리전 ap-northeast-2 (§9.2) | `provider.region` | |
| 더미 `GDB_*` + `use-mysql-cte-for-graph=true` (§3.3·R1) | `aws_instance.user_data`(부트스트랩) 또는 비커밋 compose env | **코드 0 변경**(spec-01 R1) — IaC 는 env 주입만 |

> Phase A provider 예시 골격(LocalStack): `skip_credentials_validation`,
> `skip_requesting_account_id`, `skip_metadata_api_check = true` + `endpoints { ec2/rds/... =
> "http://localhost:4566" }` + 더미 access/secret key. (실 AWS 전환 = 이 블록 제거.)

---

## 5. 단계

| Phase | 내용 | 자격 | 과금 | 게이트 |
|---|---|---|---|---|
| **A (지금, 무계정)** | LocalStack 으로 `validate`→`plan`→(mock)`apply`→`destroy`. HCL 문법·리소스 그래프·diff 읽기 체득 | 없음(로컬 mock) | 0 | — |
| **B (계정 생긴 후)** | 같은 config 를 real AWS 에 `plan`(T1 read). data source 실제 조회, diff 읽기 | T1 read | 0 | — |
| **C (G1 준비 시)** | 같은 config 에 `apply` = 실제 프로비저닝 (= spec-01 §9 실행) | G1 write | **시작** | **spec-02 G3** |

→ A·B 는 본 spec, **C 는 spec-01 §9 + spec-02 G3 으로 넘어간다**(여기 범위 밖).

> 🟢 **Phase A 성공 기준의 의미 고정 (green 오독 금지).** A 의 성공 = "`validate→plan→apply→destroy`
> **사이클을 돌렸다** + **리소스 그래프를 읽었다**"(= 테라폼을 익혔다). **LocalStack 에서 배우는 건
> AWS 가 아니라 *테라폼*이다** — 사이클·그래프·HCL 문법. 따라서 **LocalStack green ≠ "이 설정이 real
> AWS 에서 뜬다".** "이 RDS 설정이 진짜 AWS 에서 정상 기동하나"는 LocalStack 이 답 못 하며, 그건
> **Phase B(real `plan`)의 몫**이다. 특히 mock-`apply` green 을 *인프라 정합* 신호로 오독하지 말 것 —
> RDS 는 plan/validate 까지만 신뢰(R-T1). (검수자 green 의 의미를 명제 단위로 고정하는 spec-02 §4
> 패턴의 terraform 판.)

---

## 6. 하네스 분담 (spec-02 G3 정합)

- **config 작성** = 하네스(이 챗에서 HCL 초안 제공 가능).
- **`validate`/`plan` 실행** = 가역·무과금이라 하네스가 돌려도 됨(LocalStack/T1). spec-02 G3 의 "가역
  → 등급 강등 OK"에 해당.
- **`apply`(C)** = G1 사람 게이트(비가역·과금).
- ✓ 결과: plan-only 단계는 **사람 개입 0 에 가까운 첫 실전 G3 사례**. spec-02 §6.1 원장 첫 줄이
  *apply 전 setup 개입 없이* "G3 plan(LocalStack/T1), 사람 개입 0"으로 찍힌다.

> 이 챗 환경 한계: 어시스턴트는 로컬 셸에서 `terraform` 을 직접 못 돌린다. 실제 실행·자격 설정은
> 사용자(또는 CC 하네스), 어시스턴트는 HCL 초안 + `plan` diff 함께 읽기.

---

## 7. 시크릿·state 위생 (★ 커밋 1번째 — terraform 파일을 *하나라도* 만들기 전)

- **순서가 핵심.** `.gitignore` 보강이 spec-03 의 **어떤 terraform 파일보다 먼저** 나가야 한다 —
  단독 커밋으로 spec-03 *앞*에, 최소한 동일 커밋. 지금은 LocalStack 더미값이지만 **Phase B/C 에서
  real `plan`/`apply` 하는 순간 tfstate 에 RDS 비번·실 리소스 ID 가 평문으로 박힌다.** 한 번 커밋되면
  `rm` 해도 **git 히스토리에 영구히 남아** 사후 제거가 사실상 불가 → 선제 차단만이 답.
- **`.gitignore` 보강 항목**(현재 terraform 항목 없음): `*.tfstate`·`*.tfstate.*`·`.terraform/`·
  `*.tfvars`(시크릿 들어감, `!*.tfvars.example` 만 예외) 무시 + **`.terraform.lock.hcl` 은 커밋**
  (provider 버전 잠금 공유, D3).
- ⚠️ RDS 비밀번호는 변수 + 비커밋 `terraform.tfvars`/환경변수(`TF_VAR_*`)로만. 이건 spec-02 **G2**
  ("시크릿 커밋 금지")의 **terraform 판**이다. 루트 CLAUDE.md 금지사항과 정합.
  - ✅ 본 항목은 이미 선반영: `.gitignore` 에 terraform 무시 블록 추가됨(spec-03 커밋과 함께/앞서 커밋).

---

## 8. 리스크 레지스터 (analyze-lite — ✓ 사실 / ⚠️ 가정)

- **R-T1 ⚠️ LocalStack RDS 커버리지.** Community 의 RDS mock 은 편차 큼 → RDS 는 plan/validate 까지만
  신뢰, mock-apply 는 버전 문서 확인(§3 D1).
- **R-T2 ✓ plan 시 data source read.** `data aws_ami` 등은 plan 시점에 자격을 요구 → 무계정 단계에선
  LocalStack endpoint 로 우회(§4). 자격 없이 real provider 로 data source plan 시 인증 에러.
- **R-T3 ⚠️ provider 버전 드리프트.** `required_providers` 핀 누락 시 plan 결과가 환경마다 다름 →
  버전 핀 + `.terraform.lock.hcl` 커밋(§7).
- **R-T4 ✓ state 자격 누수.** §7 위생 미준수 시 시크릿 유출.
- **R-T5 ⚠️ free tier 만료(C 단계).** apply 후 `destroy` 규율 — IaC 는 spin-up 마찰 0 이라 "켜고 잊기"
  쉬움(backlog §3-2). C 단계 진입 시 destroy/비용 체크 필수.

---

## 9. 롤백

- **A·B(plan-only)**: 리소스 0 → 롤백 자명(파일 삭제/`git revert`). LocalStack mock 은 `tflocal destroy`
  또는 컨테이너 폐기.
- **C(apply)**: spec-01 §4.4·§9.7 + `terraform destroy`. (본 spec 범위 밖.)
