# 🤖 다음 세션 — AMI 필터 지뢰 제거

**작성:** 2026-08-31 (RDS 8.4 업그레이드 세션 말미) · **선행 작업 브랜치:** `ops/rds-mysql-84-upgrade`

> 이 파일은 **복붙 실행용**이다. 다음 세션 시작할 때 이걸 통째로 읽히면 된다.
> 정본 분석·결정거리는 [`docs/backlog/ami-filter-picks-minimal-no-ssm-agent.md`](docs/backlog/ami-filter-picks-minimal-no-ssm-agent.md) 다 —
> 여기 복제하지 않았다. **작업 전 그 파일을 먼저 읽어라.**

---

## 한 줄

`infra/terraform/compute.tf` 의 AMI 데이터소스가 `most_recent = true` + 느슨한 필터라
**새 AL2023 AMI 가 나올 때마다 전체 `terraform apply` 가 프로덕션 EC2 를 교체하려 든다.**
2026-08-31 현재 그 상태이고, 이건 가설이 아니라 실측이다.

## 지금 상태 (2026-08-31 측정)

```
$ terraform plan          # infra/terraform, 읽기 전용
  # aws_eip_association.app must be replaced
      ~ instance_id = "i-098e63bf15a150633" -> (known after apply)  # forces replacement
  # aws_instance.app must be replaced
      ~ ami = "ami-07ed1042cd8928bd3" -> "ami-072139fac78f90345"    # forces replacement
Plan: 2 to add, 0 to change, 2 to destroy.
```

`aws_instance.app` 교체 = 호스트 로컬 자산이 **전부 소멸**한다:
`~/mmt-backend.env`(시크릿) · `~/active-backend.conf` · `~/deploy/` · nginx 설정 · TLS 인증서.
재런치 절차(`docs/handoff/🤖-M7-인프라-티어다운-재런치.md` §3)를 처음부터 다시 밟아야 한다.

## 왜 지금 급한가

RDS 업그레이드는 이 지뢰를 **우회**했다 — 터라폼 apply 대신 RDS API 직접 호출 + `apply -refresh-only`.
우회는 이번 한 번을 넘겼을 뿐이고, **`-refresh-only` 가 아닌 apply 가 필요한 다음 인프라 작업은
전부 이 지뢰 위에 선다.** 즉 이건 "언젠가"가 아니라 **다음 인프라 변경의 선행조건**이다.

## 결정거리 (백로그 §결정거리 = 정본)

| | 무엇 | 성격 |
|---|---|---|
| D1 | 필터를 `al2023-ami-2023.*-x86_64` 로 조인다 (minimal 배제) | 위생 — **단독 적용 시 위험**(AMI id 가 바뀌어 교체 plan 을 만든다) |
| D2 | `lifecycle { ignore_changes = [ami] }` | 안전장치 — 우발 교체 차단. 대가 = AMI 고정(보안 업데이트 수동) |
| D3 | user_data 에 `dnf install -y amazon-ssm-agent` | 보장 — AMI 변형과 무관, 멱등 |

**권장 순서 = D2 → D3 → D1.** D2 없이 D1 을 적용하면 **그 apply 가 프로덕션을 교체한다.**
⚠️ 이건 트레이드오프다(특히 D2 의 "AMI 고정 vs 우발 교체"). **혼자 정하지 말고 사용자와 합의하고 착수.**

## 착수 절차

1. **[사람]** AWS 세션 발급 — `bash ~/mmt-aws-session.sh` (MFA 6자리 → `mmt-session` 프로파일, 1시간)
2. `docs/backlog/ami-filter-picks-minimal-no-ssm-agent.md` 정독 (D1~D3 근거·2026-08-07 실측)
3. `/analyze-before-change` — `aws_instance.app` 참조 지점·영향 테스트·롤백 시나리오
4. D2 적용 후 **반드시 `terraform plan` 전문을 읽는다.**
   **`Plan:` 줄에 `to destroy` 가 0 이 아니면 멈춘다.** 목표 상태 = `0 to destroy`
5. D3 → D1 순서로 반복. 각 단계마다 4번의 검문을 다시 통과시킨다
6. 커밋은 D2 / D3 / D1 **각각 분리** (Task 단위 규칙)

## 함정

- ⚠️ **`terraform apply` 는 에이전트가 실행할 수 없다**(분류기 차단, `-target` 포함).
  plan 까지는 에이전트가 돌린다. **apply 는 사람이 실행**하고 출력을 붙여넣는다.
- ⚠️ 터라폼 자격은 프로파일이 아니라 **env** 로 들어간다(provider 에 profile 미지정).
  `source infra/terraform/tf-assume.sh` 를 쓰거나, `mmt-session` 프로파일에서 꺼내 export 한다.
- ⚠️ state 는 **로컬**(`infra/terraform/terraform.tfstate`, remote backend 없음). 백업본이 옆에 있다.
- ⚠️ `aws_db_instance.app` 의 `snapshot_identifier` 는 **ForceNew** — 건드리면 RDS 데이터 소멸. 고정.

## 곁다리로 남아 있는 것 (이 작업과 별개, 섞지 말 것)

- **터라폼 표기 정리** — `engine_version` 이 state `8.4.11` vs config `8.4` 라 in-place diff 1건이 뜬다.
  순수 표기 차이이고 무해하다. AMI 작업으로 plan 이 clean 해지면 그때 같이 흡수된다.
- **`EngineLifecycleSupport` 결정** — 현재 `open-source-rds-extended-support`.
  8.4 는 표준 지원 구간이라 지금은 무과금이지만, 이 값이 남아 있으면 **8.4 EOL 때 같은 일이 자동 재발**한다.
- **`/api/v1/concepts/{없는 id}` 가 404 아닌 500** — 업그레이드와 무관한 기존 에러 처리 누락.
