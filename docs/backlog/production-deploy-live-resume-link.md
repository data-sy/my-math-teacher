# Backlog: 서버 올리기 — 프로덕션 상시 배포 (이력서 라이브 링크)

**분류:** [Infra/GTM] — 백로그
**관련:** M4 spec-01 §9(프리티어 프로비저닝), [M4 spec-03 Terraform IaC](../specs/m4/spec-03-terraform-plan-only-iac-sandbox.md), M4 `run-log.sh` 하네스
**상태:** 📌 **미착수 — 검토 단계.** M4 에서 `apply→측정→destroy` 사이클로 라이브를 여러 번 띄웠다 내렸으므로 **프로비저닝 능력은 이미 있음**. 이 항목은 그걸 *상시(always-on)* 로 전환해 이력서에 걸 **공개 링크**로 만드는 것.

> 목적: 채용용 라이브 데모 링크 확보. `https://www.my-math-teacher.com` 을 신규 AWS 계정의 EC2 한 대에 상시 서빙.

> 🟡 **[2026-07-11 발견→추가검증] 시크릿 유출 프레이밍 하향.** 초기엔 `application-secure.yml`이 라이브 OAuth/JWT/DB/Redis를 유출한 것으로 봤으나, 3중 검증(플레이스홀더 판별·해시 대조·전체 히스토리 피카axe)으로 **현재 라이브 시크릿은 공개 히스토리에 없음** 확인 — 추적되던 파일은 `${ENV}` 플레이스홀더였고 실제 값은 gitignored `docker-compose.yml`/박스 env-file에만 존재(커밋 0). 공개된 건 이미 대체된 옛 리터럴 3종뿐. 🤖 추적 해제·gitignore 완료(`f7d26ee`). **결정: 방어심화로 전면 로테이션 진행(👤, 긴급도=위생 수준).** 운영 소스는 `application-secure.yml`이 아니라 env-file/compose임에 유의. 절차 정본: [`../specs/m6/oauth-and-secret-rotation-runbook.md`](../specs/m6/oauth-and-secret-rotation-runbook.md) — §8 OAuth redirect-uri 등록과 통합.
>
> **진행(2026-07-11):** **전 6단위(Google·Naver·Kakao·JWT·Redis·RDS) ✅ 완료.** 각 단위 새 값 발급 → 박스 env-file 주입(값 무노출, 해시 대조) → 백엔드(및 Redis) 재생성 → 검증(브라우저 로그인·PING·HikariPool 연결) 완주. 상세·잔여·재생성 명령 = 런북 **§1-B**.

---

## 1. 왜 백로그인가

M4 의 라이브 배포는 **측정 후 destroy** 가 규율이었다(비용·프리티어 소진 관리). 상시 배포는 그 반대로 **destroy 하지 않고 유지**하는 거라, 비용·수명주기·도메인이 새 결정 축으로 붙는다. 무중단 배포(M4)·관측성(M5)의 차단 요소는 아니고, **취업 활동 타임라인에 종속**된 선택 항목이라 백로그.

## 2. 비용 요약 (2026-07 기준, 조사분)

트래픽이 아니라 **서비스 RAM 합**이 인스턴스를 정한다(리뷰어는 가끔 방문 → 트래픽 요금 ≈ 0). M2 에서 Neo4j→MySQL CTE 이전이 끝났으므로(`mmt.migration.use-mysql-cte-for-graph`) 데모 스택은 줄일 수 있다.

| 구성 | 인스턴스 | RAM | 인스턴스/월 | 총액/월(+IPv4·EBS·R53) |
|---|---|---|---|---|
| 풀 스택(Neo4j+TF Serving) | t3.large | 8GB | $60.7 | ~$67 |
| **린(Neo4j 끄고 CTE, TF Serving 유지)** ⭐ | t3.medium / t4g.medium | 4GB | $30.4 / ~$24.5 | ~$37 / ~$31 |
| 최소(Neo4j+TF Serving 둘 다 제외, 합성 확률) | t4g.small | 2GB | ~$12.3 | ~$19 (JVM+MySQL 빡빡, swap) |

