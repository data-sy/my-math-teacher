# Spec 01: 상시 프로덕션 배포 기반 (Always-On Production Deploy)

**상위 마일스톤:** Milestone 6 (프로덕션 상시 배포 — 이력서 라이브 링크)
**대상 Phase:** Phase 1 (상시화 + 도메인 + TLS 토대)
**선행 spec:** M4 spec-01(무중단 배포 기반, 완료) — 본 spec 은 그 메커니즘을 *소비*한다
**후속:** spec-02(👤 사람 핸드오프 — 도메인 DNS·상시 결제·수명주기)

> ⚠️ **상태: 인프라 apply 완료(2026-07-11).** §1·§2·§7 채움 완료. **D1·D2·D3·D4 전부 확정**
> (D4=x86 t3.medium). §8 Analyze-Before-Change 완료 → 3건 커밋(instance_type bump·
> web api.js same-origin(ADR 0009)·nginx 443 TLS). **`terraform apply` 완료: 18 added, 0 destroyed.**
> EIP=`54.116.29.102`(association 완료·재기동해도 고정)·RDS=`mmt-db.c7qu444ug8bf.ap-northeast-2.rds.amazonaws.com:3306`·
> EC2=`i-0eb170169ac70ee05`. **잔여 = 사람 핸드오프(spec-02): RDS 시드·인증서 발급·DNS.**
> 실비용·공개노출·데이터면 작업이라 사람이 트리거한다.

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
| Terraform IaC | `infra/terraform/{compute,database,network,iam,provider,variables}.tf` | 상시용으로 조정(destroy 안 함, EIP 고정). 리전 `ap-northeast-2`(`provider.tf:27`), 루트볼륨 gp3 30GB(`variables.tf:53`). 상시화 diff = 인스턴스 타입 bump(§5-1)·apply 착수 시 |
| tfvars/state | `infra/terraform/terraform.tfvars` · `terraform.tfstate` | **현재 state 빈 껍데기**(`resources: []`, serial 237) = M4 destroy 후 잔여 0 검증됨. `tfstate.backup` 에 직전 M4 세대 전체(`aws_instance`·`aws_db_instance` db.t3.micro·`aws_eip` 등) 잔존 = 참조 인벤토리. tfvars 3줄: `use_localstack=false`·`my_ip=27.1.27.65`·`db_password` |
| 배포 스크립트 | `deploy/switch-backend.sh` | 그대로 재사용(blue-green 컷오버) |
| 측정 하네스 | `infra/terraform/run-log.sh` | destroy 단계 스킵하고 상시 측정에 재사용. destroy 는 `cmd_tf_destroy()`(라인 86-93) → `tf-destroy` 서브커맨드로만 발동(자동 훅 아님) = 상시 모드 = tf-destroy 미호출로 충분, 코드 제거 불필요 |
| 프론트 nginx | `web/nginx.conf` | 현재 **80 단독 listen, TLS 없음**. SPA try_files·blue-green upstream 프록시 존재 → 443 listen + Let's Encrypt 인증서 블록 신규 추가 지점 |
| 헬스 | `api/.../controller/HealthController.java` | 그대로(컷오버 게이트). permitAll = `GET /api/v1/health`(정확매칭, `SecurityConfig.java:82`) |
| CI 워크플로 | `.github/workflows/api-ci-cd-with-ec2.yml` | 그대로(수동 dispatch) |

### 1.2 상시화가 바꾸는 전제

- **destroy 규율 역전** — M4 는 측정 후 `terraform destroy` 가 안전이었다. M6 는 destroy = 링크 사망. `run-log.sh` 의 destroy 훅·SessionEnd teardown 류 금지. **경로 식별됨**: `cmd_tf_destroy()`(`run-log.sh:86-93`)가 `tf-destroy` 서브커맨드로만 발동(자동 훅 없음) → 상시 모드는 이 서브커맨드를 안 부르면 됨.
- **공개 노출 상시화** — SG·인증·시크릿·rate limit 표면이 상시 열림. **재점검됨**: SG 인그레스 = 80·443 공개(0.0.0.0/0), 22 내IP만(`network.tf:33-58`), 8080 규칙 부재. permitAll 공개면 = catalog(chapters/concepts/tests, 정답 미포함)·sample 체험·auth·oauth2(`SecurityConfig.java:74-96`), 정답 포함 items/detail·validation 은 의도적 보호. R2 최소개방 전제 성립.
- **비용이 시간에 비례** — 원샷이 아니라 월 단위. **확인됨**(백로그 §2): 린 스택 t3.medium 4GB ~$37/월(ARM t4g.medium ~$31), + Public IPv4 ~$3.6·EBS 30GB ~$2.4·Route53 $0.5. 신규계정 $200 크레딧+6개월 무료, 크레딧 남아도 6개월 자동종료(§3, R1).

---

## 2. 타깃 아키텍처

> `[TODO: 착수 시 다이어그램/구성 확정]`. 스케치:

