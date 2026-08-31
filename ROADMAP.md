# MMT Roadmap

**이 문서는 인덱스다.** 무엇을 하고 있고 다음이 무엇인지만 둔다 —
과정·이력은 링크된 정본 문서와 git 히스토리에 있다. 여기 옮겨 적지 않는다.

> ## ▶ 다음 세션은 여기서 시작 (2026-08-31 갱신)
>
> 🔴 **[AMI 필터 지뢰](docs/backlog/ami-filter-picks-minimal-no-ssm-agent.md)가 실측으로 확인됐다.**
> 지금 `terraform apply`(전체)를 돌리면 **프로덕션 EC2 와 EIP 연결이 교체된다**
> (`Plan: 2 to add, 0 to change, 2 to destroy`). 다음 인프라 변경의 **선행조건**이다.
>
> **문서 1순위 — [`docs/handoff/🤖-문서-구조-정돈.md`](docs/handoff/🤖-문서-구조-정돈.md)** (문서 배치·이름·진입동선)
> ⚠️ **답을 정해두지 않은 프롬프트다.** 열린 결정 6건(archive 처리·폴더명·외부 독자 가중치 등)을
> **사용자와 대화로 정한 뒤** 착수한다. 혼자 판단해 파일을 옮기지 말 것.
>
> **이어서 가능 — CI 이식성 잔여**(테스트 4개 클래스). 선행 조건 = 3306·6379 를 다른 도커 스택이
> 비워야 검증이 된다. 절차·결정거리 = [`test-suite-not-portable-to-ci.md`](docs/backlog/test-suite-not-portable-to-ci.md) §다음 세션은 여기서부터

---

## 현재 상태

| | |
|---|---|
| 서비스 | 🟢 라이브 — https://www.my-math-teacher.com |
| 프론트 | React [`web-v2/`](web-v2/CLAUDE.md) (`mmt-front:2.0.2`) — 구 Vue [`web/`](web/CLAUDE.md) 는 롤백 자산으로만 보존 |
| 백엔드 | Spring Boot 3.1 · 그래프 탐색 = MySQL 재귀 CTE(Neo4j 미구동) · 시급도 = DKT on TF Serving |
| 인프라 | 단일 EC2 blue-green + RDS · CD = GitHub Actions → SSM Run Command |
| ⚠️ 알려진 구멍 | **AMI 필터 지뢰 — 전체 `terraform apply` 가 프로덕션 EC2 를 교체한다(실측 2026-08-31)** · CI 테스트 게이트가 꺼져 있다(`skip_tests=true`) · SSH 인그레스가 내 IP 고정 |

---

## Now — 진행 중

- **[M7] 제품 피벗 — 자가진단 + React 재작성** — 🟢 **라이브. 남은 것은 폴리싱·백로그뿐.**
  - 문제 풀 실부재로 진단이 막혀 **제품을 피벗**했다: 문제풀이 → **self-report OX 자가진단**,
    학습지 출제 → **그래프 학습 경로 + 개념 링크**, Vue → **React 새로 작성**(마이그레이션 아님), 모바일 퍼스트.
  - 정본: [milestone-7](docs/milestones/milestone-7-product-pivot.md) · [PRD](docs/prd/m7-prd.md) ·
    [specs/m7](docs/specs/m7/) · **화면 계약 = [`docs/design/v2/`](docs/design/v2/)**(`00-flow-map.html` 이 전이 SSOT)
  - ⚠️ **승인 대기 1건:** 홈 인증 행 상시 노출이 `01-home.html` ●7 계약을 깬다 — 3안 비교 제시 완료, 사용자 결정 필요

- **[M8] 개념 학습자료 링크** — 🚧 **1차 시드까지 라이브**(`links 0→3` 실측). **커버리지 10/1,631 개념.**
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
| 🟡 테스트 CI 이식성 | 전 스위트가 CI 에서 성공한 적이 없다. **원인 A·B 해결, C 미착수** — 프로파일 미지정 4개 클래스도 남음 | [파일](docs/backlog/test-suite-not-portable-to-ci.md) |
| AMI 필터 지뢰 | `al2023-ami-*` 가 minimal 을 집어 SSM 부재. **`most_recent=true` + `lifecycle` 부재라 전체 apply 가 프로덕션 EC2 를 교체할 수 있다** | [파일](docs/backlog/ami-filter-picks-minimal-no-ssm-agent.md) |
| SSH → SSM Session Manager | 인그레스가 `my_ip/32` 라 IP 바뀔 때마다 배포가 막힌다. 선행 = SSM 등록 정상화(순환 의존) | [파일](docs/backlog/ssh-ingress-ip-pinning-to-session-manager.md) |

### 그 외

- **프론트(web-v2) 폴리싱** — [그래프 요약 칩](docs/backlog/m7-graph-summary-chips.md) ·
  [스테일 큐 첫 탭 403](docs/backlog/m7-stale-queue-403-first-tap.md) ·
  [완주 세션 재프리뷰](docs/backlog/m7-result-completed-session-repreview.md) ·
  [홈 완료 배너 재진단](docs/backlog/m7-home-completed-banner-rediagnosis-cta.md) ·
  [적응 순회 문항 선택](docs/backlog/m7-adaptive-traversal-question-selection.md) ·
  [카피 방향](docs/backlog/m7-copy-direction-highschool-persona.md)(열린 결정, 현행 유지)
- **데이터·백엔드** — [지식그래프 상호 선수 사이클 26쌍](docs/backlog/knowledge-space-mutual-prerequisite-cycles.md) ·
  로컬 DB 초기화 시드 정본 부재 · 샘플 진단 depth-0 행 누락 · `RedisUtil` value serializer 격리 ·
  Testcontainers Redis `@ServiceConnection`(Spring Boot 3.2+ 의존)
- **운영·문서** — [README 포트폴리오 잔여 2건](docs/backlog/readme-portfolio-followups.md)(레포 description·Postman) ·
  [진단 테스트 계정 정리](docs/backlog/m7-diagnostic-test-accounts-cleanup.md) ·
  `mmt-terraform-admin` 세션 8h 연장 · M4 측정 하네스(`run-log.sh`)를 별도 레포로 이관(cross-repo)
- **[GTM] 실사용자 확보 컨설팅 1회** — 격리 세션용 [프롬프트](docs/consulting/🤖-user-acquisition-consulting-prompt-draft.md) 준비됨(draft, 브리핑 최신화 전제)

> 구 Vue(`web/`) 시절의 UI 백로그와 학습지 출제 알고리즘 항목들은 **M7 피벗으로 무효**가 되어 정리했다.
> 필요하면 git 히스토리(이 문서의 2026-08-26 이전 판)에서 되살린다.

---

## Done — 완료

| | 무엇 | 결과 |
|---|---|---|
| **[Ops]** 2026-08-31 | RDS MySQL 8.0 → 8.4 업그레이드 | Extended Support 과금 종료 — 8월 gross **$146.77**(전체 usage 의 72.7%, 만근 $175/월). 크레딧 소진으로 9월부터 전액 카드 청구였다. 다운타임 ~6분 ([백로그](docs/backlog/rds-mysql-8-0-extended-support-billing.md)) |
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
