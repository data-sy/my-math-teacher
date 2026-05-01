# Milestone 1: 테스트 인프라 및 기준선 구축

**브랜치:** `chore/setup-test-infrastructure`
**의존성:** Milestone 0 완료 (CLAUDE.md 계층·슬래시 커맨드·가드레일)
**위험 수준:** 낮음
**관련 ADR:** [ADR 0001: 마이그레이션 전 테스트 커버리지 선행 구축](../adr/0001-test-coverage-before-migration.md)

---

## 목표

Neo4j → MySQL CTE 마이그레이션(Milestone 2)을 안전하게 진행하기 위한 기반을 구축합니다. 마이그레이션 전 성능 기준선을 확보하고, 회귀를 감지할 수 있는 테스트 인프라를 마련합니다.

**이 마일스톤이 먼저인 이유:**
- 기준선 없이 마이그레이션하면 성능 저하를 감지할 수 없음
- 테스트 없이 마이그레이션하면 롤백 판단 기준이 없음
- 피처 플래그 없이 마이그레이션하면 즉시 롤백이 불가능

상세 맥락은 위 ADR 참조.

---

## 주요 결정사항 (audit-doc 리포트 반영)

본 마일스톤 실행 전에 확정된 세 가지 결정. 모든 spec이 이를 전제로 작성됨.

### 결정 1: 테스트 프로파일 구조

현재 `application.yml` + `application-securelocal.yml` 구조를 유지하고, 테스트 전용 `application-test.yml`만 신규 도입한다. `securelocal`과 독립적이며 인클루드 관계 없음.

테스트 실행 시 `@ActiveProfiles("test")` 또는 `-Dspring.profiles.active=test`로 활성화.

### 결정 2: 피처 플래그 네임스페이스

2단계 네임스페이스 `mmt.<영역>.<설정>`을 채택한다. 초기 영역은 세 가지:

```yaml
mmt:
  migration:
    use-mysql-cte-for-graph: false   # M2에서 스위치
    use-jpa-for-tests: false         # JPA 전환 Epic에서 사용
    use-jpa-for-concepts: false      # JPA 전환 Epic에서 사용
  observability:
    slow-query-threshold-ms: 100
  benchmark:
    baseline:
      # 실측 후 주입 (Spec 02 결과)
```

### 결정 3: N+1 감지 방식

별도 유틸(예: `QueryCountAssertions`)을 만들지 않고 Hibernate의 `Statistics` API를 직접 사용한다. Neo4j Reactive 리포지토리는 그래프 전체를 단일 Cypher로 조회하므로 N+1이 구조적으로 발생하지 않아 이 검증 범위에서 제외.

---

## 포함된 Spec

이 마일스톤은 3개의 spec으로 분할됩니다. 세션을 spec 단위로 끊어 실행하세요.

1. [`spec-01-testcontainers-and-integration-tests.md`](../specs/m1/spec-01-testcontainers-and-integration-tests.md) — Testcontainers 환경 구축, CI 테스트 단계 추가, Neo4j·JPA 리포지토리·서비스 통합 테스트
2. [`spec-02-performance-baseline.md`](../specs/m1/spec-02-performance-baseline.md) — 리포지토리별 쿼리 성능 실측, 기준선 기록, 그래프 결과 스냅샷
3. [`spec-03-feature-flags-and-observability.md`](../specs/m1/spec-03-feature-flags-and-observability.md) — 피처 플래그, 조건 분기 구조, 쿼리 로깅·AOP 메트릭

---

## 완료 기준

- [ ] Testcontainers(MySQL·Neo4j) 기반 통합 테스트 환경 동작 확인
- [ ] Neo4j 그래프 쿼리 3종에 대한 통합 테스트 작성 완료 (Reactive 스타일)
- [ ] 그래프 탐색 관련 서비스·유틸 단위 테스트 작성 완료
- [ ] 모든 주요 쿼리에 대한 성능 기준선 **실측** 및 기록 완료
- [ ] 그래프 탐색 결과 JSON 스냅샷 저장 완료 (M2 동치성 검증용)
- [ ] 피처 플래그 설정 추가 및 조건 분기 구조 준비 완료
- [ ] Hibernate 쿼리 로깅 및 AOP 메트릭 구성 완료
- [ ] CI 워크플로우에 `./gradlew test` 단계 추가 및 전체 테스트 통과 확인

## 산출물

- 통합 테스트 프레임워크 (Testcontainers MySQL + Neo4j 기반)
- `application-test.yml` (테스트 전용 프로파일)
- 성능 기준선 보고서: `docs/benchmark/milestone-1-baseline.md`
- 그래프 결과 스냅샷: `shared/benchmark/neo4j-snapshot-YYYYMMDD.json`
- 피처 플래그 구성: `api/src/main/resources/application.yml`
- 쿼리 시간 측정 AOP 및 로깅 구성
- CI 워크플로우 업데이트 (`.github/workflows/api-ci-cd-with-ec2.yml`)
- 마이그레이션 체크리스트 (M2에서 참조)

---

## 이후 마일스톤과의 관계

- **M2 (Neo4j → MySQL CTE 마이그레이션)** — 이 마일스톤의 성능 기준선·테스트·피처 플래그 위에서 진행. `mmt.migration.use-mysql-cte-for-graph` 플래그가 M2의 스위치가 됨. Neo4j 그래프 스냅샷이 MySQL CTE 결과와의 동치성 검증 기준이 됨
- **Epic (JdbcTemplate → JPA 전환)** — 이 마일스톤에서 준비된 `use-jpa-for-tests`, `use-jpa-for-concepts` 플래그 구조를 그대로 재사용