부대 비용: **Public IPv4 ~$3.6/월**(실행 중 인스턴스에 붙어 있어도 과금, 2024-02~) · EBS 30GB gp3 ~$2.4/월 · Route53 호스팅 영역 $0.5/월 · 데이터전송 out 월 100GB 무료.

## 3. ⚠️ 6개월 자동 종료 (신규 프리티어의 진짜 제약)

2025-07-15 이후 신규 계정은 **$200 크레딧 + 6개월 무료 플랜**. **크레딧이 남아도 가입 6개월이 되면 계정이 자동으로 닫힌다**(둘 중 먼저). → 무료로 링크를 유지할 수 있는 **최대치는 6개월**. 그 안에 취업 예정이라 이 제약은 수용(사용자 판단, 2026-07-10). 이후 필요하면 유료 플랜 업그레이드(월 $30대) 또는 더 싼 상시 호스팅으로 이전.

- 남은 크레딧·만료일 확인: Billing 콘솔 → **Credits**, Cost Explorer(= M4 소진분).
- 예산 알림(AWS Budgets) 세팅 권장 — 크레딧 소진 속도 감시.

## 4. 도메인 (`my-math-teacher.com`) — 다른 AWS 계정 소유

**결론: 링크를 서빙하려고 도메인을 신규 계정으로 *이관할 필요는 없다*.** DNS 는 등록(레지스트라)과 분리돼 있어, 기존 계정에 도메인을 그대로 두고 **A 레코드만 신규 계정 EC2 의 Elastic IP 로** 가리키면 된다.

이관을 굳이 한다면(둘 다 AWS 계정이라 가능·무료):
- Route53 계정 간 **등록 이관은 무료**, 레지스트라 간 이전의 60일 락 없음(단 등록 후 14일 경과 필요). 소스 계정이 `TransferDomainToAnotherAwsAccount` 개시 → 수신 계정이 **3일 내 수락**, 미수락 시 취소.
- **호스팅 영역은 자동으로 안 따라온다**(별도 마이그레이션). 등록과 호스팅 영역이 서로 다른 계정에 있어도 DNS·등록은 정상 동작.
- ⚠️ **주의(수명주기 충돌):** 신규 계정은 6개월 후 자동 종료 대상 → 아끼는 도메인 등록을 그 계정에 넣으면 계정이 닫힐 때 도메인이 위험(연 갱신 ~$12/yr 은 프리티어와 무관). **→ 등록은 안정적인 기존 계정에 두고 DNS 위임만 하는 편이 안전.** 이관은 취업 후 계정 정리 단계에서 재검토.

## 5. 착수 시 단계 스케치 (apply 단계에서 확정)

