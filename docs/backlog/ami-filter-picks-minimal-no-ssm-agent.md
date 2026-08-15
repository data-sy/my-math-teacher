# [Infra] AMI 필터가 minimal 을 집어 SSM 에이전트가 없는 인스턴스가 뜬다 (+ 전체 apply 가 EC2 를 교체할 위험)

**등록:** 2026-08-07 (CD 실패 근본 원인 추적 중 확정) · **상태:** 📌 **미착수 — 결정 필요** ·
**분류:** IaC 결함 / 재발성 · **관련:** [CD 복구](ci-backend-image-missing-secure-yml.md)

> **한 줄:** `compute.tf` 의 AMI 필터가 `al2023-ami-*-x86_64` 라 **minimal 변형까지 매칭**하고,
> `most_recent = true` 와 겹쳐 재런치 때 **SSM 에이전트가 없는 minimal 이미지**를 집었다. 그 결과 CD 가 죽었다.

## 실측 (2026-08-07)

인스턴스 `i-098e63bf15a150633`:
```
/etc/image-id → image_name="al2023-ami-minimal"
                image_file="al2023-ami-minimal-2023.12.20260803.0-kernel-6.18-x86_64.xfs.gpt"
rpm -q amazon-ssm-agent → not installed
systemctl → Unit amazon-ssm-agent.service could not be found
```
IMDSv2·instance-id·IAM 롤(`mmt-ec2-ssm-role`)은 **전부 정상**이었다 — 자격증명·네트워크 문제가 아니라
**이미지 선택** 문제다. 2026-07-27·28 CD 성공 당시엔 표준 이미지였고, 2026-08-05 재런치에서 갈렸다.

## 결함 2개 (같은 뿌리)

1. **필터가 너무 느슨하다** — `values = ["al2023-ami-*-x86_64"]`(compute.tf:18)가
   `al2023-ami-minimal-...` 도 매칭한다. 표준 이미지만 잡으려면 `al2023-ami-2023.*-x86_64`.
2. **`most_recent = true` + `lifecycle` 블록 없음** — `aws_instance.ami` 가 바뀌면 **교체(destroy+create)** 다.
   즉 **새 AL2023 AMI 가 릴리스될 때마다, 전체 `terraform apply` 가 프로덕션 EC2 를 갈아엎으려 든다.**
   2026-08-07 의 IP 동기화는 `-target` 으로 SG 규칙만 건드려서 무사했다 — **전체 apply 였으면 위험했다.**

## 임시 대응 (이미 있음)

`docs/handoff/scripts/ssm-recover.sh --apply` 가 에이전트가 없으면 `dnf install` 후 기동한다.
**러닝 인스턴스는 이걸로 복구되지만, 다음에 인스턴스를 새로 만들면 같은 일이 반복된다.**

## 결정거리

- **D1 — 필터를 조일까:** `al2023-ami-2023.*-x86_64` 로 좁히면 minimal 을 배제한다. 단 이 변경 자체가
  **AMI id 를 바꿔 교체 plan 을 만든다** — D2 없이 적용하면 위험하다.
- **D2 — `lifecycle { ignore_changes = [ami] }` 를 넣을까:** 프로덕션 EC2 의 우발적 교체를 막는다.
  대신 **AMI 가 고정**돼 보안 업데이트가 자동으로 따라오지 않는다(교체는 사람이 의도적으로).
  1인 운영·상시 서비스에선 **우발 교체 방지가 더 급하다**는 판단이 자연스럽지만, 명시 결정이 필요하다.
- **D3 — user_data 에 `dnf install -y amazon-ssm-agent` 를 넣을까:** AMI 변형과 무관하게 보장된다.
  D1 의 백업(멱등, 표준 이미지면 no-op). 셋 다 채택해도 서로 충돌하지 않는다.

**권장 = D2 먼저(안전장치) → D3(보장) → D1(위생).** D2 없이 D1 을 적용하면 그 apply 가 프로덕션을 교체한다.

## 착수 시 주의

- 어떤 순서든 **`terraform plan` 전문을 읽고** `aws_instance.app` 에 `must be replaced` 가 뜨는지 먼저 확인한다.
- RDS 는 별개지만, EC2 교체는 호스트 로컬 자산(`~/mmt-backend.env`·`~/active-backend.conf`·`~/deploy/`)을
  전부 날린다 — 재런치 절차(`docs/handoff/🤖-M7-인프라-티어다운-재런치.md` §3)를 다시 밟아야 한다.
