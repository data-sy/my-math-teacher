# MMT Roadmap

**이 문서는 인덱스다.** 무엇을 하고 있고 다음이 무엇인지만 둔다 —
과정·이력은 링크된 정본 문서와 git 히스토리에 있다. 여기 옮겨 적지 않는다.

> ## ⏸️ 이 프로젝트는 휴면 중이다 — mothball 2026-09-08 (기간 미정 — 1년 이상 비울 수 있다)
>
> **프로덕션 인프라는 존재하지 않는다.** 2026-09-08 `terraform destroy` 로 EC2·EIP·RDS·SG·
> IAM role/OIDC/instance profile·keypair 를 **전량** 파괴했다. `https://www.my-math-teacher.com` 은
> 뜨지 않고 DNS A레코드도 지웠다 — **고장이 아니라 의도다.** 사유 = 학습 프로젝트를 세워두는 비용
> (8월 gross **$217** — 서울 리전 사용 $207.65 + 세금 $9.30 + 예산 $0.23. RDS Extended Support 사건 포함)이
> 휴면 중 얻는 것보다 컸다.
>
> ### 다시 켜려면
> [`docs/handoff/🤖-M7-인프라-티어다운-재런치.md`](docs/handoff/🤖-M7-인프라-티어다운-재런치.md) **§3 재런치 런북** 하나만 따라간다.
> 요지 세 줄:
> 1. `terraform apply` — 코드가 인프라를 재생성한다(IAM/OIDC 포함. **CI 배포는 그전까지 실패한다**)
> 2. **DNS 는 타 AWS 계정 Route53** — `www` A레코드를 새 EIP 로 손수 만든다(TLS 발급이 DNS 선행을 요구)
> 3. 호스트 상태(env·인증서·nginx·이미지)는 IaC 밖 — 런북 §3-5 로 손으로 복원
>
> ⚠️ **RDS 스냅샷은 없다 — 데이터 원천 = `api/sql` 시드.** 마지막 스냅샷 `mmt-mothball-2026-09-08` 을
> 2026-10-02 에 삭제했다(사용자 결정 — 개발 공간이라 프로덕션 유저·진단 기록은 버린다). 계정의 RDS 스냅샷은 **0개**다.
> `infra/terraform/database.tf` 에서 `snapshot_identifier` 를 걷어냈으므로 `apply` 는 **빈 `mmt-db`(MySQL 8.4)** 를 만들고,
> 데이터는 런북 §3 의 시드 적재 단계로 채운다. ⚠️ 이 경로는 **아직 실증된 적이 없다**(문서·`terraform validate` 까지만).
>
> ### 휴면 중에도 할 수 있는 일 (검증이 로컬·GitHub 러너에서 닫히는 것들)
> - **[Ops] 휴면 정리 3종** — 클라우드·로컬 잔여 / 로컬 재현성 / 런북 §5. **지금 Now 의 유일한 활성 항목**(아래 Now 첫 블록)
> - **[자바/Spring Boot 상향](docs/backlog/java-17-lts-upgrade.md)** — CI `tests_only` 로 인프라 없이 게이트만 돌릴 수 있다
> - **[문서 구조 정돈](docs/handoff/🤖-문서-구조-정돈.md)** — ⚠️ 열린 결정 D1'·D2~D6, 사용자와 대화로 정한 뒤 착수 (D1 = 소비 완료 핸드오프 삭제는 2026-10-02 에 실행됨)
> - **프론트 폴리싱** — `web-v2` mock 모드(`npm run dev`)면 백엔드 없이 화면 작업 가능
>
> ### 계정에 남겨둔 것 (2026-10-02 실측 · 전 리전)
> **과금 리소스 0.** 시간당 과금 0 · RDS 스냅샷 0 · S3/ECR/Secrets/KMS/로그그룹 0 · 서울 외 16개 리전도 0.
> 남은 것은 $0 짜리뿐이다 — 예산 3개 · CloudWatch 청구 알람 2개 · IAM role `mmt-terraform-admin`.
> 청구 알람은 **일부러 살려 뒀다** — 휴면 중 이상 과금을 알아채는 유일한 경로다.
>
> ### ⚠️ 오래 비웠다 돌아왔다면 먼저 확인할 2가지
> 1. **도메인이 끊겼을 수 있다.** `my-math-teacher.com` 등록 만료일 = **2027-06-17**(whois 2026-10-01). 등록·호스팅 영역이
>    **타 AWS 계정**이다. **도메인은 계속 보유할 생각이고 자동갱신이 켜져 있을 것으로 기억한다 — 다만 그 계정 콘솔에서 확인한 적은 없다.**
>    자동갱신이 맞다면 매년 그 계정에 갱신비가 청구된다. 꺼져 있었다면 그 날짜 이후 도메인이 남의 것이 됐을 수 있으니
>    `whois my-math-teacher.com | grep -i expir` 부터 본다. 사라졌다면 재런치 런북의 DNS·TLS·OAuth redirect-uri 가 전부 새 이름 기준으로 바뀐다.
> 2. **AWS CLI 키가 꺼져 있다.** 장기 부재 대비로 IAM 사용자 `mmt-cli` 의 액세스 키를 **비활성화**해 뒀다(2026-10-02 결정).
>    `aws sts get-caller-identity --profile mmt-base` 가 `InvalidClientTokenId` 로 실패하면 그 상태다 →
>    AWS 콘솔(루트/콘솔 로그인) → IAM → Users → `mmt-cli` → Security credentials → 액세스 키 **Activate**.
>    켜기 전에는 `.local/mmt-aws-session.sh`·`mmt-admin`·terraform 이 전부 인증 실패한다.