```
사용자 → (443/TLS) → 타 계정 Route53 (www A → EIP)
                        → EC2(EIP 고정, ap-northeast-2)
                            nginx(443 TLS 종단, 80→443 리다이렉트)
                              → blue/green 백엔드(switch-backend.sh 컷오버)
                              → 정적 SPA(web/dist)
                            MySQL(RDS db.t3.micro, EC2 밖 분리=§7 D3 확정 a) · Redis(로컬) · TF Serving(실서빙 유지=§7 D1 확정 a)
                            Neo4j 미구동(CTE-only, mmt.migration.use-mysql-cte-for-graph=true)
```

- 도메인 등록은 **타 계정 유지**(이관 없음). DNS 위임만 — 등록/호스팅 영역이 다른 계정이어도 정상 동작.
- TLS: **Let's Encrypt(certbot, nginx 443 종단, 90일 자동갱신) 확정(§7 D2 a)**. SG 443 이미 개방·nginx 현재 80 단독 → 443 listen + 인증서 블록 신규 추가.

---

## 3. 변경할 파일과 셋업 단계

**A. 코드/IaC 변경 (완료 — 2026-07-11 커밋)**

| # | 변경 | 파일 | 커밋 |
|---|---|---|---|
| A1 | instance_type 기본값 `t3.micro`→`t3.medium`(D4=x86, 4GB 하한 R7) | `infra/terraform/variables.tf` | `b765ee0` |
| A2 | web API baseURL prod=same-origin(`window.location.origin`), dev=localhost 유지 + ADR 0009 | `web/src/composables/api.js`·`docs/adr/0009-*` | `07254da` |
| A3 | nginx 443 TLS 종단 + 80→443 리다이렉트(ACME 챌린지 예외) | `web/nginx.conf` | `119c870` |

**B. 사람 핸드오프 (spec-02 — 실비용·공개노출·데이터면, 순서 중요)**

1. `terraform apply`(destroy 없음, `use_localstack=false`) → EC2·EIP·RDS·SG 생성, EIP 고정 확인
   - ⚠️ apply 전 `terraform.tfvars` 의 `my_ip`(SSH 22 인바운드) 현재 IP 로 갱신 — stale 시 SSH 잠김
   - **plan 사전검증 완료(2026-07-11): `Plan: 18 to add, 0 to change, 0 to destroy`.** instance_type=`t3.medium`(A1)·AMI al2023 x86_64(D4)·`aws_eip`+association(EIP 고정)·RDS `db.t3.micro`(D3)·SG 80/443 공개+3306 app-SG 한정+22 단일IP(R2) 전부 plan 상 확인. `my_ip`=27.1.27.65 는 apply 시점 실 공인 IP 와 일치 확인(갱신 불요)
   - ✅ **apply 완료(2026-07-11): `18 added, 0 changed, 0 destroyed`(RDS 5m17s).** EIP=`54.116.29.102`(assoc `eipassoc-082cf1b65b0c6e1e0`)·EC2=`i-0eb170169ac70ee05`·RDS=`mmt-db.c7qu444ug8bf.ap-northeast-2.rds.amazonaws.com:3306`. tfstate/tfvars gitignore 확인(RDS 비번 비유출)
2. **RDS 시드** — 신규 RDS 는 빈 DB. **시드 소스 = (b) in-repo `api/sql/` 스크립트로 확정**(2026-07-11):
   `ddl-auto: none` 이라 `create.sql` 이 스키마 정본이고, concepts(`_latex`)·chapters·knowledge_space·items·
   diag·인덱스가 전부 in-repo → 운영 덤프(PII·외부접근) 불요. 접속=SSM 포트포워딩(RDS 비공개 유지·SSH 키 불요).
   **FK-safe 로드 순서·item_id 정합 리스크·실증 절차는 별도 런북**: [`rds-seed-runbook.md`](rds-seed-runbook.md).
   이게 없으면 §4 "그래프·진단 라이브"가 빈 DB 로 실패.
3. **DNS 먼저** — 타 계정 호스팅 영역에 `www` A → EIP `54.116.29.102` (낮은 TTL 로 시작, R4) → `dig` 전파 확인.
   ⚠️ **원안 순서 3(인증서)↔4(DNS) 역전 정정(2026-07-11):** certbot HTTP-01 은 도메인→EIP 해석에 의존하므로
   인증서 발급보다 **선행**해야 검증이 통과한다.
4. **인증서 발급** — DNS 전파 후. 닭-달걀 주의: 전체 nginx.conf(80+443)는 인증서 파일 부재 시 `nginx -t` 실패 →
   (a) `certbot certonly --standalone -d www.my-math-teacher.com`(nginx 잠깐 내리고 발급) 또는
   (b) 80-only 임시 conf 로 `/var/www/certbot` 서빙 후 `--webroot` 발급.
