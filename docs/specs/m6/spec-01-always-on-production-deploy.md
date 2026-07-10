# Spec 01: 상시 프로덕션 배포 기반 (Always-On Production Deploy)

**상위 마일스톤:** Milestone 6 (프로덕션 상시 배포 — 이력서 라이브 링크)
**대상 Phase:** Phase 1 (상시화 + 도메인 + TLS 토대)
**선행 spec:** M4 spec-01(무중단 배포 기반, 완료) — 본 spec 은 그 메커니즘을 *소비*한다
**후속:** spec-02(👤 사람 핸드오프 — 도메인 DNS·상시 결제·수명주기)

> ⚠️ **상태: 설계 뼈대(skeleton) — 구현 미착수.** 섹션 골격과 확정된 사실만 채웠고, 실파일
> 대조가 필요한 부분(§1)·아키텍처 상세(§2)는 `[TODO: 착수 시 채움]` 으로 표시했다. §결정(§7)
> 사인오프 이전에는 코드/인프라를 변경하지 않는다.

---

## 0. 범위

M4 blue-green 배포 메커니즘을 **상시(always-on) 공개 프로덕션**으로 전환하는 토대를 깐다.
새로 설계하는 표면은 네 가지뿐 — ① 스택 확정(린), ② 상시 프로비저닝(destroy 없는 apply + EIP 고정),
③ 도메인/DNS 연결(타 계정), ④ TLS/HTTPS. 배포 컷오버 로직·IaC 리소스·측정 하네스는 **M4 것을 그대로 재사용**한다.

산출물: 외부에서 접속 가능한 `https://www.my-math-teacher.com`.

---

## 1. 현재 상태 분석 (실파일 기준)

> 아래 자산은 경로 실존만 확인(2026-07-11). 내부 내용의 상시화 영향 분석은 `[TODO]`.

### 1.1 재사용하는 M4 자산 (인벤토리)

| 자산 | 경로 | M6 에서의 처지 |
|---|---|---|
| Terraform IaC | `infra/terraform/{compute,database,network,iam,provider,variables}.tf` | 상시용으로 조정(destroy 안 함, EIP 고정) — `[TODO: 상시화 diff]` |
| tfvars/state | `infra/terraform/terraform.tfvars` · `terraform.tfstate` | `[TODO: 현재 state 가 살아있는지/빈지 확인 — destroy 후 잔여 0 이었음]` |
| 배포 스크립트 | `deploy/switch-backend.sh` | 그대로 재사용(blue-green 컷오버) |
| 측정 하네스 | `infra/terraform/run-log.sh` | destroy 단계 스킵하고 상시 측정에 재사용 |
| 프론트 nginx | `web/nginx.conf` | TLS(443) 추가 지점 — `[TODO: 443/인증서 블록 설계]` |
| 헬스 | `api/.../controller/HealthController.java` | 그대로(컷오버 게이트) |
| CI 워크플로 | `.github/workflows/api-ci-cd-with-ec2.yml` | 그대로(수동 dispatch) |

### 1.2 상시화가 바꾸는 전제

- **destroy 규율 역전** — M4 는 측정 후 `terraform destroy` 가 안전이었다. M6 는 destroy = 링크 사망. `run-log.sh` 의 destroy 훅·SessionEnd teardown 류 금지. `[TODO: run-log.sh 의 destroy 경로 식별]`
- **공개 노출 상시화** — SG·인증·시크릿·rate limit 표면이 상시 열림. `[TODO: SG 인그레스·SecurityConfig 공개 엔드포인트 재점검]`
- **비용이 시간에 비례** — 원샷이 아니라 월 단위. `[TODO: 백로그 §2 스택별 월액 재확인]`

---

## 2. 타깃 아키텍처

> `[TODO: 착수 시 다이어그램/구성 확정]`. 스케치:

```
사용자 → (443/TLS) → 타 계정 Route53 (www A → EIP)
                        → EC2(EIP 고정, ap-northeast-2)
                            nginx(443 TLS 종단, 80→443 리다이렉트)
                              → blue/green 백엔드(switch-backend.sh 컷오버)
                              → 정적 SPA(web/dist)
                            MySQL(RDS 또는 로컬=§7 D3) · Redis(로컬) · TF Serving(실서빙 유지=§7 D1 확정 a)
                            Neo4j 미구동(CTE-only, mmt.migration.use-mysql-cte-for-graph=true)
```

- 도메인 등록은 **타 계정 유지**(이관 없음). DNS 위임만 — 등록/호스팅 영역이 다른 계정이어도 정상 동작.
- TLS: `[TODO: Let's Encrypt(certbot, nginx 종단, 90일 자동갱신) vs ACM — §7 D2]`

---

## 3. 변경할 파일과 셋업 단계

> `[TODO: §7 결정 확정 후 구체화]`. 큰 단계:

1. 스택 확정(§7 D1) → terraform `variables`/인스턴스 타입 반영
2. 상시 `apply`(destroy 없음) → EIP 할당·고정 확인
3. 타 계정 호스팅 영역에 `www` A → EIP (spec-02 사람 작업)
4. nginx 443/TLS 블록 + 80→443 리다이렉트, 인증서 발급(spec-02 사람 작업)
5. `run-log.sh` 상시 모드(destroy 스킵)로 기동 측정
6. AWS Budgets 예산 알림(spec-02)

---

## 4. 검증 방법

> `[TODO: 측정 설계 상세]`. 성공 기준 골격:

- **외부 접속**: 로컬 아닌 외부 네트워크에서 `https://www.my-math-teacher.com` 200 + 유효 인증서
- **핵심 플로우 라이브**: 개념 그래프 탐색(CTE 경로) · 진단 결과 — 실브라우저에서 동작(사람 시각검증)
- **무중단 유지**: M4 검증 재현 — 배포 도중 `http_req_failed==0`(회귀 없음 확인 수준)
- **DNS 해석**: `dig www.my-math-teacher.com` → EIP, 전파 확인

---

## 5. 측정·기준선 메모

`[TODO]` — 상시 인스턴스 실 RAM/CPU 스냅샷(`run-log.sh` docker-stats), 크레딧 소진 속도 기준선.

---

## 6. Out of scope

- 상시 관측성·알림(→ M5) · 오토스케일링·멀티 인스턴스·LB·K8s
- 무중단 DB 스키마 마이그레이션 · 도메인 등록 이관 · Neo4j 실삭제(→ M3)

---

## 7. 결정 (사인오프 대기)

| # | 결정 | 옵션 | 상태 |
|---|---|---|---|
| **D1** | 데모 스택 | (a) **TF Serving 실서빙 유지 = 4GB(t3.medium ~월 $37)** — 진단 확률까지 실기능 시연 / (b) 합성 확률 대체 = t4g.small ~월 $19 | ✅ **확정 (a) — 실서버 유지** (2026-07-11, 사용자). 이력서 데모에서 AI 진단이 실제로 도는 걸 보여주는 값어치 채택 |
| **D2** | TLS 방식 | (a) Let's Encrypt+certbot(nginx 종단, 무료, 90일 자동갱신) / (b) ACM(단일 EC2엔 ALB 필요 → 비용↑) | ⏳ **다음** — a 유력(단일 EC2 nginx 종단) |
| **D3** | MySQL 배치 | (a) RDS(M4 database.tf 재사용, 프리티어 db.t3.micro) / (b) EC2 로컬 MySQL(비용↓, 관리↑) | ⏳ |
| **D4** | 인스턴스 아키 | x86 t3.medium(~월 $37) vs ARM t4g.medium(4GB 동일, ~월 $31, 재빌드 필요) | ⏳ — D1=a 로 **4GB 하한 고정**, 남은 건 x86/ARM 비용 ~$6/월 차이뿐 |

> **D1 확정(a·실서버 유지)** → RAM 4GB 하한 고정(JVM+MySQL+Redis+TF Serving 공존). 인스턴스는 t3.medium 기준선, D4(ARM 전환 ~$6/월 절감)만 남은 소소한 선택. **다음 = D2(TLS)·D3(RDS vs 로컬).**

---

## 8. 분석 메모 (Analyze-Before-Change)

`[TODO: 착수 시 /analyze-before-change]`. 예비 점검 대상:
- `infra/terraform/*` 상시화 diff 의 참조 지점(EIP·SG·인스턴스 타입 변경 영향)
- `web/nginx.conf` 443 블록 추가가 기존 80 서빙·SPA 라우팅에 미치는 영향
- 상시 공개로 `SecurityConfig` 공개 엔드포인트(permitAll)·시크릿 주입 재점검
- 롤백 시나리오(§상위 마일스톤 롤백 안전망 표)

---

## 9. 리스크·의존성 레지스터

| # | 리스크 | 완화 |
|---|---|---|
| **R1** | 신규 프리티어 계정 **6개월 자동 종료** → 링크 사망 | 취업 타임라인 내 수용(백로그 §3). 만료일 캘린더·유료 전환 판단점 사전 표시 |
| **R2** | 상시 공개 = **보안 표면 확대**(SG·인증·시크릿) | SG 최소 개방(443/80 만 공개, 22 내 IP), permitAll 재점검, 시크릿 비커밋 |
| **R3** | **destroy 규율 역전** — 실수로 down/destroy 시 링크 사망 | run-log destroy 훅 제거, SessionEnd teardown 금지(루트 CLAUDE.md 토글 비대칭) |
| **R4** | 도메인 DNS **타 계정** — 접근/전파 지연/오설정 | 낮은 TTL 로 시작, spec-02 사람 체크리스트, `dig` 검증 |
| **R5** | **TLS 갱신** 실패(Let's Encrypt 90일) | certbot 자동갱신 크론 확인, 갱신 실패 알림 |
| **R6** | **크레딧 소진 속도** 오판 → 예상외 과금/조기 종료 | AWS Budgets 알림(50/25/10%), 스택 사이징 보수적 |
| **R7** | 린 스택 **RAM 압박**(D1=a 확정 → JVM+MySQL+Redis+TF Serving 공존) | **t3.medium 4GB 하한 고정**, swap 확보. 실측 압박 시 D3=b(로컬 MySQL 제외 RDS 분리)로 EC2 RAM 확보 |
| **R8** | 단일 인스턴스 **SPOF** | 데모용 수용(비범위). 재기동 절차만 문서화 |

---

## 참조

- 상위: [`milestone-6-production-deploy.md`](../../milestones/milestone-6-production-deploy.md)
- 소비 대상 M4 spec-01: [`../m4/spec-01-zero-downtime-deployment.md`](../m4/spec-01-zero-downtime-deployment.md)
- 사람 핸드오프 짝: [`spec-02-human-domain-lifecycle-handoff.md`](spec-02-human-domain-lifecycle-handoff.md)
- 비용·수명주기·도메인 정본: [`../../backlog/production-deploy-live-resume-link.md`](../../backlog/production-deploy-live-resume-link.md)