---

## 현재 상태

| | |
|---|---|
| 서비스 | ⏸️ **휴면** (2026-09-08~) — 인프라 없음. 코드·시드·절차는 보존, 프로덕션 DB 는 폐기(2026-10-02) |
| 프론트 | React [`web-v2/`](web-v2/CLAUDE.md) — 마지막 배포 `mmt-front:2.0.2`. 구 Vue [`web/`](web/CLAUDE.md) 는 롤백 자산으로만 보존 |
| 백엔드 | Java 17 · Spring Boot 3.1.6 · 그래프 탐색 = MySQL 재귀 CTE(Neo4j 미구동) · 시급도 = DKT on TF Serving |
| 인프라 | **파괴됨.** 정의는 `infra/terraform/` 에 그대로 — 재런치는 `apply`(빈 RDS) + 런북 §3 |
| 데이터 | 정본 = `api/sql` 시드(개념·지식그래프·진단 문항). RDS 스냅샷은 없다 — 프로덕션 유저·진단 기록은 2026-10-02 폐기 |
| ⚠️ 재런치 때 되살아나는 구멍 | SSH 인그레스 `my_ip/32` 고정 · OS 패치 알림이 호스트 motd 안에만 머묾 · TLS 자동갱신은 호스트와 함께 소멸(재등록 필요) |

---

## Now — 진행 중

> ⏸️ **마일스톤(M7·M8·M5)은 휴면으로 전부 멈춰 있다** — 아래 마일스톤 항목은 "재개하면 여기서부터"라는 뜻이지
> 지금 굴러가는 일이 아니다. **예외 = 바로 아래 [Ops] 휴면 정리**로, 인프라 없이 닫히므로 지금 굴러간다.

