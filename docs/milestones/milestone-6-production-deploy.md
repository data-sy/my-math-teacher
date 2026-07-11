# Milestone 6: 프로덕션 상시 배포 (Always-On Production Deploy — 이력서 라이브 링크)

> **상태: 🟢 라이브 — `https://www.my-math-teacher.com` 상시 서빙(step4·5 완주, 2026-07-11).**
> M4 의 blue-green 배포 메커니즘을 *상시(always-on)* 로 전환해 공개 데모 링크를 세웠다. 스택 확정·코드/IaC·
> DNS·TLS·시크릿 로테이션 6단위·OAuth 콘솔 등록·AWS Budgets 완료. 잔여 = 진단(TF Serving) end-to-end
> 시각검증 + 백로그 §9/§10 비차단 후속. 정본 진행상태는 백로그 [§9](../backlog/production-deploy-live-resume-link.md).

**브랜치 정책:** spec 단위 분할(M2·M4 관습). 현재 진행 브랜치 `feat/m6-spec-01-production-deploy`.
첫 커밋 = 백로그·roadmap docs(`2e6fecd`). 이후 설계(spec-01/02) → 구현을 이 브랜치에서 이어가되,
구현 PR 분할은 착수 시 결정.

**예상 소요:** 설계(spec-01/02) 0.5~1일 · 상시 프로비저닝 0.5일 · 도메인/TLS(사람 핸드오프) 0.5일 · 검증 0.5일
**의존성:**
- **M4(완료)** — blue-green 메커니즘·`deploy/switch-backend.sh`·`infra/terraform/*`·`run-log.sh`·헬스 엔드포인트를 **재사용**한다. M6 는 이것들을 처음부터 설계하지 않고 *소비*한다.
- **M2(완료)** — CTE-only(`mmt.migration.use-mysql-cte-for-graph`)로 Neo4j 없이 실서버 구성하는 전제.
**위험 수준:** **중간~높음** — 실과금 + 비가역(도메인/DNS·상시 계정 결제) + 상시 공개(보안 표면 확대) + 신규 프리티어 계정 6개월 자동 종료라는 수명주기 제약. 단 앱 로직 변경은 거의 없음(배포·인프라·DNS·TLS 레이어).
**선행 조건(비가역 사람 핸드오프):** 스택 확정(§결정 D1) · 도메인 DNS 접근(소유=**타 AWS 계정**) · 상시 계정 결제/수명주기 수용. → **spec-02(👤 사람 핸드오프)** 로 분리.

---

## 목표

M4 에서 증명한 무중단 배포 능력을 **한 번 띄우고 내리는 측정용**에서 **공개 프로덕션 상시 운영**으로
전환한다. 산출물은 이력서에 거는 라이브 데모 링크 `https://www.my-math-teacher.com` 하나.

구체적으로 (M4 가 §비범위로 빼뒀던 것들을 이번에 한다):
- **상시화** — `terraform apply` 후 destroy 하지 않고 유지(Elastic IP 고정). M4 의 "측정 후 destroy" 규율을 **의도적으로 역전**.
- **도메인 연결** — 타 계정 소유 `my-math-teacher.com` 을 이관 없이 DNS 위임(A 레코드 → EIP)으로 연결.
- **TLS/HTTPS** — 공개 링크이므로 인증서 필수(M4 §비범위였음).
- **수명주기·비용 관리** — 신규 프리티어 $200 크레딧·6개월 자동 종료를 감안한 스택 사이징·예산 알림.

대상은 M4 와 동일하게 **단일 EC2 위 백엔드+프론트 서빙**. 오토스케일링·멀티 인스턴스·K8s·상시 관측성은 범위 밖(§비범위).

---

## 왜 새 마일스톤인가 (M4 확장이 아니라)

