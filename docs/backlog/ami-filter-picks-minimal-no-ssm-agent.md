# [Infra] AMI 필터가 minimal 을 집어 SSM 에이전트가 없는 인스턴스가 뜬다 (+ 전체 apply 가 EC2 를 교체할 위험)

**등록:** 2026-08-07 (CD 실패 근본 원인 추적 중 확정) · **상태:** ✅ **해결 (2026-08-31)** — D2·D3·D1 전부 적용 ·
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

## 재확인 — 예측이 실측이 됐다 (2026-08-31)

RDS Extended Support 과금 대응 중 `terraform plan`(읽기 전용)을 돌리자 **D2 가 경고한 그대로**가 떴다:

```
  # aws_eip_association.app must be replaced
      ~ instance_id = "i-098e63bf15a150633" -> (known after apply) # forces replacement
  # aws_instance.app must be replaced
      ~ ami = "ami-07ed1042cd8928bd3" -> "ami-072139fac78f90345"   # forces replacement
Plan: 2 to add, 0 to change, 2 to destroy.
```

새 AL2023 AMI 가 릴리스되면서 `most_recent = true` 가 새 id 를 집었다. **지금 전체 apply 를 돌리면
프로덕션 EC2 와 EIP 연결이 교체된다** — 위 "결함 2" 가 가설이 아니라 현재 상태라는 뜻이다.

이 때문에 RDS 8.0→8.4 업그레이드는 **터라폼 apply 경로를 포기**하고 RDS API 직접 `modify-db-instance`
+ `terraform apply -refresh-only`(리소스 무변경, state 만 갱신)로 우회했다. 우회는 이번 한 번을 넘겼을 뿐
**이 항목을 해결하지 않는다.** `-refresh-only` 가 아닌 apply 가 필요한 다음 인프라 작업은 전부 이 지뢰 위에 선다.

→ 권장 순서(D2 → D3 → D1)는 그대로. 우선순위만 **📌 미착수 → 🔴 다음 인프라 변경의 선행조건**으로 올린다.

## 해결 (2026-08-31) — D2 → D3 → D1 전부 적용

권장 순서 그대로 적용했다. **AWS apply 는 하지 않았다** — 세 변경 모두 plan-time/create-time
전용이라 리소스 변경이 필요 없다. `lifecycle` 은 state 에 저장되지 않는 메타 인자이고,
`ignore_changes` 대상 두 속성은 diff 자체가 억제되며, AMI 필터는 다음 create 때 평가된다.

| | 무엇 | 커밋 | 적용 후 plan |
|---|---|---|---|
| D2 | `lifecycle { ignore_changes = [ami] }` | `64bb710` | `0 to add, 1 to change, 0 to destroy` |
| D3 | user_data 에 `amazon-ssm-agent` 설치 + `ignore_changes` 에 `user_data` 추가 | `b067cca` | 동일 (`aws_instance.app` 이 액션 목록에서 소멸) |
| D1 | 필터를 `al2023-ami-2023.*-x86_64` 로 축소 | `1cee5e7` | 동일 |

기준선이던 `Plan: 2 to add, 0 to change, 2 to destroy` 는 사라졌다. 남은 1건은 무관한
RDS `engine_version` 표기 diff(`8.4.11` → `8.4`)로, 이 항목의 스코프 밖이다.

### 착수 중 드러난 정정 2건

**1. 지뢰가 이 문서보다 넓었다.** 위에서는 minimal 만 지목했는데, `describe-images` 로
느슨한 필터가 매칭하는 계열을 전수하니 **4계열**이었다:

```
al2023-ami-           ✅ 표준 (원하는 것)
al2023-ami-minimal-   ✗ SSM 에이전트 없음
al2023-ami-ecs-hvm-        ✗ 이 문서에 없던 것
al2023-ami-ecs-neuron-hvm- ✗ 이 문서에 없던 것
```

교체 직전이던 `ami-072139fac78f90345` 는 minimal 이 **아니라** `al2023-ami-ecs-hvm-2023.0.20260820`
이었다. 조인 필터로 재질의하면 ecs·minimal 매칭은 0건이고, plan 은
`al2023-ami-2023.12.20260817.0-kernel-6.1-x86_64`(`ami-0729121845edb4108`)를 해석한다.

**2. D3 는 공짜가 아니었다.** 위에서는 "멱등"이라고만 적었는데, provider 5.100 은
`user_data_replace_on_change` 기본값이 `false` 라 user_data 변경이 교체가 아닌 **in-place
업데이트**로 뜨고, 그 구현이 **stop → ModifyInstanceAttribute → start** 다
(plan 의 `~ public_ip -> (known after apply)` 가 그 흔적). 즉 상시 서비스에 정지/재기동
다운타임이 생기는데, cloud-init 은 최초 부팅에만 실행되므로 **러닝 인스턴스에는 에이전트가
설치되지도 않는다** — 다운타임만 내고 얻는 게 없다.

그래서 `ignore_changes` 에 `user_data` 를 함께 넣었다(사용자 결정). `ignore_changes` 는
create 에 적용되지 않으므로 **다음 런치는 최신 user_data 로 뜬다** — 목적은 달성하면서
러닝 호스트는 무접촉이다. D3 는 "러닝 인스턴스 복구책"이 아니라 **다음 인스턴스 보장책**이다.

### 러닝 인스턴스 현황

`i-098e63bf15a150633` 은 여전히 `al2023-ami-minimal-2023.12.20260803.3` 로 떠 있지만
SSM 은 `Online`(에이전트 3.3.4624.0, 2026-08-31 확인)이다 — `ssm-recover.sh --apply` 로
수동 설치된 상태다. CD 는 정상이며, 이 인스턴스를 교체할 이유는 없다.

### 남은 잔가지

- 조인 필터도 커널 변형 3종(`kernel-6.1`·`6.12`·`6.18`)을 동시 매칭해 `most_recent` 의
  타이브레이크가 비결정적이다. 셋 다 SSM 에이전트를 포함한 표준 이미지라 CD 관점에선
  무해하고, **다음 런치의 커널 버전이 흔들릴 수 있다**는 점만 남는다. 커널을 고정하려면
  필터에 `-kernel-6.12-` 같은 조각을 더 박으면 되지만, 그건 EOL 때 다시 손대야 하는 핀이다.
- `ignore_changes = [ami]` 때문에 **AMI 보안 업데이트가 자동으로 따라오지 않는다.**
  올릴 때는 사람이 lifecycle 블록을 일시 제거하거나 taint 로 의도적으로 교체한다 —
  그때는 호스트 로컬 자산 재구성(재런치 절차 §3)이 따라온다는 걸 전제로 계획해야 한다.
