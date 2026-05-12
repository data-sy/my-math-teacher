# MMT Roadmap

MMT 프로젝트의 중장기 작업 계획. 세부 실행 지시는 각 마일스톤·spec 문서를 참조.

---

## Now — 진행 중

- **[M2] Neo4j → MySQL CTE 마이그레이션 — 검증 단계 완료** (PR [#22](https://github.com/data-sy/my-math-teacher/pull/22))
  - 그래프 탐색 쿼리를 MySQL 재귀 CTE로 이전, 결과 동등성·성능·시각화·거리 맵 의미 보존 검증 완료
  - 회수: depth 3 p95 14.034ms (Neo4j) → 0.556ms (CTE), 약 25배 개선
  - 결과 보고: `docs/reports/m2-cte-migration.md`
  - 점진 출시·관찰·폐기는 **M3** 로 분리 (포트폴리오 컨텍스트로 실제 폐기 수행은 보류 가능)

---

## Next — 다음 분기 (착수 예정)

- **[M3] 그래프 인프라 폐기 + 운영 출시 정책**
  - M2 검증된 CTE 경로를 피처 플래그 ON 으로 점진 출시, 관찰 후 Neo4j 인프라·코드 일괄 폐기
  - ADR 0006 (점진 전환 정책) + ADR 0007 (Neo4j 폐기 통합본) 작성
  - 본 프로젝트는 운영 서비스가 아니므로 실제 수행은 선택적 — 마일스톤 문서로 의사결정·체크리스트만 정의 완료
  - 문서: [`docs/milestones/milestone-3-graph-infra-deprecation.md`](milestones/milestone-3-graph-infra-deprecation.md)

- **[Epic] JdbcTemplate → JPA 전환**
  - 레거시 JdbcTemplate 기반 코드를 JPA로 점진적 마이그레이션
  - 레포지토리 단위로 쪼개어 복수 마일스톤으로 분할 예정

---

## Later — 백로그 (아직 미착수, 검토 단계)

- **[Infra] RedisUtil value serializer isolation 리팩토링** (M2 spec-02 PR [#21](https://github.com/data-sy/my-math-teacher/pull/21) 에서 발견)
  - `RedisUtil.set` 이 호출마다 `RedisTemplate.setValueSerializer` 를 클래스 단위로 교체 → 다른 호출의 deserialize 와 충돌 (`ClassCastException`). M2 spec-02 에서는 통합 테스트 cleanup (`deleteByPrefix`) 으로 우회.
  - 범위: 호출 단위 serializer isolation 또는 통합 serializer 도입. ADR 작성 필요.
  - 완료 시 `ConceptServiceFeatureFlagTest.WhenFlagTrue` 의 `@BeforeEach cleanGraphCache` 우회 코드 제거 가능.
- **[Infra] Testcontainers Redis 자동 ServiceConnection** (Spring Boot 3.2+ 업그레이드 의존)
  - M2 spec-02 에서 G2 로 검토했으나 Spring Boot 3.1.6 의 Redis `@ServiceConnection` 미지원으로 보류. 3.2+ 에서 자동 지원.
  - 범위: Spring Boot 업그레이드 후 `TestcontainersConfig` 에 Redis 컨테이너 추가, `application-test.yml` 동적 주입. 로컬 `mmt-redis` 의존 없이 CI 친화 통합 테스트.
- **[Infra] 프로파일·로깅 위생 정리** (M1 Spec 03 에서 관찰됨)
  - `application.yml` 의 `spring.profiles.include: securelocal` 을 `!test` 프로파일 조건부로 전환 → 현재 `@ActiveProfiles("test")` 만으로는 securelocal 이 배제되지 않아 테스트가 `@Import(TestcontainersConfig.class)` 강제됨 (Spec 03 작업 규칙 2 "프로파일 독립성" 미완)
  - `application-prod.yml` · `application-local.yml` 도입으로 4 프로파일 (default/local/test/prod) 체계 완성
  - `application.yml` 의 공통 `com.mmt` · `springframework.data.neo4j` · `springframework.security` DEBUG 로거를 프로덕션에서 INFO 로 하향 (M1 Spec 03 Task 3.3 에서 관찰)
  - 완료 시 `FeatureFlagIntegrationTest` 류가 Testcontainer import 없이도 기동
- DKT 모델 서빙 파이프라인 재검토 (현재 TensorFlow Serving 고정)
- 프론트엔드(`web/`) 상태 관리·빌드 시스템 현대화
- CI/CD 파이프라인 정비 및 배포 자동화
- 모니터링·알림 체계 구축 (현재 Grafana+Prometheus 기반 확장)
- `shared/` 내부 구조 정리 (`diagrams/`, `scripts/`, `data/` 분리 — 필요시)

---

## Done — 완료

- **[M1] 테스트 인프라 및 기준선 구축** — 2026-04-24 완료 (PR [#11](https://github.com/data-sy/my-math-teacher/pull/11), [#12](https://github.com/data-sy/my-math-teacher/pull/12))
  - [milestone](milestones/milestone-1-test-infrastructure.md)
  - Testcontainers 기반 통합 테스트 (MySQL 8 + Neo4j 5.12), 테스트 전용 `application-test.yml` 프로파일, JPA N+1 감지 (Hibernate Statistics)
  - 성능 기준선 실측 (warmup 3 + 측정 100 회, avg/p95/p99) — 7 개 연산을 `docs/benchmark/milestone-1-baseline.md` 에 기록
  - Neo4j 그래프 결과 sha256 스냅샷 (`shared/benchmark/neo4j-snapshot-20260424.json`) — M2 동치성 비교용
  - 회귀 감지 테스트 2 종 (`shouldNotRegress*`, warmup 20 + median 30 기반)
  - 피처 플래그 구조 `mmt.<영역>.<설정>` 도입 (`mmt.migration.*`, `mmt.observability.*`, `mmt.benchmark.baseline.*`)
  - `MysqlConceptRepository` 스텁 + `ConceptService.findNodesIdByConceptIdDepth3` 조건 분기 (M2 롤백 구조)
  - `QueryTimingAspect` + Micrometer `SimpleMeterRegistry` — 리포지토리 쿼리 시간 · 슬로우 쿼리 WARN
- **[M0] Claude Code 통합 환경 구축** — 2026-04-24 완료 (머지 `710167c`)
  - [milestone](milestones/milestone-0-claude-code-integration.md)
  - 계층형 CLAUDE.md (루트 + `api/` + `web/`), 슬래시 커맨드 3종(`/analyze-before-change`, `/write-adr`, `/review-pr`), Analyze-Before-Change·피처 플래그 가드레일 명시, ADR 템플릿

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