1. 스택 결정: 린(t3.medium/t4g.medium) + Neo4j 끄고 CTE 플래그. (사용자 미결: TF Serving 유지 vs 합성 확률 — §2 표의 t3.medium vs t4g.small 트레이드오프)
2. 프로비저닝: M4 Terraform config 재사용, **destroy 없이 상시**(run-log 의 destroy 단계 스킵). Elastic IP 고정.
3. 시드: [Infra/Data] 로컬 DB 초기화(시드) 백로그의 정본 시드 재사용(M4 R4 와 동일 경로).
4. DNS: 기존 계정 호스팅 영역의 `www` A 레코드 → EC2 EIP. TLS(Let's Encrypt/ACM).
5. 예산 알림 + 크레딧 만료일 캘린더 표시.

## 6. 경계

- **teardown 규율 역전:** M4 는 "측정 후 destroy" 가 안전이었지만 여기선 destroy 하면 링크가 죽는다. SessionEnd 훅 류의 무조건 teardown 금지(루트 CLAUDE.md 환경 토글 비대칭 규율과 정합).
- 시크릿(RDS 비번·OAuth·JWT)은 HCL/state/커밋에 평문 금지 — 비커밋 `*.tfvars`/환경변수만.
- 리전 ap-northeast-2(서울) 유지(M4 와 동일).

## 7. 이월 — concepts LaTeX 시드 충실도 (임시 수정 적용됨, 제대로 된 수정 필요)

**배경(2026-07-11):** RDS 시드 시 `api/sql/insert_concepts_latex.sql` 만 LaTeX 수식을 담는데, 이 파일은
단일 백슬래시 LaTeX(`\begin`·`\frac`·`\to`)와 이스케이프-요구 시퀀스(`\'` 138건·`\n` 3453건)를
**한 파일에 섞어** 어떤 단일 MySQL 로드 모드로도 온전히 안 들어간다(기본모드=LaTeX 깨짐, NO_BACKSLASH_ESCAPES=`\'` 문법에러).
원본 운영 DB 는 앱/드라이버 파라미터 바인딩 경로로 적재됐을 가능성. → `mysql < file` 직로드는 근본적으로 불충분.

**적용한 임시 수정:** n(소문자) 제외 문자로 시작하는 명령의 백슬래시만 이중화하는 sed 변환
(`s/\\([a-mo-zA-Z])/\\\\\1/g`) 후 concepts 만 기본모드 재로드. → `\frac`·`\sqrt`·`\times`·`\to`·`\begin`
등 대다수 명령 복원 확인(concept 5762·3 스팟체크). concepts 외 테이블·item_id 정합 무영향.

**남은 흠(제대로 된 수정 대상):**
- **n-시작 명령**(`\ne`≠ 124건·`\ni`·`\nabla` 류 ~150건)은 `\n`(줄바꿈)과 구분 불가라 미변환 → 여전히 깨짐.
- cases 행 구분자 `\\`(원본 `\\\`)가 기본모드에서 단일 `\` 로 축약 → 줄바꿈 렌더 흠 가능.
- **근본 해결 방향:** (a) 원본 운영 DB 덤프의 concepts.description ground-truth 확보, 또는
  (b) 앱/스크립트 파라미터 바인딩으로 재적재(문자열 리터럴 보존), 또는 (c) 프론트 `VMarkdownView` 가
  기대하는 정확한 인코딩 확인 후 그에 맞춘 결정적 변환 작성. 이력서 데모엔 그래프·진단이 핵심이라 현 임시본 수용,
  실제 수식 렌더 품질이 문제되면 위 (a)~(c) 로 정식 수정.
- 정본 절차·현 상태: [`../specs/m6/rds-seed-runbook.md`](../specs/m6/rds-seed-runbook.md).

## 8. ✅ 완료 — OAuth redirect-uri 프로덕션 콘솔 등록 (소셜 로그인 활성화용)

> ✅ **완료(2026-07-11).** 세 provider(Google·Naver·Kakao) 콘솔에 프로덕션 redirect-uri 등록 완료 → 소셜 로그인 연동 동작. 아래는 기록 보존.

**배경(2026-07-11):** 최초배포 시 백엔드 `application-secure.yml` 은 OAuth redirect-uri 를
`https://www.my-math-teacher.com/login/oauth2/code/{google,naver,kakao}` 로 고정한다. 이 URI 가
각 provider **개발자 콘솔에 등록**돼 있어야 소셜 로그인이 실제로 성공한다.

- **사이트 기동엔 무영향** — 카탈로그·개념 그래프(CTE)·진단(TF Serving)·페이지 서빙은 OAuth 등록과 무관하게 동작한다. 영향 범위는 **사용자가 소셜 로그인 버튼을 클릭한 그 순간의 인증 플로우뿐**(미등록 시 provider 가 redirect_uri mismatch 로 그 로그인만 거부).
- **왜 이월:** provider 콘솔(Google Cloud·Naver Developers·Kakao Developers)은 외부 계정 로그인이 필요해 어시스턴트가 대신 못 함 = 👤 사람 전용. 이력서 데모 핵심(그래프·진단)이 아니라 비차단으로 이월.
- **할 일(👤):** 세 콘솔 각각에 위 3개 redirect-uri 등록(또는 v1 프로드 도메인과 동일하면 확인만). 등록 후 재배포 불요 — 런타임 검증이라 즉시 반영.
- 연관: [`../specs/m6/first-deploy-runbook.md`](../specs/m6/first-deploy-runbook.md) 시크릿·env-file 절.

## 9. 이월 — 라이브 후 잔여 (2026-07-11, 링크 라이브 완료 시점의 비차단 항목)

`https://www.my-math-teacher.com` 는 라이브(step4·5 완주). 아래는 링크를 막지 않는 후속 — 정본 맥락은 [`../specs/m6/first-deploy-runbook.md`](../specs/m6/first-deploy-runbook.md) §실행 결과.

- **TF Serving 진단 플로우 시각검증** 🟢 unblock — 개념 그래프(CTE)·SPA·API 는 실증됐으나 **진단 결과(AI 확률, D1 "실서빙 유지" 값어치)**는 아직 end-to-end 미검증. §8 OAuth 콘솔 등록 **완료(2026-07-11)** 로 선행조건 해소 → 실브라우저 로그인 후 진단 1회 돌려 `mmt-ai:8501` 왕복 확인만 남음. **현 시점 M6 유일한 실질 잔여 검증 항목.**
- **CI 정상 무중단배포 정합** — 최초는 손기동이라 무관하나, `switch-backend.sh`(blue↔green) 쓰기 전:
  ① 레포 변수 `COMPOSE_NET` = `ec2-user_default` → **`mmt-net`** 으로 변경(현 손기동 네트워크와 일치),
  ② 박스에 `deploy/switch-backend.sh`·`active-backend.conf`(rw 마운트) 배치,
  ③ 백엔드 이미지 계정 = **`mmt2024`**(org 아님) 인지 IMAGE_REPO 정합 확인.
- **front Dockerfile `npm ci` 전환** — 박스 `npm install`+node:14 가 vue3-markdown 최신본(`dist/style.css` export 제거)을 끌어와 `vite build` 실패했음. lockfile(1.1.9) 준수하도록 `web/Dockerfile` 을 `npm install`→`npm ci`(주석 이미 존재) 로 바꾸거나 front CI 로 빌드. (현 라이브 front 는 로컬빌드 dist→최소 nginx 이미지로 우회 중.)
- **step 6 상시 측정** — `run-log.sh` 상시 모드(`tf-destroy` 미호출)로 기동·docker-stats 스냅샷, 크레딧 소진 기준선.
- **step 7 AWS Budgets** ✅ **완료(2026-07-11)** — 예산 알림 세팅 완료. 신규계정 6개월 자동종료(R1)·크레딧 만료 캘린더는 사용자 수명주기 관리로 이관(§3).

## 10. 이월 — 시크릿 로테이션 후속 위생 (2026-07-11, 로테이션 완주 후 비긴급)

전 6단위 로테이션(런북 §1-B)은 완료. 아래는 방어심화 후속 — 라이브·차단 아님, 👤 콘솔/판단 필요.

- **Google 옛 OAuth 클라이언트 삭제(👤) — 보류(2026-07-11 사용자 결정)**. Google 로테이션은 *새 계정·프로젝트·클라이언트 신규*로 했으므로 옛 클라이언트가 여전히 존재·유효. **저위험**(앱 미사용·유저 0)이나 무위험은 아님(옛 secret 과거 공개분이면 앱 사칭 OAuth 플로우 여지). 사용자가 **옛 계정을 기억 못 해 보류**. **복구 경로:** git 히스토리의 옛 `GOOGLE_CLIENT_ID`(초기 커밋 `application-secure.yml`/`docker-compose.yml`)로 프로젝트 번호 역추적 → 해당 Google 계정 식별. 착수 시 Google Cloud Console → 옛 프로젝트 → 사용자 인증 정보 → 클라이언트 삭제(또는 사용중지).
- **RDS least-privilege 앱 유저 도입** — 현재 앱이 RDS **마스터 유저 `mmtadmin`으로 직결**. 최소권한 앱 전용 유저(필요 DB/테이블 GRANT만) 생성 후 앱 전환이 정석. **마스터 비번 로테이션과는 별개**(로테이션은 완료). 스키마/권한 변경이라 착수 시 `/analyze-before-change` + 롤백 시나리오 필수.