- **[Ops] 휴면 정리 — 클라우드·로컬·런북 잔여** — ⏸️ **중단**(2026-09-09 착수 · 2026-10-02 클라우드 쪽 닫음). 남은 것은 로컬 한 바퀴 복구와 코드 스멜 1건이다.
  - **재개 진입점 = [`docs/handoff/🤖-휴면-정리-이어서.md`](docs/handoff/🤖-휴면-정리-이어서.md)** — 브랜치 `ops/mothball-local-cleanup-2026-09`.
    2026-10-02 까지의 변경은 [#65](https://github.com/data-sy/my-math-teacher/pull/65) 로 올렸다(`git log main..HEAD` 가 비어 있지 않으면 미머지). 받을 결정 = `specs/m6` 표기 정정 여부
  - **[클라우드·로컬 잔여](docs/backlog/mothball-residual-cleanup-cloud-and-local.md)** — ✅ 클라우드 닫힘(2026-10-02): 과금 리소스 0 · 마지막 RDS 스냅샷 삭제 · 도메인 만료 2027-06-17. 로컬 도커 찌꺼기도 0(실측)
  - **[로컬 재현성](docs/backlog/local-dev-env-reproducibility-after-mothball.md)** — 볼륨·이미지 전부 소멸 → 다음 기동은 시드 재적재부터. ✅ `ai/savedmodel`(DKT 6.7MB)은 `acc72f4` 부터 **이미 git 추적 중**(2026-09-13 확인) — 남은 건 재빌드 실증
  - **[런북 §5 열린 항목](docs/backlog/teardown-runbook-open-items.md)** — 휴면 중 가능/재런치 대기/승인 필요로 재분류. ✅ 네이밍 정정 완료([ADR-0011 §정정](docs/adr/0011-react-web-v2-and-front-image-swap.md)+compose). 남은 승인 = `specs/m6` 런북 표기

- **[M7] 제품 피벗 — 자가진단 + React 재작성** — ✅ **제품으로 완성됨** (2026-08-06~09-08 라이브 운영). 남은 것은 폴리싱·백로그뿐.
  - 문제 풀 실부재로 진단이 막혀 **제품을 피벗**했다: 문제풀이 → **self-report OX 자가진단**,
    학습지 출제 → **그래프 학습 경로 + 개념 링크**, Vue → **React 새로 작성**(마이그레이션 아님), 모바일 퍼스트.
  - 정본: [milestone-7](docs/milestones/milestone-7-product-pivot.md) · [PRD](docs/prd/m7-prd.md) ·
    [specs/m7](docs/specs/m7/) · **화면 계약 = [`docs/design/v2/`](docs/design/v2/)**(`00-flow-map.html` 이 전이 SSOT)
  - ⚠️ **승인 대기 1건:** 홈 인증 행 상시 노출이 `01-home.html` ●7 계약을 깬다 — 3안 비교 제시 완료, 사용자 결정 필요

- **[M8] 개념 학습자료 링크** — 🚧 **1차 시드까지 배포 완료**(라이브 `links 0→3` 실측, 2026-08-15). **커버리지 10/1,631 개념.** 프로덕션 DB 는 폐기됐고(2026-10-02) 링크 정본은 `shared/data/concept-links-seed.csv` 다 — 재런치 때 다시 적재한다.
  - 정본: [spec-03](docs/specs/m7/spec-03-learning-path-links.md)
  - 남은 것 ① 2차 시드 = 초등 병목 상위 10 ([왜](docs/backlog/concept-links-miss-the-students-who-need-them.md) — 기초가 무너진
    학생일수록 카드가 초등으로 내려가 링크를 못 받는다) ② 링크 생존 점검 재설계
    ([HTTP 200 이 위장 green](docs/backlog/concept-links-liveness-check-false-green.md)) ③ 카드 근거 문구를 후수 개념명으로
  - ⚠️ **승인 대기:** ①②는 spec-03 수정을 동반한다(규범 문서 = 승인 후 반영)

- **[M5] 관측성 — Grafana/Prometheus 재계측** — ⬜ **파킹.** 브랜치만 있고 spec 미작성.
  M4 무중단은 이미 로그로 증명됐으므로 **차단 요소가 아니다**(관측성 학습 목적).
  [백로그](docs/backlog/observability-grafana-prometheus-for-zero-downtime.md)

---

## Next — 다음 분기

- **[M3] Neo4j 그래프 인프라 폐기** — 의사결정·체크리스트는 [문서로 정의 완료](docs/milestones/milestone-3-graph-infra-deprecation.md).
  운영 서비스가 아니므로 실수행은 선택
- **[Epic] JdbcTemplate → JPA 전환** — 리포지토리 단위로 쪼개 복수 마일스톤으로 분할 예정

---

## Later — 백로그

정본은 각 파일이다. 여기엔 "무엇이 왜 남아 있나" 한 줄만 둔다.

### ⭐ 착수 대기 상위

| 항목 | 한 줄 | 정본 |
|---|---|---|
| SSH → SSM Session Manager | 인그레스가 `my_ip/32` 라 IP 바뀔 때마다 배포가 막힌다. 선행 = SSM 등록 정상화(순환 의존) | [파일](docs/backlog/ssh-ingress-ip-pinning-to-session-manager.md) |

### 그 외

- **프론트(web-v2) 폴리싱** — [그래프 요약 칩](docs/backlog/m7-graph-summary-chips.md) ·
  [스테일 큐 첫 탭 403](docs/backlog/m7-stale-queue-403-first-tap.md) ·
  [완주 세션 재프리뷰](docs/backlog/m7-result-completed-session-repreview.md) ·
  [홈 완료 배너 재진단](docs/backlog/m7-home-completed-banner-rediagnosis-cta.md) ·
  [적응 순회 문항 선택](docs/backlog/m7-adaptive-traversal-question-selection.md) ·
  [카피 방향](docs/backlog/m7-copy-direction-highschool-persona.md)(열린 결정, 현행 유지)
- **백엔드 상향** — [자바 17 → LTS 상향](docs/backlog/java-17-lts-upgrade.md) ⏸️**휴면 중 가능**
  (Spring Boot 3.1.6 은 이미 OSS 지원 종료 → 두 축이 한 작업. 목표 조합은 착수 전 사용자 결정)
- **데이터·백엔드** — [지식그래프 상호 선수 사이클 26쌍](docs/backlog/knowledge-space-mutual-prerequisite-cycles.md) ·
  ~~로컬 DB 초기화 시드 정본 부재~~ → [로컬 재현성](docs/backlog/local-dev-env-reproducibility-after-mothball.md) 으로 흡수(Now) ·
  샘플 진단 depth-0 행 누락 · `RedisUtil` value serializer 격리 ·
  로컬 MySQL root 비번 강화(compose 초기값 그대로 — 로컬 전용이라 비차단) ·
  Testcontainers Redis `@ServiceConnection`(Spring Boot 3.2+ 의존)
- **운영·문서** — [README 포트폴리오 잔여 2건](docs/backlog/readme-portfolio-followups.md)(레포 description·Postman) ·
  `mmt-terraform-admin` 세션 8h 연장 · M4 측정 하네스(`run-log.sh`)를 별도 레포로 이관(cross-repo)
- **[GTM] 실사용자 확보 컨설팅 1회** — 격리 세션용 [프롬프트](docs/consulting/🤖-user-acquisition-consulting-prompt-draft.md) 준비됨(draft, 브리핑 최신화 전제)

> 구 Vue(`web/`) 시절의 UI 백로그와 학습지 출제 알고리즘 항목들은 **M7 피벗으로 무효**가 되어 정리했다.
> 필요하면 git 히스토리(이 문서의 2026-08-26 이전 판)에서 되살린다.

---

## Done — 완료

| | 무엇 | 결과 |
|---|---|---|
| **[Ops]** 2026-09-08 | **mothball — 인프라 전량 파괴** | 스냅샷 확보 → `terraform destroy`(18개 리소스) → DNS A레코드 삭제. 시간당 과금 0 실측. 절차·스크립트 = [런북 §4](docs/handoff/🤖-M7-인프라-티어다운-재런치.md) |
| **[Ops]** 2026-08-31 | RDS MySQL 8.0 → 8.4 업그레이드 | Extended Support 과금 종료 — 8월 gross **$146.77**(전체 usage 의 72.7%, 만근 $175/월). 크레딧 소진으로 9월부터 전액 카드 청구였다. 다운타임 ~6분 ([사건 기록](docs/incidents/2026-08-rds-extended-support.md)) |
| **[Ops]** 2026-08-31 | 운영 위생 3건 | AMI 우발 교체 차단(`ignore_changes`) ([정본](docs/backlog/ami-filter-picks-minimal-no-ssm-agent.md)) · AL2023 `releasever` 핀 해제로 거짓 "0건 패치" 계기판 수리 ([정본](docs/backlog/host-os-patching-al2023-releasever-pin.md)) · 홈 일회성 스크립트 16→1 정리(프론트 배포 스크립트를 `docs/handoff/scripts/deploy-front.sh` 로 승격) |
| **[Infra]** 2026-08-31 | 테스트 CI 이식성 | 전 스위트가 CI 에서 처음 초록(`181/0`, 4회 연속). `skip_tests` 우회 제거로 배포 게이트 복구 ([#61](https://github.com/data-sy/my-math-teacher/pull/61)) |
| **[M8]** 2026-08-15 | 개념 학습자료 링크 1차 | `concept_links` + 파일럿 10개념 26링크 라이브 ([#54](https://github.com/data-sy/my-math-teacher/pull/54)) — 2차 시드는 Now |
| **[M7]** 2026-08-06 | 자가진단 피벗 + React 재작성 | 프로덕션 프론트를 `mmt-front:2.0.2` 로 스왑 ([ADR-0011](docs/adr/0011-react-web-v2-and-front-image-swap.md)) |
| **[M6]** 2026-07-11 | 프로덕션 상시 배포 | 이력서용 라이브 링크 + TLS + OAuth 3사 + 예산 알람 ([#47](https://github.com/data-sy/my-math-teacher/pull/47)) |
| **[M4]** 2026-07-06 | 배포 무중단화 | in-place **60.3% 유실** → blue-green **0%** 실측. 부팅 JVM CPU 캡이 결정타 ([리포트](docs/benchmark/milestone-4-run-report.md)) |
| **[M2]** 2026-05 | Neo4j → MySQL 재귀 CTE | depth3 p95 14.0ms → 0.556ms (**~25배**), 결과 동등성 검증 ([리포트](docs/reports/m2-cte-migration.md)) |
| **[M1]** 2026-04-24 | 테스트 인프라·성능 기준선 | Testcontainers · N+1 감지 · 기준선 실측 · 피처 플래그 체계 |
| **[M0]** 2026-04-24 | Claude Code 통합 환경 | 계층형 CLAUDE.md · 슬래시 커맨드 · Analyze-Before-Change 가드레일 |

곁가지로 닫힌 트랙: 구 Vue 리디자인 P0·P1(토큰·셸·폼·화면 재설계) · Terraform IaC 사이클 ·
TLS 자동갱신 타이머 등록 — 전부 위 마일스톤에 흡수됐거나 백로그 파일에서 ✅ 로 닫혔다.

---

## 문서 체계

| 층 | 무엇 | 위치 |
|---|---|---|
| Roadmap | 전체 인덱스 (이 문서) | `ROADMAP.md` |
| Milestone | 시간·완료 상태가 있는 체크포인트 | `docs/milestones/` |
| Spec | 구현 지시 (규범 문서) | `docs/specs/` |
| ADR | 되돌리기 어려운 의사결정 | `docs/adr/` |
| Backlog | 미착수·부분 해소 항목의 정본 | `docs/backlog/` |
| Handoff | 세션 간 인계(소비성) | `docs/handoff/` |

## 갱신 규칙

- **진행 상태의 정본은 이 문서와 백로그 파일이다.** `CLAUDE.md` 계열에는 시점성 정보를 쓰지 않는다
- **여기엔 결과와 포인터만.** 과정·시행착오는 정본 문서에 두고, 그마저 소비되면 git 히스토리에 맡긴다
- 마일스톤이 닫히면 Now → Done 으로 옮기고 **한 줄로 줄인다**
- 새 발견은 `docs/backlog/` 에 **파일로** 만들고, 여기엔 한 줄 + 링크만 추가한다
- 운영 문서 최신화는 `/refresh-ops-docs` — spec·ADR·CLAUDE.md 는 승인 없이 고치지 않는다
