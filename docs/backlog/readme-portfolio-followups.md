# [백로그] README 포트폴리오 검수 — 잔여 4건

> 등록 2026-08-07 · 출처 = README 포트폴리오 검수(면접관 페르소나), 리포트 [`docs/consulting/out/06-readme-portfolio-review.md`](../consulting/out/06-readme-portfolio-review.md)
> 검수 권장안은 2026-08-05 README 재작성으로 대부분 반영 완료. 본 문서는 **그때 반영하지 못한 잔여**만 추적한다.
> ⚠️ 이 목록을 README 본문에 TODO 로 적지 않는다 — README 의 제1 독자는 면접관이고, "아직 안 한 것" 목록은 그 자체가 감점 신호다(검수 리포트 C-5 "개발 중" 항목과 같은 종류).

## 무엇

| # | 항목 | 성격 | 차단 요소 |
|---|---|---|---|
| 1 | **제품 스크린샷 확보 → README 삽입** | 자산 생성 | web-v2 캡처가 리포에 없음 |
| 2 | **ADR-0008 Status `Proposed` → `Accepted` 승격** | 거버넌스 | 사용자 승인 필요 |
| 3 | **GitHub 레포 description 갱신** | 외부 표면 | 없음 (즉시 가능) |
| 4 | **Postman API 명세에 M7 진단 경로 반영** | 외부 문서 | Postman 워크스페이스 접근 |

## 왜 지금 안 하나

- **1** — 구 velog 이미지 3장(12색 그래프 GIF·삭제된 결과 표·Neo4j 현역 아키텍처)은 stale 이 확인돼 **제거만** 했다. 대체 자산을 새로 캡처해야 한다. *(2026-08-13 갱신: 프로덕션이 다시 라이브(HTTP 200)라 "어디서 캡처하나" 결정은 해소됨 — **라이브 사이트에서 캡처**하면 된다. 착수 가능.)*
- **2** — ADR 은 데이터면·거버넌스 문서라 하네스가 자의로 상태를 바꾸지 않는다(루트 CLAUDE.md 규칙). 실제로는 M4 에서 구현·라이브 e2e 검증까지 끝났으므로 승격이 사실에 맞다.
- **3·4** — 작업 자체는 작지만 README 재작성 범위 밖(레포 설정·외부 SaaS)이라 같이 처리하지 않았다.

## 착수 시 할 일

**1. 제품 스크린샷**
- 후보 화면 3종: ① 개념 지식그래프(A안 색 층위 — 서비스 차별점) ② 자가진단 문답 ③ 결과 = 시급도 카드 + 학습 큐.
- 캡처 경로: `cd web-v2 && npm run dev`(mock) 또는 실서버 모드. 실기기 폭(모바일 퍼스트) 캡처가 제품 의도에 더 맞다.
- 저장 위치는 velog 외부 호스팅 말고 **리포 안**(`shared/diagram/` 또는 `docs/assets/`) — 외부 CDN 은 이번 stale 사고의 원인 중 하나였다.
- 삽입 지점: README 「서비스 소개」 3단계 불릿 옆.

**2. ADR-0008 승격**
- `docs/adr/0008-m4-ci-deploy-channel-ssh-to-ssm-run-command.md` §Status → `Accepted (2026-07-06 — M4 라이브 e2e 검증 완료)`.
- 근거: [`docs/benchmark/milestone-4-run-report.md`](../benchmark/milestone-4-run-report.md) 의 SSM→runuser→blue-green 컷오버 완주 기록.
- README 하이라이트 3 이 이 ADR 을 "핵심 의사결정 기록" 으로 인용 중 — Proposed 상태면 면접관이 링크를 열었을 때 "제안 단계를 성과로 인용" 으로 읽힌다.

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

- DNS `www` A레코드 갱신은 **여기가 아니라** 재런치 런북 [`🤖-M7-인프라-티어다운-재런치.md`](../handoff/🤖-M7-인프라-티어다운-재런치.md) §5-1 소관.
