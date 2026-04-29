# ADR 0002: Milestone 1 구현 컨벤션 묶음

## Status

Accepted

## Context

Milestone 1(테스트 인프라 및 기준선 구축) 진행 중, `/audit-doc` 슬래시 커맨드를 통한 spec 감사 과정에서 다음 4가지 사항에 대한 결정이 필요했다:

1. 피처 플래그 네임스페이스
2. 테스트 프로파일 구조
3. N+1 쿼리 감지 방식
4. Micrometer MeterRegistry Bean 등록 방식

각 결정은 단독 ADR로 분리할 만큼 큰 변경은 아니지만, M1 마일스톤 완료 후 회고할 때 이유를 잊지 않기 위해 묶음 ADR로 기록한다.

## Decisions

### 1. 피처 플래그 네임스페이스: `mmt.<영역>.<설정>`

**결정:** 모든 피처 플래그·설정 키를 2단계 네임스페이스 `mmt.<영역>.<설정>`로 통일한다. 초기 영역은 `mmt.migration.*`, `mmt.observability.*`, `mmt.benchmark.baseline.*` 세 가지.

**근거:**
- 1차 audit에서 같은 문서 안에 `mmt.migration.use-mysql-cte-for-graph`와 `mmt.use-neo4j-for-graph` 두 스타일이 혼재한 것이 발견됨
- 영역 구분이 있으면 새 플래그 추가 시 위치가 자명해지고 충돌 위험 감소
- 2단계로 제한한 이유: 더 깊어지면 과도한 분류로 오히려 발견성이 떨어짐

**대안 검토:**
- 단일 평면(`mmt.use-mysql-cte-for-graph`): 영역 구분 없어 키 충돌 가능성
- 3단계 이상(`mmt.feature.migration.use-mysql-cte`): 과도한 깊이로 오타 위험 증가

### 2. 테스트 프로파일: `application-test.yml` 단독 도입

**결정:** 기존 `application.yml` + `application-securelocal.yml` 구조를 보존하고, 테스트 전용 `application-test.yml`만 신규 도입한다. `securelocal`과 독립적이며 인클루드 관계 없음.

**근거:**
- 원본 마일스톤은 `application-local/test/prod.yml` 3개 분리를 가정했으나, 실제 프로젝트는 `securelocal` 중심 구조
- `local`·`prod` 추가는 실제 배포 구조 파악 후 별도 결정해야 함 (현 시점에서는 충분한 정보 없음)
- 테스트 환경의 격리만 우선 달성하는 최소 변경 채택

**대안 검토:**
- 3개 프로파일 전면 도입: 변경 범위 크고 prod 설계가 명확하지 않음
- 기존 `securelocal` 재활용: 로컬 개발과 테스트 설정이 섞여 디버깅 곤란

### 3. N+1 쿼리 감지: Hibernate `Statistics` API 직접 사용

**결정:** 별도 유틸(`QueryCountAssertions` 등)을 만들지 않고 Hibernate의 `Statistics` API를 테스트 코드에서 직접 호출한다. 적용 위치: `api/src/test/java/com/mmt/api/repository/users/UsersRepositoryN1Test.java` 등 `*N1Test.java`.

**근거:**
- 원본 마일스톤은 `QueryCountAssertions` 유틸 사용을 가정했으나 실제 프로젝트에 미존재
- 외부 라이브러리(예: `hibernate-query-counter`) 도입은 오버엔지니어링
- N+1 검증은 JPA 리포지토리에 한정되며 사용 빈도가 낮아, 표준 API 직접 호출이 단순하고 충분

**제외 범위:**
- Neo4j Reactive 리포지토리: 그래프 전체를 단일 Cypher로 조회하므로 N+1이 구조적으로 발생 안 함

### 4. MeterRegistry Bean 등록: `SimpleMeterRegistry` 수동 Bean 등록

**결정:** `spring-boot-starter-actuator`를 도입하지 않고, `ObservabilityConfig`(`api/src/main/java/com/mmt/api/observability/ObservabilityConfig.java`)에서 `SimpleMeterRegistry`를 `@Bean`으로 직접 등록한다. 의존성은 `io.micrometer:micrometer-core`만 추가.

**근거:**
- 1차 audit에서 `MeterRegistry`를 주입받는 코드(`QueryTimingAspect`)가 있는데 Bean 등록이 누락된 상태가 발견됨 (런타임에 컨텍스트 기동 실패 위험)
- M1의 목적은 "쿼리 시간을 측정 가능한 인프라"의 도입이지 운영 모니터링 인입이 아니므로 Actuator의 자동설정·엔드포인트·보안 영향까지 끌어들일 필요가 없음
- `SimpleMeterRegistry`는 in-memory 누적 구현으로, 테스트에서 Timer 값을 검증하기에 충분

**대안 검토:**
- `spring-boot-starter-actuator` 도입: 자동설정·HTTP 엔드포인트·보안 설정까지 영향 범위가 커지며, M1 범위 밖. 추후 Prometheus·Grafana 연동 로드맵 진입 시 승급 예정 (해당 시점에 본 Bean 제거)
- `MeterRegistry` 미등록(NoOp 사용): `QueryTimingAspect`가 빈 주입 실패로 컨텍스트 기동 실패. 채택 불가

## Consequences

### Positive
- 4가지 결정의 맥락이 한 곳에 보존되어 이후 마일스톤에서 일관성 유지 가능
- M2 이후 새 피처 플래그·테스트 설정·메트릭 추가 시 본 ADR을 참조하여 일관 적용

### Negative
- 4가지 서로 다른 주제가 한 ADR에 묶여 있어 개별 검색이 살짝 불편
- 향후 한 결정만 뒤집을 때 ADR 분할이 필요할 수 있음

### Neutral
- 각 결정이 코드에 이미 반영된 상태이므로 본 ADR은 사후 기록의 성격

## References

- 관련 마일스톤: `docs/milestones/milestone-1-test-infrastructure.md`
- 선행 ADR: `docs/adr/0001-test-coverage-before-migration.md`
- 도출 과정: M1 진행 중 두 차례 `/audit-doc` 배치 감사를 통해 확정
- 관련 spec:
  - `docs/specs/m1/spec-01-testcontainers-and-integration-tests.md` (테스트 프로파일·N+1 감지)
  - `docs/specs/m1/spec-03-feature-flags-and-observability.md` (피처 플래그·MeterRegistry)