5. 발급 성공 후 전체 nginx.conf(80+443) 적용 + 443 포트 노출 + `/etc/letsencrypt`·`/var/www/certbot` 볼륨 마운트 → reload
6. `run-log.sh` 상시 모드(`tf-destroy` 미호출)로 기동 측정
7. AWS Budgets 예산 알림 + 크레딧 만료일 관리(R1·R6)

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
| **D2** | TLS 방식 | (a) Let's Encrypt+certbot(nginx 종단, 무료, 90일 자동갱신) / (b) ACM(단일 EC2엔 ALB 필요 → 비용↑) | ✅ **확정 (a) — Let's Encrypt+certbot** (2026-07-11, 사용자). SG 443 이미 개방·nginx 80 단독 listen 확인 → 443 블록 신규 추가만 하면 M4 nginx 종단 blue-green 구조 그대로. ACM=ALB(~$16/월)는 그 컷오버 메커니즘을 갈아엎어 §0 "M4 재사용" 전제 위배 |
| **D3** | MySQL 배치 | (a) RDS(M4 database.tf 재사용, 프리티어 db.t3.micro) / (b) EC2 로컬 MySQL(비용↓, 관리↑) | ✅ **확정 (a) — RDS** (2026-07-11, 사용자). M4 `tfstate.backup` 에 `aws_db_instance`(db.t3.micro) 실존 → `database.tf` 가 이미 RDS 프로비저닝 = 재사용. DB를 EC2 밖으로 빼 R7(4GB RAM 경합) 완화. 신규계정 6개월 종료(R1)가 RDS 프리티어 12개월보다 먼저 와 만료 비용 무의미 |
| **D4** | 인스턴스 아키 | x86 t3.medium(~월 $37) vs ARM t4g.medium(4GB 동일, ~월 $31, 재빌드 필요) | ✅ **확정 (a) — x86 t3.medium** (2026-07-11, 사용자 "진행해"). §8 분석에서 ARM 은 비용 -$6/월이나 `compute.tf` AMI 필터(`al2023-ami-*-x86_64`)·compose 플러그인 URL(`docker-compose-linux-x86_64`) 2곳 재배선 리스크 확인 → x86 은 IaC 재배선 0. $6/월 < 재배선 리스크로 x86 채택. `variables.tf` 기본값 bump 완료(커밋 `b765ee0`) |

> **D1·D2·D3·D4 전부 확정** (2026-07-11) → 스택 골격 잠금: 단일 EC2(**x86 t3.medium 4GB**, TF Serving 실서빙) + RDS(db.t3.micro, DB 분리로 R7 완화) + nginx 443 Let's Encrypt TLS 종단, blue-green 컷오버는 M4 그대로. **잔여 결정 없음** — §8 Analyze-Before-Change 완료, 코드/IaC 3건 커밋. 실 apply·인증서·DNS·RDS 시드는 사람 핸드오프(spec-02).

---

## 8. 분석 메모 (Analyze-Before-Change) — 완료 2026-07-11

**영향받는 테스트:** 없음. 변경 대상(terraform HCL·nginx.conf·api.js 상수)을 커버하는 테스트 부재
(web 테스트 프레임워크 없음, terraform 테스트 없음). SecurityConfig 무변경.
**스키마·마이그레이션:** 스키마 변경 0. 단 신규 RDS 는 빈 DB → *데이터 이관*(§3-B2) 필요(스키마 아님).

- **`infra/terraform/*`** — 현 `tfstate` 빈 껍데기 → apply 는 diff 가 아니라 M4 스택 from-scratch 재생성.
  EIP 는 이미 `compute.tf`(`aws_eip.app`+association)에 존재 → "고정"은 destroy 안 하면 자동 달성(추가 코드 0).
  변경은 instance_type bump 1건뿐(A1). ARM 미채택 근거=위 D4. `my_ip` stale 주의(§3-B1).
- **`web/nginx.conf` 443** — 기존 80 단독을 80(ACME+리다이렉트)/443(TLS+프록시)로 분리. 보존 확인:
  upstream include(blue-green 컷오버 핵심)·3 proxy location·Host 헤더·SPA try_files 그대로 이전(A3).
  certbot 순서 함정 2건 문서화(인증서 선발급 / 갱신 경로 80 보존) → §3-B3·nginx.conf 주석.
- **`SecurityConfig` 재점검 — 코드 변경 불필요.** permitAll 표면(catalog·sample·auth 진입·oauth2·health)은
  정답 미포함, 정답 포함(items/tests-detail/validation)은 `anyRequest().authenticated()` 보호 → R2 최소개방 성립.
  CORS origins=`${EC2_DOMAIN_NAME1/2}` env → 배포 시 주입(코드 아님). 백엔드는 이미 prod 도메인 정렬
  (`application-secure.yml` redirect-uri·OAuth success handler).
- **교차 워크스페이스 블로커(스펙 원안 §8 범위 밖, 분석서 발굴):**
  ① `api.js` baseURL=`localhost:8080` 하드코딩 → https 에서 mixed-content 로 §4 라이브 플로우 차단.
     → prod same-origin 전환으로 해소(A2/ADR 0009). ② 신규 RDS 빈 DB → 시드 필요(§3-B2, 사람).
- **롤백:** 상위 마일스톤 안전망 표 그대로. 개별: A1→직전 타입 재apply / A2·A3→git revert 후 재빌드 /
  컷오버 실패→`switch-backend.sh` flip-back(1분) / TLS 실패→80 임시 유지. 전면 철회 destroy=링크 사망(신중).

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
