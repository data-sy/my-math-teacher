# [백로그] README 포트폴리오 검수 — 잔여 2건

> 등록 2026-08-07 · 출처 = README 포트폴리오 검수(면접관 페르소나), 리포트 [`docs/consulting/out/06-readme-portfolio-review.md`](../consulting/out/06-readme-portfolio-review.md)
> 검수 권장안은 2026-08-05 README 재작성으로 대부분 반영 완료. 본 문서는 **그때 반영하지 못한 잔여**만 추적한다.
> ⚠️ 이 목록을 README 본문에 TODO 로 적지 않는다 — README 의 제1 독자는 면접관이고, "아직 안 한 것" 목록은 그 자체가 감점 신호다(검수 리포트 C-5 "개발 중" 항목과 같은 종류).

## 무엇

| # | 항목 | 성격 | 상태 |
|---|---|---|---|
| ~~1~~ | ~~제품 스크린샷 확보 → README 삽입~~ | 자산 생성 | ✅ **완료 (2026-08-13)** — `e2e/capture.spec.ts`(390px mock)로 캡처, 그래프·문답·결과 3장을 `docs/assets/` 에 두고 「서비스 소개」에 삽입 |
| ~~2~~ | ~~ADR-0008 Status 승격~~ | 거버넌스 | ✅ **완료 (2026-08-13)** — 사용자 승인 후 `Proposed` → `Accepted`, 승격 근거(M4 라이브 e2e) 명시 |
| 3 | **GitHub 레포 description 갱신** | 외부 표면 | 미착수 (즉시 가능 — 사용자 확인 필요) |
| 4 | **Postman API 명세에 M7 진단 경로 반영** | 외부 문서 | 미착수 (Postman 워크스페이스 접근 필요) |

## 왜 지금 안 하나

- **3·4** — 작업 자체는 작지만 README 재작성 범위 밖(레포 설정·외부 SaaS)이라 같이 처리하지 않았다. 둘 다 README 본문 밖 표면이라 면접관이 README 를 읽는 경험에는 영향이 적다(레포 description 은 레포 페이지 상단이라 3 이 4 보다 우선).

## 착수 시 할 일

**3. 레포 description**
- 현재: `수학 취약점 진단 및 맞춤학습을 제공하는 AI 튜터링 서비스. Spring Boot 3 + Vue 3 모노레포, v2 에서 Neo4j → MySQL 재귀 CTE 로 그래프 탐색 통합`
- `Vue 3` 이 stale(현재 프론트 = React `web-v2/`). 레포 페이지 상단이라 README 보다 먼저 읽히는 표면이다.
- 제안: `수학 지식그래프 기반 자가진단 AI 튜터링 서비스 (1인 개발). Spring Boot 3 + React 모노레포 · Neo4j → MySQL 재귀 CTE 통합 · 단일 EC2 blue-green 무중단 배포`
- `gh repo edit --description "..."` 또는 레포 설정 UI. **외부 표면 변경이라 사용자 확인 후 실행.**

**4. Postman 명세**
- 미반영 경로 8개 = `POST /api/v1/diagnosis/{frontier,next,preview}` · `POST /api/v1/diagnosis` · `GET /api/v1/diagnosis/{userTestId}` · `POST /api/v1/learning-queues` · `GET /api/v1/learning-queues/me` · `PATCH /api/v1/learning-queues/{queueId}/items/{queueItemId}/done`
- 계약 정본 = `docs/specs/m7/spec-01-diagnosis-self-report-dkt.md` §4.6(에러 계약 포함).
- 반영 전까지 README 의 Postman 링크에는 `*(M7 자가진단 경로는 미반영)*` 각주가 달려 있다 — 반영 후 각주 제거.

## 관련

- DNS `www` A레코드는 **여기가 아니라** 재런치 런북 [`🤖-M7-인프라-티어다운-재런치.md`](../handoff/🤖-M7-인프라-티어다운-재런치.md) 소관.
- 스크린샷 재캡처가 필요해지면(UI 변경 시) `SHOT_DIR=... npx playwright test e2e/capture.spec.ts` — 390px mock 로 14장 생성, 그중 3장을 `docs/assets/` 로 교체.
