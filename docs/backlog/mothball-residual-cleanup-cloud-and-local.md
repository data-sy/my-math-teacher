# [Ops] 휴면 잔여 정리 — 클라우드에서만 닫혔고 로컬은 안 닫혔다

- **상태:** 🚧 진행 예정 (Now)
- **등록:** 2026-09-09
- **인프라 불요** — 클라우드 쪽은 read-only 점검 + 판단, 로컬 쪽은 이 맥에서 끝난다.

## 왜 남았나

2026-09-08 mothball 은 **AWS 쪽만** 닫았다(`terraform destroy` 18개 리소스 · DNS A레코드 삭제 · 시간당 과금 0 실측).
로컬은 손대지 않았고, 그 뒤로 상태가 조용히 어긋났다 — **MMT 로컬 스택은 이미 사라졌는데 문서는 "볼륨이 유지된다"고 말한다**
(재현성 쪽 정본 = [`local-dev-env-reproducibility-after-mothball.md`](local-dev-env-reproducibility-after-mothball.md)).

이 파일은 **"휴면을 로컬까지 확장해 닫는다"** 는 한 가지 일의 정본이다.

## 실측 (2026-09-09 · 이 맥)

| 축 | 값 |
|---|---|
| MMT 컨테이너 | **0** — `mmt-mysql`·`mmt-redis`·`mmt-ai`·`mmt-neo4j` 전부 없음 |
| MMT 볼륨 | **0** — `mysql-vol`·`neo4j-vol` 둘 다 없음 (데이터·시드 소멸) |
| MMT 이미지 | **0** — `mymathteacher/*` 태그 하나도 없음 |
| 지금 떠 있는 것 | `high-traffic-performance-tuning` 스택 4개(mysql:8.0 · redis:7-alpine · prometheus · grafana) |
| 도커 총 점유 | 이미지 3.279GB · 볼륨 262.6MB · reclaimable **0B**(전부 사용 중) |

즉 **회수할 MMT 찌꺼기는 없다.** 도커가 먹고 있는 3.3GB 는 전부 남의 스택 것이고, 그건 MMT 가 지울 대상이 아니다
(⚠️ 3306/6379 를 공유하므로 MMT 를 다시 띄우려면 그쪽을 `docker compose stop` 으로 비켜야 한다 — `CLAUDE.local.md`).

## 할 일

### A. 로컬 (지금 가능)

- [ ] **"MMT 로컬 흔적 0" 을 사실로 확정**하고 문서를 그에 맞춘다 — 위 실측이 근거. 지울 게 없다는 것도 결론이다.
- [ ] `docs/DEVELOPMENT.md` 종료 절의 **"볼륨은 유지되므로 초기 데이터 적재를 반복할 필요가 없다"** 서술 정정
      (지금은 반대다 — 다음 기동은 **시드 재적재부터**다). → 재현성 파일에서 함께 처리.
- [ ] 남의 스택과의 포트 충돌 절차가 여전히 유효한지 한 번 확인(`docker ps` → `stop` → MMT → `start` 원복).

### B. 클라우드 (read-only 점검 후 판단 — 파괴 아님)

- [x] **도메인 만료일 확인** — **2027-06-17**(whois, 2026-10-01). 휴면 중 만료되지 않는다.
      등록·호스팅 영역이 **타 AWS 계정**이다. 계속 보유할 생각이고 자동갱신일 것으로 기억하나(사용자, 2026-10-02) 그 계정 콘솔에서 확인한 적은 없다.
- [x] 잔여 과금 재확인 (2026-10-02 · 전 리전) — 과금 리소스는 RDS 스냅샷 하나뿐이었고(9/10 이후 21일 $0.057 ≈ 월 $0.08),
      그것도 같은 날 삭제했다. 예산 초과분은 Cost Explorer 에 잡히지 않았다($0). **지금 과금 리소스 0.**
- [x] **RDS 스냅샷 `mmt-mothball-2026-09-08` 삭제** (2026-10-02 · 사용자 결정 "개발 공간이라 지워도 된다").
      계기 = AWS 의 스냅샷 비용 표시 변경 안내 메일. 삭제 후 `describe-db-snapshots` 0줄 확인.
- [x] 예산 `RDS-Monthly-Cost` — **존치 결정** (2026-09-13 사용자 승인). 월 ~$0.5 를 내고 재런치 마찰을 없앤다.
      청구 알람을 통째로 살려둔 기존 방침과 같은 결. **이 항목은 더 묻지 않는다.**

### C. 스냅샷 삭제 후속 (2026-10-02)

- [x] **`infra/terraform/database.tf` 의 `snapshot_identifier` 제거** — 삭제된 스냅샷을 가리켜 `apply` 가 실패할 상태였다.
      참조 지점은 그 한 줄뿐(terraform 내부 소비자·테스트·CI 0), state 는 비어 있어 교체될 인스턴스가 없다. `terraform validate` 통과.
- [x] **재런치 런북 §1~§3 을 "빈 RDS + 시드 적재" 경로로 재작성.**
- [x] **수명이 끝난 스크립트 삭제** — `mothball-1/2/3`(죽은 스냅샷·EIP 하드코딩) · `zdbg-cleanup` · `m8-ddl-and-bottleneck` ·
      `seed-concept-links`(프로덕션 호스트 전용 — 추적본 `shared/scripts/concept-links-seed-to-sql.sh` 가 따로 있다). 필요하면 git 히스토리.
- [ ] ⚠️ **빈 RDS + 시드 경로는 실증된 적이 없다** — 재런치 때 처음 돌아간다. 선행 = 시드 정본 확정
      ([재현성 파일](local-dev-env-reproducibility-after-mothball.md)) + 로컬에서 같은 순서로 한 번 적재해 보기.

### ⛔ 이 정리에서 건드리지 않는 것

- **청구 알람 3종(예산 3 + CloudWatch 2 + SNS `billing-alerts`)** — 휴면 중 이상 과금을 알아채는 **유일한 경로**라 일부러 살려 뒀다.
- **IAM / OIDC / keypair** — $0 이고 재런치 마찰만 늘린다.

## 검증

```bash
docker ps -a --filter name=mmt          # 0줄이어야 한다
docker volume ls | grep -E 'mysql-vol|neo4j-vol'   # 0줄
docker system df                        # 회수 가능량이 MMT 때문이 아님을 확인
dig +short www.my-math-teacher.com      # 비어 있어야 한다 (dangling DNS 없음)
whois my-math-teacher.com | grep -i expir   # 도메인 만료일
```

클라우드 잔여 과금은 스크립트 없이 본다 — 콘솔 Billing 의 서비스별 요금, 또는 Cost Explorer 에서
`RECORD_TYPE ≠ Credit` 필터로 gross 를 본다(크레딧 차감 후 값은 실사용을 가린다).

## 연결

- 티어다운·재런치 정본 = [`🤖-M7-인프라-티어다운-재런치.md`](../handoff/🤖-M7-인프라-티어다운-재런치.md) §4 / §3
- 로컬 재현성 = [`local-dev-env-reproducibility-after-mothball.md`](local-dev-env-reproducibility-after-mothball.md)
- 런북에 남은 열린 항목 = [`teardown-runbook-open-items.md`](teardown-runbook-open-items.md)
