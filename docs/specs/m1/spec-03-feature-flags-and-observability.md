# Spec M1-03: 피처 플래그 및 관측성 인프라

**상위 마일스톤:** [Milestone 1](../../milestones/milestone-1-test-infrastructure.md)
**브랜치:** `chore/setup-test-infrastructure`
**예상 Claude Code 세션:** 1회 (60분)
**선행 Spec:** [spec-02-performance-baseline.md](spec-02-performance-baseline.md) 완료 필수

---

## 이 Spec의 범위

M2 마이그레이션의 즉시 롤백을 가능하게 할 피처 플래그 구조를 준비하고, 쿼리 수준의 관측성(로깅·메트릭)을 확보한다. 테스트 전용 프로파일(`test`)을 신규 도입하여 로깅·통계 설정을 격리한다.

## 확인된 전제

- `application.yml`, `application-secure.yml`, `application-securelocal.yml`만 존재. `application-local.yml`·`application-test.yml`·`application-prod.yml` **미존재**
- `spring-boot-starter-aop` 의존성 **미존재** → Task 3.4에서 추가
- `ConceptRepository`는 Neo4j Reactive (`ReactiveNeo4jRepository<Concept, Integer>`)
- `ConceptService`에는 `findPrerequisiteConcepts` 메서드 없음. 실제는 `findNodesByConceptId`, `findNodesIdByConceptIdDepth2/3/5`, `findToConcepts`
- `.collectList().block()` 패턴 실제 코드에서 사용 중 (`ProbabilityService.java:66`, `KnowledgeSpaceService.java:36`)
- `MetricsService` 미존재 → Micrometer `MeterRegistry` 직접 주입
- `com.mmt.api.repository` 패키지 존재 (AOP 포인트컷 대상)
- `mmt.*` 프로퍼티 프로젝트 전체에서 전무 → 본 Task에서 최초 도입

## 작업 규칙

