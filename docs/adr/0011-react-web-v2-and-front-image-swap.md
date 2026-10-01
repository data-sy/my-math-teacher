# ADR 0011: React 프론트 v2(web-v2) 도입과 mmt-front 이미지 전환

## Status
Accepted (2026-07-18 — 사용자 사전 승인, 자기완결 빌드 런)

## Context

- M7 제품 피벗(D6 React 새로 짜기·D7 모바일 퍼스트)에 따라 프론트를 Vue(`web/`)에서 React 로 재작성했다. UX 정본 = `docs/design/v2/`, 구현 정본 = `docs/specs/m7/frontend-spec.md`(제로베이스 샌드박스 빌드 → 이 리포 정착), 스택 = Vite + React 19 + TS · react-router v7 · TanStack Query · CSS Modules · Cytoscape.js · MSW · Playwright (spec-02 T1~T5 재확정, 블라인드 평가로 확정된 틸 그로스 하이파이 포함).
- spec-01 롤백 조건("구 Vue 프론트 생존")과 M4/M6 blue-green 배포(이미지 전환 롤백)를 지키려면 구 `web/` 을 덮지 않고 **별도 이미지로 공존**해야 한다 (spec-02 T5).
- 루트 CLAUDE.md 는 compose 서비스 구성 변경에 ADR 을 요구한다.

## Decision

1. **신규 `web-v2/` 워크스페이스** — `web/` 은 런치까지 무변경 보존(롤백·라이브 자산). 런치 스왑 후 정리는 별도 Task.
2. **별도 이미지 태그** — `mmt2024/mmt-front:2.0.0` (web-v2 dist + nginx. 원문은 `mymathteacher/…` 였다 — 레지스트리 계정 오기, 아래 §정정). 구 `1.0.0`(Vue) 태그는 그대로 남아 **롤백 = 이미지 태그 되돌리기**.
3. **nginx 토폴로지 무변경** — `web-v2/nginx.conf` 는 `web/nginx.conf`(M6 정본: TLS 종단·blue-green upstream fragment·`/api/v1/`·`/oauth2/`·`/login/oauth2` 프록시·SPA fallback)를 그대로 미러. 서빙 대상 dist 만 교체.
4. **compose `mmt-front` 이미지 참조를 `2.0.0` 으로 전환** — 서비스 토폴로지(포트·의존)는 무변경. docker-compose.yml 은 gitignored(자격증명 포함)라 로컬 반영 + 본 ADR 로 기록.

### 정정 (2026-09-13 · 사용자 승인)

**레지스트리 계정 이름이 오기였다.** 본문에 `mymathteacher/…` 로 적힌 Docker Hub 계정은 실제로 **`mmt2024`** 다.

- 근거: `.github/workflows/*` 는 `${{ secrets.DOCKERHUB_USERNAME }}/mmt-*` 로 푸시하고, 그 실값이 박힌 곳이
  [`docs/handoff/scripts/deploy-front.sh`](../handoff/scripts/deploy-front.sh)(`mmt2024/mmt-front:2.0.1`)와
  [M4 런 리포트](../benchmark/milestone-4-run-report.md)(`mmt2024/mmt-backend:<sha>`)다.
- **결정 자체는 불변** — 별도 태그 + "롤백 = 태그 되돌리기" 구조는 그대로다. 바뀐 것은 **이름 표기뿐**이다.
- 같은 오기가 `docker-compose.yml`(gitignored · 로컬 반영 완료 2026-09-13)과
  구 v1 자산 `shared/docker_commands/*.sh`·`docs/specs/m6/first-deploy-runbook.md` 에도 남아 있다.
  전자는 고쳤고 **후자 둘은 손대지 않았다**(v1 스크립트는 이력 자산, m6 런북은 규범 문서 = 별도 승인)
  → [`teardown-runbook-open-items.md`](../backlog/teardown-runbook-open-items.md).
- 과거 실측 리포트(예: `docs/benchmark/m7-r2-dkt-selfreport.md`)의 `mymathteacher/…` 표기는 **그대로 둔다** — 당시 기록이다.

## Consequences

### Positive
- 롤백이 git revert 가 아닌 이미지 태그 전환 — blue-green 운영 규율과 일치.
- 구 Vue 자산 무손상 — spec-01 의 "구 경로 무변경 = 롤백 대상" 성립 유지.
- 프록시·TLS 토폴로지 미러라 배포 레이어 검증 부담 최소 (로컬 docker 로 SPA fallback + `/api` 프록시 → 실백엔드 200 확인 완료, 2026-07-18).

### Negative
- nginx.conf 가 두 벌(web·web-v2) — 갈라지면 안 되는 파일. 런치 스왑 후 구 web 정리 때 한 벌로 회귀.
- 프로덕션 EC2 반영은 별도 배포 작업(이미지 push + compose/배포 스크립트의 태그 갱신) — 본 ADR 범위 밖.

### Neutral
- `web/CLAUDE.md` 전면 재작성·`web-v2/CLAUDE.md` 신설은 런치 스왑 시점(거버넌스 문서 — 사용자 승인 경유).
