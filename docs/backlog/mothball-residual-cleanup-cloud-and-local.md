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

점검 도구는 이미 있다: [`docs/handoff/scripts/mothball-3-residual.sh`](../handoff/scripts/mothball-3-residual.sh) (read-only).

- [ ] **도메인 만료일 확인** — ⚠️ 가장 급하다. 등록·호스팅 영역이 **타 AWS 계정**이라 이 계정의 알람에 안 걸린다.
      휴면 7개월 중 만료되면 **도메인을 잃는다**(회복 불가 · 재런치 런북이 전제하는 이름이 사라진다).
- [ ] 잔여 과금 재확인 — 스냅샷 1개(~$0.24) + 예산 초과분(~$0.5). 월 $1 미만이면 정상.
- [x] 예산 `RDS-Monthly-Cost` — **존치 결정** (2026-09-13 사용자 승인). 월 ~$0.5 를 내고 재런치 마찰을 없앤다.
      청구 알람을 통째로 살려둔 기존 방침과 같은 결. **이 항목은 더 묻지 않는다.**

### ⛔ 이 정리에서 건드리지 않는 것

- **RDS 수동 스냅샷 `mmt-mothball-2026-09-08`** — 계정에 남은 **유일한 데이터 정본**. 삭제 = 프로젝트 데이터 소멸.
- **청구 알람 3종(예산 3 + CloudWatch 2 + SNS `billing-alerts`)** — 휴면 중 이상 과금을 알아채는 **유일한 경로**라 일부러 살려 뒀다.
- **`infra/terraform/database.tf` 의 `snapshot_identifier`** — ForceNew 라 값을 바꾸면 RDS 교체 = 데이터 소멸.
- **IAM / OIDC / keypair** — $0 이고 재런치 마찰만 늘린다.

## 검증

```bash
docker ps -a --filter name=mmt          # 0줄이어야 한다
docker volume ls | grep -E 'mysql-vol|neo4j-vol'   # 0줄
docker system df                        # 회수 가능량이 MMT 때문이 아님을 확인
bash docs/handoff/scripts/mothball-3-residual.sh   # 잔여 과금·dangling DNS (read-only)
```

도메인 만료일은 **타 계정**이라 위 스크립트로 안 잡힌다 — 등록 계정의 Route53/Registered domains 에서 직접 본다.

## 연결

- 티어다운·재런치 정본 = [`🤖-M7-인프라-티어다운-재런치.md`](../handoff/🤖-M7-인프라-티어다운-재런치.md) §4 / §3
- 로컬 재현성 = [`local-dev-env-reproducibility-after-mothball.md`](local-dev-env-reproducibility-after-mothball.md)
- 런북에 남은 열린 항목 = [`teardown-runbook-open-items.md`](teardown-runbook-open-items.md)
