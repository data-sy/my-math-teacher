# Spec 04: 👤 사람이 직접 수행하는 AWS 프로비저닝 핸드오프

> **⚠️ 이 spec 은 Claude Code(하네스)가 실행하는 spec 이 아니다.**
> spec-01/02/03 은 어시스턴트가 코드·설정을 작성하는 실행 지시지만, **이 문서는 *사람*이 손으로
> 수행해야 하는 비가역 작업을 모은 체크리스트**다. 자율 진행이 *여기서 멈추는* 이유이자, 사람이
> 다시 진입할 때 펴 보는 작업 목록이다.

**상위 마일스톤:** Milestone 4 (배포 무중단화)
**성격:** 👤 **인간 실행 스펙(human-run)** — 가역성상 사람만 가능한 비가역 작업(spec-02 §2 정렬축)
**근거 spec:** [spec-01 §9](spec-01-zero-downtime-deployment.md)(리소스 정의), [spec-02 §5](spec-02-harness-handoff-gates.md)(게이트 G1~G3)
**상태:** 🟢 G1 완료(2026-07-03) — 계정·부트스트랩(①~④)·아이덴티티(⑤ B_IAM)·Terraform 자격(⑥) 전부 완료. Terraform Phase B `plan` 성공(12 리소스). **다음 = G2 시크릿 + G3 `apply`(과금 게이트, 사람 GO)**

---

## 0. 이 문서를 언제 펴는가

전체 M4 작업 순서에서 사람이 끼는 단 한 구간이다:

```
①  테라폼·무중단 학습자료 읽기 (개념 적재)
         │
②  ▶ 이 문서(spec-04) 읽으며 손으로 AWS 셋업 ◀   ← G1 완료(2026-07-03). G2·G3 남음
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

> **계정 부트스트랩 순서: ①복구 앵커 → ②root 잠금 → ③빌링 → ④기본 → ⑤아이덴티티 → ⑥자격.**
> ①②와 ③⑤ 사이엔 *하드* 의존이 없다(MFA 없이도 IAM 생성 가능). 그러나 ⑤ 이후 작업은
> 모두 **root 세션에서** 하므로 ①②로 root 를 먼저 잠근 뒤 진행해 무방비 노출창을 줄인다.
> ③④는 가역이나 **콘솔 전용이라 사람만** 가능(하네스 불가) → ☐ 로 둔다.

- [x] ☐ **AWS 계정 생성** (`mmt.contact2026@gmail.com`, 2026-07-03 완료) — 가입 시 카드 등록 동반. Phase A(LocalStack)는 무계정으로도 먼저 경험 가능했음(spec-03 §2)
- [x] ☐ **① Gmail 2FA** — root 비번 복구 앵커. 여기가 실질 마스터키라 사슬 최하단
- [x] ☐ **② root 하드닝** — 강한 비번 + MFA(인증앱/하드웨어) + **root 액세스 키 없음 확인**(있으면 삭제; root 키는 만들지 않음). 이후 **root 로그인 금지** 원칙
- [x] ☐ **③ 빌링 안전핀** — 결제수단 확인 + AWS Budgets 월 예산·초과/예측 알림 + Billing preferences 이메일 알림 + Cost Explorer. 프리티어/크레딧(가입 후 12개월 만료) 잔량 위치 파악
- [x] ☐ **④ 계정 기본** — 대체 연락처(청구/운영/보안) + 기본 리전 **ap-northeast-2(서울)** 확인(provider.tf 와 정렬) + 태그 컨벤션(`Project=mmt`, 비용 추적·정리용)
- [x] ☐ **⑤ admin 아이덴티티 — ✅ 완료: B_IAM(IAM user + assume-role), 2026-07-03** (mmt-base/mmt-admin 프로필 + mmt-terraform-admin role 구성·assume 검증). 근거: **비대칭 가역성** — Identity Center(=Org 생성)는 무료 크레딧($100~200)을 *비가역* 소멸시키는데, Identity Center 자체는 *언제든* 켤 수 있다. 소멸성 자산(크레딧)부터 태우고 SSO 는 크레딧 소진·만료 후 전환. Paid 계정도 예외 없음(FAQ "When your account **joins an AWS Organization**... credits expire immediately" — plan 무관, [FAQ](https://aws.amazon.com/free/free-tier-faqs/)).
  - **채택 B_IAM.** IAM role `mmt-terraform-admin`(AdministratorAccess, 신뢰정책=내 user assume + `aws:MultiFactorAuthPresent:true`) ← IAM user `mmt-cli`(정책=그 role 에 대한 `sts:AssumeRole`만) + 액세스 키 + 가상 MFA. **장기 *admin* 키는 없음**(베이스 키는 assume-role 전용 최소권한). Org 미생성 → 크레딧 보존(가입 12개월 ~2027-07).
  - *대안 A_SSO(미채택).* Identity Center 조직 인스턴스 + `aws sso login`(정적 키 0, 더 위생적)이나 **켜는 순간 크레딧 소멸**. account instance 우회는 계정 접근 미지원이라 불가([문서](https://docs.aws.amazon.com/singlesignon/latest/userguide/account-instances-identity-center.html)). SSO 깔끔함 > 크레딧이면 override 가능 — 그땐 리전 먼저 확정 후 Enable.
  - 공통: 솔로 M4 엔 **S3/DynamoDB state 백엔드는 과함** — local state(gitignored) 유지(provider.tf §17).
- [x] ☐ **⑥ Terraform/CLI 자격 (B_IAM)** — `~/.aws/config` 에 `[profile mmt-base]`(정적 키, credentials 파일) + `[profile mmt-admin]`(`role_arn`+`source_profile=mmt-base`+`mfa_serial`+`duration_seconds`). 검증 `aws sts get-caller-identity --profile mmt-admin`(MFA 프롬프트). **키 커밋 절대 금지.** ⚠️ **Terraform 은 프로필의 MFA assume 를 대화형 처리 못 함**(`AssumeRoleTokenProvider session option not set`) → provider 에 `profile` 을 박지 않고, **`source infra/terraform/tf-assume.sh`** 로 role 을 MFA assume 해 `AWS_*` env 주입 후 plan/apply. `default_tags{Project="mmt"}` 는 provider 유지. Phase B 자격은 `TF_VAR` 아닌 이 env 경로로 spec-03 **Phase B/C** 에서 더미 `test`/`test` 대체(§3). M4 검증 후 베이스 키 삭제/rotate.

> 💸 **비용 현실(신규 계정 보정):** 이 계정(2026-07-03 생성)은 2025-07-15 이후라 **크레딧 기반 신규 플랜** — 레거시 "12개월 750h 무료"가 그대로 적용되지 않는다. blue-green t3.micro + RDS db.t3.micro 24/7 은 크레딧 소진 후 **서울 기준 대략 월 $25~30** 실과금. "프리티어라 계속 무료"가 아니라 **크레딧이 3~6개월 완충**이며, ③ zero-spend budget + `terraform destroy` 습관이 진짜 안전핀.
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

> ✅ **Terraform Phase B `plan` 성공(2026-07-03, 12 리소스)** — `infra/terraform/` 가 real AWS 에
> apply-ready. 키페어(`aws_key_pair.app`)·RDS 3306 SG 배선 완료(커밋 `f4e5c56`). 위 ◑ 항목 대부분이
> `terraform apply` 한 방(Phase C)으로 생성된다. **남은 트리거 = `apply`(과금, G3 사람 GO)** + apply 후
> RDS 시드·EC2 초기화(SSH via `~/.ssh/mmt-ec2`). 실행 절차·자격 획득 = `infra/terraform/README.md`.

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
