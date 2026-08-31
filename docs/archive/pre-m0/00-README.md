# pre-m0 아카이브 — M0 착수 이전의 사전조사 자료

> **성격: 이력 보존. 새 작업 아님 · 현행 상태의 근거 아님.**
> 여기 있는 문서는 전부 **2025-12-12 ~ 2026-03-31**, 즉 리포 커밋이 멈춰 있던 공백기에
> 리포 밖에서 만든 사전조사물이다. 이후 M0(Claude Code 통합, 문서 최초 커밋 2026-04-24)부터
> 지금까지의 결정은 이 문서들을 **대체**했다 — 아래 "현행 정본"을 신뢰할 것.

**왜 리포에 넣었나:** 다른 어디에도 사본이 없는 유일본이고(2026-09-01 홈 전체 해시 대조 확인),
"M0 를 무슨 근거로 시작했나"의 사료 가치가 있어서. 2026-09-01 `~/Downloads/mmt/`(옵시디언 볼트)
정리 때 편입했다.

## 무엇이 있나

| 파일 | 작성 | 내용 | 현행 정본 (이걸 대신 본다) |
|---|---|---|---|
| `mmt-migration-analysis-ko.md` / `-en.md` | 2026-01 / 04 | JdbcTemplate→JPA · Neo4j→MySQL CTE 마이그레이션 사전분석(11장: 기술부채·DDD 권고·위험평가·단계 계획·기간 추정) | M2 로 실행 완료 → [`docs/reports/m2-cte-migration.md`](../../reports/m2-cte-migration.md) · [milestone-2](../../milestones/milestone-2-neo4j-to-mysql-cte.md) · M3 그래프 인프라 폐기 |
| `mmt_code_review.md` | 2025-12-12 | 백엔드 API 코드리뷰(당시 Java 112파일·약 4,373줄 기준) | **코드베이스가 달라져 대조 불가.** 현행 규약 = 각 `CLAUDE.md` · [`docs/DEVELOPMENT.md`](../../DEVELOPMENT.md) |
| `mmt_priority_list.md` | 2025-12-12 | 위 리뷰의 P0~P4 개선 우선순위 목록 | 활성 작업 정본 = [`docs/backlog/`](../../backlog) · `ROADMAP.md` |
| `claude-md-draft-en.md` / `-ko.md` | 2025-12-12 | M0 이전의 `CLAUDE.md` 초안(en/ko) | 현행 = 리포 루트 · `api/` · `web/` · `web-v2/` 의 `CLAUDE.md` 4종 |
| `docker-memory-stats.md` | 2026-03-31 | 컨테이너 6종 메모리·CPU 스냅샷(Neo4j 306MiB 포함) | Neo4j 는 M3 에서 폐기됨 → 현행 실측 = [`docs/benchmark/`](../../benchmark) |

## 파일명 주의

`claude-md-draft-*.md` 는 원본 파일명이 `CLAUDE.md` / `CLAUDE_ko.md` 였다.
**그대로 두면 Claude Code 가 이 디렉토리에서 작업할 때 구버전 지침을 자동 로드**하므로 개명했다.
내용은 손대지 않았다 — 이 폴더의 문서는 전부 당시 작성분 그대로다.
