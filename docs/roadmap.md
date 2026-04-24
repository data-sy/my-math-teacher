# MMT Roadmap

MMT 프로젝트의 중장기 작업 계획. 세부 실행 지시는 각 마일스톤·spec 문서를 참조.

---

## Now — 진행 중

- **[M0] Claude Code 통합 환경 구축** — [milestone](milestones/milestone-0-claude-code-integration.md)
  - AI 없이 개발되어 온 레포에 Claude Code 워크플로우·가드레일 도입
- **[M1] 테스트 인프라 및 기준선 구축** — 마일스톤 문서 작성 예정 (`docs/milestones/milestone-1-test-infrastructure.md`)
  - Testcontainers 기반 통합 테스트, 성능 기준선, 피처 플래그

---

## Next — 다음 분기 (착수 예정)

- **[M2] Neo4j → MySQL CTE 마이그레이션**
  - 그래프 탐색 쿼리를 MySQL 재귀 CTE로 이전
  - Neo4j Reactive 제거, 단일 RDB 운영으로 인프라 단순화
  - M1의 기준선·피처 플래그 위에서 진행
- **[Epic] JdbcTemplate → JPA 전환**
  - 레거시 JdbcTemplate 기반 코드를 JPA로 점진적 마이그레이션
  - 레포지토리 단위로 쪼개어 복수 마일스톤으로 분할 예정

---

## Later — 백로그 (아직 미착수, 검토 단계)

- DKT 모델 서빙 파이프라인 재검토 (현재 TensorFlow Serving 고정)
- 프론트엔드(`web/`) 상태 관리·빌드 시스템 현대화
- CI/CD 파이프라인 정비 및 배포 자동화
- 모니터링·알림 체계 구축 (현재 Grafana+Prometheus 기반 확장)
- `shared/` 내부 구조 정리 (`diagrams/`, `scripts/`, `data/` 분리 — 필요시)

---

## Done — 완료

_v2(2025.02~) 진행 중. 아직 이 브랜치에서 완료된 마일스톤 없음. 완료 시 커밋 해시·완료일과 함께 기록._

---

## Epic 및 마일스톤 분할 원칙

- **Roadmap** — 하고 싶은 모든 작업의 단일 인덱스 (이 문서)
- **Epic** — 여러 마일스톤을 묶는 큰 주제 (예: JPA 전환). 커지면 `docs/epics/` 하위로 분리
- **Milestone** — 시간·완료 상태가 있는 체크포인트 (`docs/milestones/`)
- **Spec** — Claude Code 실행 지시 (`docs/specs/`)
- **ADR** — 돌이킬 수 없는 의사결정 기록 (`docs/adr/`)

## 갱신 규칙

- 마일스톤 착수 시 Now로 이동
- 마일스톤 완료 시 Done으로 이동, 커밋 해시·완료일 기록
- 새 아이디어는 Later에 먼저 추가하고, 우선순위가 올라가면 Next로 승격
