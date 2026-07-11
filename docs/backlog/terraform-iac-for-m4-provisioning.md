# Backlog: Terraform 으로 M4 EC2/RDS 프로비저닝 (IaC 학습 겸용)

**분류:** [Infra] / 학습(chosen) — 백로그
**관련:** M4 spec-01 §9(프리티어 프로비저닝), spec-02 §5 **G3**(infra apply 게이트)
**상태:** ✅ **완료(M4 에서 소진, 2026-07-05·07-06).** Phase A(LocalStack plan-only 4슬라이스) → Phase B(real AWS plan) → M4 라이브에서 `apply(18 리소스)→측정→destroy` 사이클 수 회 실행. G3 게이트가 실제 `terraform apply` 로 동작함(사람 MFA + plan 승인). 이 문서는 배경·의사결정·경계 기록으로 보존.

> roadmap "Later — 백로그" 의 한 줄 항목을 푼 상세 문서. 의사결정·경계·단계만 정의하고
> 실제 HCL 작성/실행은 별도 Task 로 분리한다.

---

## 1. 왜 백로그인가 (요약)

M4 §9 의 인프라(EC2 1 + RDS 1 + EIP + SG)는 **한 번 깔고 거의 안 건드리는 솔로 인프라**다.
순수 효율·포폴 기준이면 콘솔 클릭 30분짜리라 Terraform 의 학습 비용·state 관리를 얹는 건
ROI 가 안 맞는다. 따라서 **무중단 배포(M4 본류)의 차단 요소가 아니라 선택적 학습 항목**으로
백로그에 둔다.

다만 목적이 **"Terraform 이 뭔지 경험"이면 오히려 좋은 첫 소재**다:

- 토이 튜토리얼보다 *실제로 쓸 데 있는* 인프라라 동기부여가 되고, 작아서 안 빠진다.
- 코어 프리미티브를 다 만진다: `provider` / `resource` / `variable` / `output`,
  `init` → `plan` → `apply` → `destroy`, 리소스 그래프.

## 2. detour 가 아닌 이유 — G3 와 맞물림

spec-02 §5 의 **G3(infra apply) 게이트가 이미 Terraform 을 가정**하고 쓰였다:
emit 이 "`terraform apply` 실행 요청 + plan diff", 재개 검증이 "`plan` == empty diff".
즉 §9 의 실제 프로비저닝 도구로 Terraform 을 쓰면 학습이 M4 와 따로 노는 게 아니라
**G3 그 자체**가 된다.

## 3. 솔직한 경계 둘 (기대치 정렬)

1. **이 규모에선 IaC 의 *진짜* 가치가 안 드러난다.** 재현성·드리프트 감지·팀 단위
   remote state/locking 이 IaC 의 핵심 payoff 인데, 한 번 깔고 마는 솔로 인프라에선 발현될
   일이 없다. HCL 문법 + 리소스 그래프 + plan/apply 멘탈모델은 확실히 배우지만, "왜 회사들이
   이걸 쓰나"의 절반(state 협업)은 이 프로젝트로는 체감 못 한다 → **"경험"엔 충분, "실무
   마스터"는 아님.** 이 선만 알고 가면 기대가 안 어긋난다.

2. **IaC 는 비용 리스크를 *키운다*.** spin-up 마찰을 0 으로 만드니 `apply` 하고 잊기 쉽다 —
   특히 RDS. 프리티어 한도(t3.micro·RDS db.t3.micro 각 750h/월, 스토리지 20GB)를 넘으면
   과금된다. 콘솔이면 "내가 뭘 켰지"가 눈에 보이는데 IaC 는 추상화돼 안 보인다 →
   **`terraform destroy` 규율이 콘솔 때보다 더 중요하다.**

## 4. 단계 — 학습과 프로비저닝 분리 (비용 0 으로 지금 시작)

Terraform 학습은 `apply` 없이도 70% 된다. config 쓰고 `init` → `plan` 까지는 **리소스를
안 만들고 비용도 0**인데 HCL 문법·리소스 그래프·diff 읽기는 다 배운다.

| 단계 | 내용 | 비용 | 선행조건 |
|---|---|---|---|
| **지금 (무과금)** | EC2/RDS/EIP/SG config 를 HCL 로 작성 → `init` → `plan` 으로 "뭐가 만들어질지" diff 읽기 | 0 | AWS 계정 + read 권한(spec-02 T1 티어면 충분). 결제 결정·실제 생성 없음 |
| **나중 (§9, 준비됐을 때)** | 같은 config 에 `apply` → 진짜 프로비저닝 + 과금 시작 = **G3** | 과금 | G1(계정·결제) 준비 완료 |

→ "Terraform 가지고 놀기"를 **비용 0 으로 지금** 시작하고, 돈 드는 `apply` 는 G1 준비됐을
때로 미룬다.

## 5. 대상 리소스 스케치 (apply 단계에서 확정)

- `aws_instance` t3.micro (Amazon Linux 2023, gp3 30GB) — spec-01 §9.2
- `aws_db_instance` db.t3.micro (MySQL, Single-AZ, 20GB) — spec-01 §9.3
- `aws_eip` 1개 (실행 인스턴스 연결 시 무료)
- `aws_security_group` — 80/443 공개, 22 내 IP, 8080 비공개 (spec-01 §9.2)
- 리전 ap-northeast-2(서울)

> ⚠️ **시크릿·자격증명은 HCL/state 에 평문으로 두지 않는다.** RDS 비밀번호 등은 변수 +
> 비커밋 `*.tfvars`(gitignore) 또는 환경변수로만. tfstate 도 자격을 담으므로 커밋 금지.
> (루트 CLAUDE.md 금지사항 + spec-02 G2 시크릿 규칙과 정합.)

## 6. 착수 조건 / 보류 사유

- M4 무중단 배포 본류(B nginx → C 스크립트 → D 워크플로 → §9 프로비저닝)와 **병행 가능** —
  Terraform 샌드박스는 AWS 무관 부분(plan-only)이라 별도로 진행해도 안 섞인다.
- 우선순위가 올라가면(또는 §9 프로비저닝을 콘솔 대신 IaC 로 하기로 결정하면) 별도 spec 으로
  승격. 그 전까지는 학습 선택 항목으로 보류.

> 🔼 **승격됨 (2026-06-16): plan-only 샌드박스로 착수.** 계정 없음에서 출발하는 실행 스펙
> = [`docs/specs/m4/spec-03-terraform-plan-only-iac-sandbox.md`](../specs/m4/spec-03-terraform-plan-only-iac-sandbox.md)
> (무과금 경계·LocalStack 무계정 경로·HCL 구성·G3 하네스 분담). 본 백로그 문서는 그 *배경·경계*
> 기록으로 유지.