- **M4 는 봉인된 완결 유닛.** 완료(2026-07-06)·PR [#45](https://github.com/data-sy/my-math-teacher/pull/45) 머지·main `4706398`, "in-place 60.3% → blue-green 0%" 측정 서사가 ADR 0007/0008 로 닫혀 있다. 상시화·도메인·TLS 를 여기 끼워넣으면 그 측정 서사가 흐려진다.
- **관심사가 다르다.** M4 = 무중단 *컷오버 메커니즘*(원샷 측정). M6 = 그 능력의 *상시 프로덕션 운영* + 도메인 + TLS + 수명주기. 그리고 이 항목들은 **M4 가 §비범위에 명시적으로 배제**한 것들("HTTPS/인증서·도메인 전환")이다 → 비범위에서의 승격 = 새 마일스톤(MMT "왜 새 마일스톤인가" 논리).
- **관계 = M4 → M6(비차단·소비형).** M4 가 배포 메커니즘·IaC·측정 하네스를 주고, M6 가 그걸 상시 운영으로 소비한다. M2→M4, M4→M3 와 같은 "한 마일스톤이 다음에 입력을 주는" 꼴.

---

## 구성 spec

두 spec 으로 구성한다. spec-01 은 "상시 프로덕션을 *어떻게 세우나*"(굳는 설계), spec-02 는
"비가역 사람 작업(도메인 DNS·상시 결제·수명주기)을 *누가 손으로 하나*"(사람용 핸드오프).

### spec-01: 상시 프로덕션 배포 기반 — [`specs/m6/spec-01-always-on-production-deploy.md`](../specs/m6/spec-01-always-on-production-deploy.md)

- M4 자산 재사용 인벤토리(무엇을 그대로 쓰고, 무엇을 상시용으로 바꾸나)
- 스택 확정(§결정 D1): 린(Neo4j 끄고 CTE) — ✅ **TF Serving 실서빙 유지 = 4GB(t3.medium 기준선)** 확정(2026-07-11). 남은 결정 = D2(TLS)·D3(RDS vs 로컬)·D4(x86/ARM)
- 상시 프로비저닝: `infra/terraform/*` 를 destroy 없는 apply 로, EIP 고정, `run-log.sh` 의 destroy 단계 스킵
- 도메인/DNS 연결 설계(타 계정 호스팅 영역 → EIP), TLS 방식 결정(Let's Encrypt vs ACM)
- **§검증**: 외부에서 `https://www.my-math-teacher.com` 접속 + 핵심 플로우(그래프·진단) 라이브 확인
- **§리스크 레지스터** R1~ (수명주기·보안 표면·destroy 역전·DNS 전파·TLS 갱신)

### spec-02 (👤 사람 핸드오프): 도메인·상시 계정·수명주기 — [`specs/m6/spec-02-human-domain-lifecycle-handoff.md`](../specs/m6/spec-02-human-domain-lifecycle-handoff.md)

- 🔄 사람이 손으로 수행하는 비가역 작업 체크리스트(M4 spec-04 패턴 재사용)
- 타 계정 Route53 호스팅 영역에 `www` A 레코드 → EIP (등록 이관 없음)
- 상시 계정 결제/플랜 수용, AWS Budgets 예산 알림, 크레딧 만료일 캘린더
- TLS 인증서 발급·갱신 사람 개입 지점

---

## 롤백 안전망

| 단계 | 롤백 방법 | 소요 |
|---|---|---|
| 배포 컷오버 실패 | M4 blue-green flip-back(`deploy/switch-backend.sh` + `nginx -s reload`) 그대로 | 1분 |
| DNS 오설정 | 타 계정 호스팅 영역에서 A 레코드 되돌림(TTL 낮게 시작) | TTL 만큼 |
| TLS 발급 실패 | HTTP(80) 로 임시 서빙 유지, 인증서 재발급 | 즉시~시간 |
| 상시 인스턴스 이상 | 마지막 정상 이미지 태그로 재배포(M4 워크플로) | ~10분 |
| 상시화 자체 철회 | `terraform destroy`(단 링크 사망 — 신중), 또는 인스턴스 stop | 즉시 |

---

## 완료 기준 (초안)

- [ ] spec-01/02 설계 작성·커밋
- [x] 스택 확정(§결정 D1~D4) — ✅ **x86 t3.medium 4GB · TF Serving 실서빙 · RDS 분리 · Let's Encrypt TLS** (2026-07-11)
- [x] 코드/IaC 준비 — ✅ **instance_type bump·web api.js same-origin(ADR 0009)·nginx 443 TLS** 커밋(2026-07-11). §8 Analyze-Before-Change 완료. 실 apply·인증서·DNS·RDS 시드는 spec-02 사람 핸드오프
- [x] 상시 프로비저닝(destroy 없는 apply) + EIP 고정 — ✅ step4·5 완주(2026-07-11)
- [x] 도메인 DNS 연결(타 계정 `www` A → EIP) — ✅ 외부 해석 확인(라이브)
- [x] TLS/HTTPS 적용 — ✅ `https://www.my-math-teacher.com` 접속
- [~] 공개 링크 검증 — 그래프(CTE)·SPA·API ✅ 실증 / **진단(TF Serving) end-to-end 미검증** (OAuth 등록 완료로 unblock, 백로그 §9)
- [x] AWS Budgets 예산 알림 — ✅ 완료(2026-07-11). 크레딧 만료일 관리 잔여(§9)
- [x] OAuth redirect-uri 3콘솔 등록(§8) — ✅ 소셜 로그인 연동 완료(2026-07-11)
- [ ] 필요 시 ADR(상시화·TLS 방식) 작성 — same-origin 은 ADR 0009
- [x] roadmap 에 M6 상태 갱신 — ✅ 라이브+로테이션 반영

---

## 비범위 (의도적으로 이번에 안 하는 것)

- **상시 관측성·알림** — 그건 **M5**(Grafana/Prometheus). M6 는 배포·도메인·TLS·수명주기까지만.
- 오토스케일링·멀티 인스턴스·로드밸런서·K8s/ECS (단일 EC2 유지 = 데모용 SPOF 수용)
- CI 자동배포 고도화(M4 `workflow_dispatch` 수준 재사용)
- 무중단 DB 스키마 마이그레이션
- 도메인 등록 **이관**(기존 계정 유지 + DNS 위임으로 충분 — 백로그 §4)
- Neo4j 코드·인프라 실삭제 → **M3**

---

## 참조

- **M4**(소비 대상): [`milestone-4-zero-downtime-deployment.md`](milestone-4-zero-downtime-deployment.md) · ADR [0007](../adr/0007-blue-green-zero-downtime-deployment.md)/[0008](../adr/0008-m4-ci-deploy-channel-ssh-to-ssm-run-command.md)
- 백로그(비용·6개월 종료·도메인 결정 정본): [`../backlog/production-deploy-live-resume-link.md`](../backlog/production-deploy-live-resume-link.md)
- M4 사람 핸드오프 선례(spec-02 패턴): [`../specs/m4/spec-04-human-aws-provisioning-handoff.md`](../specs/m4/spec-04-human-aws-provisioning-handoff.md)
- 재사용 자산: `infra/terraform/*` · `deploy/switch-backend.sh` · `run-log.sh` · `web/nginx.conf` · `HealthController.java`