1. **네임스페이스 일관성**: 모든 플래그·설정에 `mmt.<영역>.<설정>` 2단계 구조 사용 ([결정 2](../../milestones/milestone-1-test-infrastructure.md#결정-2-피처-플래그-네임스페이스))
2. **프로파일 독립성**: `test` 프로파일은 `securelocal`과 완전히 독립. 인클루드 관계 없음 ([결정 1](../../milestones/milestone-1-test-infrastructure.md#결정-1-테스트-프로파일-구조))
3. **AOP 범위 제한**: 모든 Bean이 아닌 `com.mmt.api.repository..*`에만 적용
4. **커밋 분리**: 각 Task 단위 커밋

---

## Task 3.1: 테스트 프로파일 및 피처 플래그 설정 추가

**입력:**
- `api/src/main/resources/application.yml`
- 기존 `application-secure.yml`·`application-securelocal.yml` (수정 대상 아님)

**작업 내용:**

### (1) 공통 설정 `application.yml`에 플래그 추가

기존 파일 하단에 추가 (다른 설정은 건드리지 않음):

```yaml
mmt:
  migration:
    use-mysql-cte-for-graph: false   # M2에서 true로 전환
    use-jpa-for-tests: false         # JPA 전환 Epic에서 사용
    use-jpa-for-concepts: false      # JPA 전환 Epic에서 사용
  observability:
    slow-query-threshold-ms: 100
```

### (2) `application-test.yml` 확장 (Spec 01 뼈대 위에 추가)

Spec 01 Task 1.1 (3)에서 이미 `application-test.yml` 뼈대가 생성되어 `hibernate.generate_statistics`·SQL·Cypher 로깅은 포함되어 있다. 이 Task에서는 **기존 파일에 `mmt.benchmark.baseline` 영역만 추가**한다.

위치: `api/src/main/resources/application-test.yml` (수정)

```yaml
# 아래 블록을 기존 application-test.yml 하단에 이어붙임

mmt:
  benchmark:
    baseline:
      # Spec 02 Task 2.2에서 실측 결과를 여기에 주입
      # 예: find-results-ms: 42
      # 예: depth3-traversal-ms: 18
```

(공통 `mmt.migration.*`, `mmt.observability.*`는 (1)에서 `application.yml`에 추가된다. test 프로파일은 그 기본값을 상속하므로 여기서 중복 선언 불필요.)

### (3) 활성화 확인

`@Value`로 주입되는지 단위 테스트로 검증:

```java
@SpringBootTest
@ActiveProfiles("test")
class FeatureFlagIntegrationTest {

    @Value("${mmt.migration.use-mysql-cte-for-graph}")
    private boolean useMysqlCte;

    @Value("${mmt.observability.slow-query-threshold-ms}")
    private long slowQueryThresholdMs;

    @Test
    void defaultFlagValuesAreLoaded() {
        assertThat(useMysqlCte).isFalse();
        assertThat(slowQueryThresholdMs).isEqualTo(100L);
    }
}
```

**산출물:**
- [ ] `application.yml`에 `mmt.migration.*`, `mmt.observability.*` 추가
- [ ] `application-test.yml`에 `mmt.benchmark.baseline` 영역 추가 (뼈대는 Spec 01 Task 1.1 (3)에서 생성됨)
- [ ] `FeatureFlagIntegrationTest` 신규 작성

**검증:**
```bash
cd api && ./gradlew test --tests "FeatureFlagIntegrationTest"
```

---

## Task 3.2: 서비스 레이어에 조건 분기 구조 준비

**입력:**
- `ConceptService` (Neo4j 호출 지점)
- `com.mmt.api.repository` 패키지 하위의 실제 리포지토리 구조

**작업 내용:**

### (1) MySQL 리포지토리 인터페이스 준비 (스텁)

M2에서 실제 구현. 이 Spec에서는 인터페이스·스텁만:

```java
// com.mmt.api.repository.mysql.MysqlConceptRepository (실제 패키지 구조에 맞춰)
public interface MysqlConceptRepository {
    
    /**
     * 주어진 conceptId에 대해 depth만큼 도달 가능한 모든 concept_id를 반환.
     * M2에서 MySQL 재귀 CTE로 구현 예정.
     */
    List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth);
}

// 임시 스텁 (M2에서 제거·대체)
@Component
@ConditionalOnProperty(prefix = "mmt.migration", name = "use-mysql-cte-for-graph", havingValue = "true")
public class MysqlConceptRepositoryStub implements MysqlConceptRepository {

    @Override
    public List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth) {
        throw new UnsupportedOperationException(
            "MySQL CTE 구현은 Milestone 2에서 제공됩니다");
    }
}
```

- `@ConditionalOnProperty`로 플래그 `true` 상태일 때만 Bean 등록
- 기본값(`false`)에서는 스텁조차 로드되지 않음 → 기존 동작 완전 보존

### (2) ConceptService에 분기 로직 추가

실제 `ConceptService`에는 `findNodesIdByConceptIdDepth2/3/5` 세 메서드가 있다. M2에서 MySQL CTE로 통합될 때 maxDepth를 파라미터화할 예정이므로, M1에서는 **가장 대표적인 메서드인 `findNodesIdByConceptIdDepth3`에만** 분기 구조를 선제적으로 적용한다. 나머지 depth 메서드는 M2에서 동일 패턴으로 확장.

```java
@Service
public class ConceptService {

    @Value("${mmt.migration.use-mysql-cte-for-graph:false}")
    private boolean useMysqlCte;

    private final ConceptRepository neo4jConceptRepository;  // 기존 주입
    
    // 플래그 true일 때만 주입 (Optional 처리)
    private final Optional<MysqlConceptRepository> mysqlConceptRepository;

    public ConceptService(
        ConceptRepository neo4jConceptRepository,
        Optional<MysqlConceptRepository> mysqlConceptRepository
    ) {
        this.neo4jConceptRepository = neo4jConceptRepository;
        this.mysqlConceptRepository = mysqlConceptRepository;
    }

    public List<Integer> findNodesIdByConceptIdDepth3(int conceptId) {
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            return mysqlConceptRepository.get().findPrerequisiteConceptIds(conceptId, 3);
        }
        // 기존 Neo4j 경로
        return neo4jConceptRepository
            .findNodesIdByConceptIdDepth3(conceptId)
            .collectList()
            .block();
    }

    // 다른 depth 메서드는 M2에서 동일 패턴 적용
}
```

**주의사항:**
- `Optional<MysqlConceptRepository>` 주입으로 플래그 `false` 상태에서 Bean이 없어도 컨텍스트 기동 가능
- `.block()`은 `ProbabilityService`, `KnowledgeSpaceService`에서 이미 사용 중인 패턴
- 기존 `findNodesIdByConceptIdDepth3`의 시그니처·반환 타입이 변경되지 않음을 유지

### (3) 플래그 토글 동작 테스트

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
class ConceptServiceFeatureFlagTest {

    @Nested
    @ActiveProfiles("test")
    @TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=false")
    class WhenFlagFalse {
        @Autowired ConceptService service;
        
        @Test
        void usesNeo4jPath() {
            List<Integer> result = service.findNodesIdByConceptIdDepth3(4979);
            assertThat(result).isNotNull();  // 정상 반환
        }
    }

    @Nested
    @ActiveProfiles("test")
    @TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=true")
    class WhenFlagTrue {
        @Autowired ConceptService service;
        
        @Test
        void throwsFromStub() {
            assertThatThrownBy(() -> service.findNodesIdByConceptIdDepth3(4979))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Milestone 2");
        }
    }
}
```

**산출물:**
- [ ] `MysqlConceptRepository` 인터페이스
- [ ] `MysqlConceptRepositoryStub` (조건부 Bean)
- [ ] `ConceptService.findNodesIdByConceptIdDepth3`에 조건 분기 추가
- [ ] `ConceptServiceFeatureFlagTest` (false/true 양쪽 검증)

**검증:**
```bash
cd api && ./gradlew test --tests "*ConceptServiceFeatureFlagTest"
# 플래그 false 상태에서 Spec 01의 통합 테스트도 여전히 통과해야 함
cd api && ./gradlew test --tests "ConceptServiceTest"
```

---

## Task 3.3: Hibernate 쿼리 로깅 검증

**입력:**
- Task 3.1에서 생성된 `application-test.yml`

**작업 내용:**

Task 3.1에서 `application-test.yml`에 로깅 설정은 이미 추가됨. 이 Task에서는 **설정이 실제로 작동하는지 검증**하고, 프로덕션 오염 여부만 재확인.

### (1) 프로덕션 오염 확인

```bash
# application.yml과 기타 프로파일 파일에 DEBUG/TRACE 로깅이 새어들어가지 않았는지 확인
grep -n "hibernate.SQL" api/src/main/resources/application.yml
grep -n "hibernate.SQL" api/src/main/resources/application-secure.yml
grep -n "hibernate.SQL" api/src/main/resources/application-securelocal.yml
```

위 세 파일에서는 해당 설정이 **나타나면 안 된다**. `application-test.yml`에만 있어야 함.

### (2) 로깅 작동 확인 테스트

Spec 01의 `ConceptRepositoryTest` 실행 시 로그에 Cypher 쿼리가 DEBUG로 찍히는지, JPA 리포지토리 테스트에서 SQL이 찍히는지 확인.

```bash
cd api && ./gradlew test --tests "ConceptRepositoryTest" --info 2>&1 | grep -i "cypher\|SQL" | head -20
```

### (3) N+1 감지 테스트 재검증

Spec 01 Task 1.4의 N+1 테스트는 `hibernate.generate_statistics=true`에 의존. `application-test.yml`에 해당 설정이 있는지 재확인:

```bash
grep "generate_statistics" api/src/main/resources/application-test.yml
```

결과가 `generate_statistics: true`여야 함.

**산출물:**
- [ ] 프로덕션 프로파일 파일에 DEBUG 로깅 없음을 grep으로 확인한 기록
- [ ] 로그에 Cypher/SQL 쿼리가 찍히는 테스트 실행 결과

**검증:**
```bash
cd api && ./gradlew test --tests "ConceptRepositoryTest" --info
# 로그에 Cypher 쿼리가 DEBUG 레벨로 찍히는지 확인

cd api && ./gradlew test --tests "*N1Test"
# Statistics 기반 N+1 테스트 통과
```

---

## Task 3.4: 쿼리 시간 측정 AOP 구성

**입력:**
- `api/build.gradle` (spring-boot-starter-aop 의존성 추가 대상)
- `com.mmt.api.repository` 패키지 (AOP 포인트컷)

**작업 내용:**

### (1) AOP 의존성 및 MeterRegistry Bean 구성

build.gradle에 AOP + Micrometer Core 의존성 추가:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'io.micrometer:micrometer-core'
}
```

Actuator를 도입하지 않으므로 `MeterRegistry`는 자동 등록되지 않는다. 현재 용도(Aspect 내 Timer)에 맞춰 `SimpleMeterRegistry`를 수동 Bean으로 등록한다:

```java
// api/src/main/java/com/mmt/api/config/ObservabilityConfig.java (실제 패키지 구조에 맞춰)
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
```

추후 Prometheus·Grafana 연동이 로드맵으로 진입하면 `spring-boot-starter-actuator`로 승급하고 이 Bean은 제거한다.

### (2) Aspect 클래스 작성

위치: `api/src/main/java/com/mmt/api/observability/QueryTimingAspect.java` (실제 패키지 구조에 맞춰 조정)

```java
@Aspect
@Component
@Slf4j
public class QueryTimingAspect {

    private final MeterRegistry meterRegistry;
    private final long slowQueryThresholdMs;

    public QueryTimingAspect(
        MeterRegistry meterRegistry,
        @Value("${mmt.observability.slow-query-threshold-ms:100}") long slowQueryThresholdMs
    ) {
        this.meterRegistry = meterRegistry;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    @Around("execution(* com.mmt.api.repository..*(..))")
    public Object measureQueryTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        String methodName = joinPoint.getSignature().toShortString();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - startNanos;
            long elapsedMs = elapsedNanos / 1_000_000;

            meterRegistry.timer("mmt.query.time", "method", methodName)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

            if (elapsedMs > slowQueryThresholdMs) {
                log.warn("느린 쿼리 감지: {} ({}ms)", methodName, elapsedMs);
            }
        }
    }
}
```

**주의사항:**
- 나노초 측정 필수 (밀리초 단위는 빠른 쿼리에서 0으로 뭉개짐)
- `MeterRegistry`를 Spring Boot가 제공하므로 별도 `MetricsService` 불필요
- `slow-query-threshold-ms`는 `application.yml`의 `mmt.observability.slow-query-threshold-ms`에서 주입 (Task 3.1에서 추가됨)

### (3) AOP 동작 테스트

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "mmt.observability.slow-query-threshold-ms=0")  // 모든 쿼리를 "느림"으로 만들어 로그 발생 유도
class QueryTimingAspectTest {

    @Autowired ConceptService conceptService;
    @Autowired MeterRegistry meterRegistry;

    @Test
    void aspectRecordsTimerMetric() {
        conceptService.findNodesIdByConceptIdDepth3(4979);
        
        Timer timer = meterRegistry.find("mmt.query.time").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThan(0);
    }
}
```

**산출물:**
- [ ] `spring-boot-starter-aop` + `io.micrometer:micrometer-core` 의존성 추가
- [ ] `ObservabilityConfig` (`SimpleMeterRegistry` Bean 수동 등록)
- [ ] `QueryTimingAspect` 클래스
- [ ] `QueryTimingAspectTest`

**검증:**
```bash
cd api && ./gradlew test --tests "*QueryTimingAspectTest"

# 실제 리포지토리 테스트 실행 시 AOP가 개입하는지 확인
cd api && ./gradlew test --tests "*RepositoryTest" --info 2>&1 | grep "느린 쿼리\|mmt.query.time"
```

---

## 전체 완료 체크리스트

- [ ] Task 3.1: 테스트 프로파일·피처 플래그 설정 추가, 커밋
- [ ] Task 3.2: 조건 분기 구조 준비, 토글 테스트, 커밋
- [ ] Task 3.3: 로깅 설정 검증, 프로덕션 오염 확인, 커밋 (설정 변경이 없으면 문서 커밋만)
- [ ] Task 3.4: AOP 의존성 추가, Aspect 작성, 커밋
- [ ] 피처 플래그 `false` 기본값 상태에서 Spec 01의 통합 테스트가 모두 통과함을 재확인
- [ ] CI에서 전체 테스트 스위트 통과

## Milestone 1 전체 완료 후

- [ ] PR 머지
- [ ] `docs/roadmap.md`에서 M1을 Now → Done으로 이동 (날짜·PR 번호 기록)
- [ ] M2 착수 준비: `/audit-doc docs/milestones/milestone-2-neo4j-to-mysql.md` (M2 문서가 준비되면)
- [ ] M2 첫 작업은 반드시 `/analyze-before-change`로 시작 (이 Spec에서 준비한 피처 플래그가 실제 작동하는지 체감)
