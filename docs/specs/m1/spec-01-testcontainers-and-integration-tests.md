# Spec M1-01: Testcontainers 및 통합 테스트

**상위 마일스톤:** [Milestone 1](../../milestones/milestone-1-test-infrastructure.md)
**브랜치:** `chore/setup-test-infrastructure`
**예상 Claude Code 세션:** 2회 (90~120분)
**선행 조건:** Milestone 0 완료, ADR `0001-test-coverage-before-migration.md` Accepted

---

## 이 Spec의 범위

Testcontainers 기반 통합 테스트 환경(MySQL + Neo4j)을 구축하고, 현재 Neo4j로 수행되는 그래프 쿼리 3종 및 이를 호출하는 서비스 레이어의 테스트를 작성한다. CI 워크플로우에 테스트 실행 단계를 추가한다.

## 확인된 전제 (audit 완료)

- `api/`는 Spring Boot 3.1.6, Java 17, Gradle 기반
- `ConceptRepository`는 `ReactiveNeo4jRepository<Concept, Integer>` 타입
- `Concept`는 `@Node("concept")` Neo4j 노드. `chapterId int` 필드 보유. `getChapter()` 메서드 없음
- `Chapter` 엔티티의 `schoolLevel` 필드는 주석 처리됨. `schoolLevel`은 `Concept`에 존재
- `ConceptService`의 실제 메서드: `findNodesByConceptId`, `findNodesIdByConceptIdDepth2/3/5`, `findToConcepts`
- `ProbabilityService`는 `.block()` 호출 포함 (`ProbabilityService.java:66`)
- `LogicUtil`에 BFS 알고리즘 존재 (`LogicUtil.java:31`)
- CI 워크플로우 `.github/workflows/api-ci-cd-with-ec2.yml` 존재하나 `./gradlew test` 단계 없음
- docker-compose.yml 기준 MySQL 8.0, Neo4j는 별도 버전 사용 중
- `QueryCountAssertions` 유틸 미존재 → Hibernate Statistics 직접 사용 (결정 3)

## 작업 규칙

1. **Analyze-Before-Change 준수**: 파일 생성·수정 전 현재 상태 요약 + 계획 승인 대기
2. **코드 예시는 템플릿**: 이 문서의 Java 코드 블록은 의도를 보여주는 템플릿. 실제 패키지·엔티티 필드명은 코드베이스 확인 후 조정
3. **커밋 분리**: 각 Task 완료 시 단위로 커밋
4. **테스트 실행 확인**: 각 Task 완료 시 반드시 `./gradlew test --tests "대상클래스"`로 동작 확인
5. **Reactive 테스트 패턴**: Neo4j 리포지토리 테스트는 `StepVerifier` 또는 `.block()` + AssertJ 조합. `@DataJpaTest`는 절대 사용 금지 (타입 불일치)

---

## Task 1.1: Testcontainers 의존성·설정 및 CI 갱신

**입력:**
- `api/build.gradle`
- 기존 테스트 디렉토리 `api/src/test/java/`
- `.github/workflows/api-ci-cd-with-ec2.yml`
- docker-compose.yml (MySQL·Neo4j 버전 확인용)

**작업 내용:**

### (1) build.gradle 의존성 추가

```gradle
dependencies {
    // Testcontainers BOM
    testImplementation platform('org.testcontainers:testcontainers-bom:1.19.7')
    testImplementation 'org.testcontainers:testcontainers'
    testImplementation 'org.testcontainers:mysql'
    testImplementation 'org.testcontainers:neo4j'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

- Testcontainers BOM 1.19.x는 Spring Boot 3.1.x와 호환 (공개 매트릭스 기준)
- MySQL·Neo4j 두 컨테이너 모두 필요 (Neo4j 쿼리 기준선을 별도 환경에서 측정하기 위해)

### (2) 테스트 컨테이너 설정 클래스

위치: `api/src/test/java/com/mmt/api/config/TestcontainersConfig.java` (실제 패키지는 `com.mmt.api` 하위 구조에 맞춰 조정)

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mmt_test")
            .withReuse(true);
    }

    @Bean
    @ServiceConnection
    public Neo4jContainer<?> neo4jContainer() {
        return new Neo4jContainer<>("neo4j:5")  // docker-compose.yml 실제 버전으로 교체
            .withoutAuthentication()
            .withReuse(true);
    }
}
```

- `@ServiceConnection` (Spring Boot 3.1+)로 자동 DataSource·Neo4j URI 주입
- Neo4j 이미지 태그는 docker-compose.yml의 프로덕션 버전과 일치시킬 것 (확인 후 교체)
- `.withReuse(true)`로 테스트 간 컨테이너 재사용하여 속도 확보 (로컬 한정)

### (3) CI 워크플로우에 테스트 단계 추가

`.github/workflows/api-ci-cd-with-ec2.yml`에 기존 Docker build 이전에 테스트 단계 삽입:

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run tests
        working-directory: ./api
        run: ./gradlew test
      - name: Upload test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: api/build/reports/tests/test
  
  # 기존 build 잡이 test에 의존하도록 needs 추가
  build-and-deploy:
    needs: test
    # ... 기존 설정
```

- Docker-in-Docker 없이도 GitHub Actions runner 기본 Docker로 Testcontainers 동작함
- 실패 시 테스트 리포트 아티팩트 업로드하여 원인 파악 용이

**산출물:**
- [ ] `api/build.gradle` 업데이트 (Testcontainers BOM + MySQL + Neo4j + JUnit Jupiter)
- [ ] `api/src/test/java/com/mmt/api/config/TestcontainersConfig.java` 신규 (패키지는 실제 구조에 맞춰)
- [ ] `.github/workflows/api-ci-cd-with-ec2.yml`에 test 잡 추가

**검증:**
```bash
cd api && ./gradlew test --tests "TestcontainersConfig*" --info
```
- 로그에 `mysql:8.0`, `neo4j:5`(실제 버전) 이미지 pull·start 확인
- 이후 PR 푸시 시 GitHub Actions에서 test 잡이 실행되고 통과하는지 확인

---

## Task 1.2: Neo4j 그래프 쿼리 통합 테스트 작성

**대상:** `ConceptRepository` (`ReactiveNeo4jRepository<Concept, Integer>`)

**입력:**
- `ConceptRepository`의 실제 `@Query` 어노테이션들 (Cypher 쿼리 verbatim 인용용)
- 엔티티 `Concept` (`@Node("concept")`, `chapterId`, `schoolLevel` 등)
- 엔티티 `Chapter`

**작업 내용:**

현재 Neo4j에서 수행 중인 3가지 쿼리 유형에 대해 통합 테스트 작성. 마이그레이션 후 MySQL CTE가 동일 결과를 반환해야 하므로, 이 테스트가 **결과 동치성의 기준**이 된다.

### 테스트 대상 쿼리 (Cypher)

1. 선수 개념 찾기 (들어오는 엣지):
```cypher
MATCH (n)-[r]->(m {concept_id: $conceptId}) RETURN n
```

2. 깊이 N의 개념 찾기 (0..3 또는 0..5):
```cypher
MATCH (n)-[*0..3]->(m {concept_id: $conceptId}) RETURN n
```

3. 경로에서 개념 ID 추출 (BFS 알고리즘용):
```cypher
MATCH path = (start_node)-[*0..3]->(n {concept_id: $conceptId})
WITH nodes(path) AS connected_nodes
UNWIND connected_nodes AS node
RETURN collect(DISTINCT node.concept_id) AS concept_ids
```

**중요**: 실제 `ConceptRepository.findNodesIdByConceptIdDepth2/3/5` 등에 선언된 `@Query` 어노테이션의 쿼리를 그대로 인용하여 테스트할 것. 위 예시는 원본 마일스톤 문서에서 가져온 것으로 실제와 미세하게 다를 수 있음.

### 테스트 코드 템플릿 (Reactive)

```java
// 실제 패키지: com.mmt.api.repository 하위 구조에 맞춰 import
@DataNeo4jTest
@Import(TestcontainersConfig.class)
@Testcontainers
class ConceptRepositoryTest {

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private ReactiveNeo4jOperations neo4jOperations;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 시드 (Cypher 스크립트 또는 프로그램적 생성)
        // 실제 concept·chapter 노드·관계 생성
    }

    @Test
    void findByIdReturnsMonoWithExistingConcept() {
        // findById는 Mono<Concept> 반환 (Optional 아님)
        StepVerifier.create(conceptRepository.findById(4979))
            .assertNext(concept -> {
                assertThat(concept).isNotNull();
                assertThat(concept.getConceptId()).isEqualTo(4979);
                // chapterId 필드로 검증 (getChapter() 아님)
                assertThat(concept.getChapterId()).isGreaterThan(0);
            })
            .verifyComplete();
    }

    @Test
    void findNodesIdByConceptIdDepth3ReturnsPrerequisites() {
        int conceptId = 4979;  // 실제 시드 데이터에 존재하는 id로 교체

        List<Integer> result = conceptRepository
            .findNodesIdByConceptIdDepth3(conceptId)
            .collectList()
            .block();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        // 결과셋은 Spec 02에서 스냅샷으로 저장
    }

    @Test
    void findToConceptsReturnsIncomingEdges() {
        int conceptId = 4979;

        StepVerifier.create(conceptRepository.findToConcepts(conceptId))
            .expectNextCount(/* 기대 노드 수 */ 0)  // 시드 데이터 기반
            .verifyComplete();
    }
}
```

**주의사항:**
- `findById`는 `Mono<Concept>` 반환. `.orElseThrow()` 호출 불가. `StepVerifier` 또는 `.block()` 사용
- `concept.getChapter()` 같은 메서드 존재하지 않음. `chapterId` 필드 또는 `Chapter`를 별도 조회
- JPA의 `@DataJpaTest` 사용 금지. Neo4j 전용 `@DataNeo4jTest` 사용

### 깊이별 메서드 처리 방침

현재 `findNodesIdByConceptIdDepth2/3/5` 세 메서드가 존재. M1에서는 **세 메서드를 각각 별도 테스트**로 다루고, maxDepth를 파라미터화한 래퍼 메서드 도입은 M2에서 MySQL CTE로 통합할 때 함께 결정한다.

**산출물:**
- [ ] `api/src/test/java/.../repository/ConceptRepositoryTest.java` (Reactive 스타일)
- [ ] 3종 쿼리 각각에 대한 테스트 메서드
- [ ] 깊이 2/3/5 각 메서드에 대한 기본 검증 테스트

**검증:**
```bash
cd api && ./gradlew test --tests "ConceptRepositoryTest"
```
- 모든 테스트 통과
- Neo4j 쿼리 로그에서 예상한 Cypher가 실행됨을 확인

---

## Task 1.3: 서비스 레이어 및 유틸 테스트 작성

**대상:**
- `ConceptService` (그래프 관련 메서드 호출)
- `ProbabilityService` (`.block()` 포함 리액티브 흐름)
- `LogicUtil` (BFS 알고리즘)

**입력:**
- `ConceptService`의 실제 메서드 시그니처
- `ProbabilityService.java:66` 근방의 `.block()` 호출 맥락
- `LogicUtil.java:31`의 BFS 구현

**작업 내용:**

### (1) ConceptService 테스트

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ConceptServiceTest {

    @Autowired
    private ConceptService conceptService;

    @Test
    void findNodesByConceptIdReturnsConnectedConcepts() {
        int conceptId = 4979;  // 실제 시드 데이터 id
        
        List<Concept> result = conceptService.findNodesByConceptId(conceptId);
        
        assertThat(result).isNotEmpty();
    }

    @Test
    void findNodesIdByConceptIdDepth3MatchesDepth2PlusOneHop() {
        // 깊이 관계의 단조 증가성 검증 (depth3 ⊇ depth2)
        int conceptId = 4979;
        
        Set<Integer> depth2 = new HashSet<>(conceptService.findNodesIdByConceptIdDepth2(conceptId));
        Set<Integer> depth3 = new HashSet<>(conceptService.findNodesIdByConceptIdDepth3(conceptId));
        
        assertThat(depth3).containsAll(depth2);
    }
}
```

### (2) ProbabilityService 테스트 (Reactive→Blocking 경계)

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ProbabilityServiceTest {

    @Autowired
    private ProbabilityService probabilityService;

    @Test
    void blockingCallCompletesWithinTimeout() {
        long userTestId = /* 시드 데이터 id */ 1L;
        
        assertTimeout(Duration.ofSeconds(5), () -> {
            // ProbabilityService 내부의 .block() 호출 경로 실행
            probabilityService.someMethod(userTestId);
        });
    }

    @Test
    void emptyResultHandledGracefully() {
        long nonExistentId = 99999999L;
        
        // .block()이 null 또는 빈 결과 처리하는지 검증
        // 실제 메서드 시그니처에 맞춰 어설션 조정
    }
}
```

**주의**: `ProbabilityService`의 정확한 메서드명과 `.block()` 사용 맥락은 `ProbabilityService.java:66` 근방을 실제로 확인 후 시나리오 작성. 위 템플릿은 구조 예시.

### (3) LogicUtil BFS 단위 테스트

```java
class LogicUtilTest {

    @Test
    void bfsReturnsAllReachableNodes() {
        // Given: 간단한 그래프 (1 → 2 → 3, 1 → 4)
        Map<Integer, List<Integer>> adjacency = Map.of(
            1, List.of(2, 4),
            2, List.of(3),
            3, List.of(),
            4, List.of()
        );
        
        // When
        List<Integer> result = LogicUtil.bfs(adjacency, 1);  // 실제 시그니처에 맞춰
        
        // Then
        assertThat(result).containsExactlyInAnyOrder(1, 2, 3, 4);
    }

    @Test
    void bfsHandlesCyclesWithoutInfiniteLoop() {
        // 순환 그래프 (1 → 2 → 1)
        Map<Integer, List<Integer>> adjacency = Map.of(
            1, List.of(2),
            2, List.of(1)
        );
        
        assertTimeout(Duration.ofSeconds(1), () -> {
            LogicUtil.bfs(adjacency, 1);
        });
    }

    @Test
    void bfsHandlesEmptyInput() {
        List<Integer> result = LogicUtil.bfs(Map.of(), 1);
        // 시작 노드만 반환하거나 빈 리스트 — 실제 구현에 맞춰 어설션
        assertThat(result).isNotNull();
    }
}
```

**실제 `LogicUtil.bfs` 시그니처를 확인 후 템플릿의 파라미터 타입·반환 타입 조정.**

**산출물:**
- [ ] `api/src/test/java/.../service/ConceptServiceTest.java`
- [ ] `api/src/test/java/.../service/ProbabilityServiceTest.java`
- [ ] `api/src/test/java/.../util/LogicUtilTest.java`

**검증:**
```bash
cd api && ./gradlew test --tests "*ServiceTest" --tests "LogicUtilTest"
```
- 모든 테스트 통과
- 테스트 실행 시간이 합리적 범위 (컨테이너 재사용 효과 확인)

---

## Task 1.4: N+1 회귀 방지 테스트 (JPA 리포지토리 대상)

**대상:** JPA 기반 리포지토리 중 조인 대상이 있는 것 (예: `TestRepository`, `AnswerRepository` 등 — 실제 코드 확인 후 대상 확정)

**입력:**
- JPA 리포지토리 목록
- Hibernate Statistics API

**작업 내용:**

Neo4j 그래프 쿼리는 단일 Cypher로 전체를 가져오므로 N+1이 구조적으로 발생하지 않는다. N+1 감지는 JPA 리포지토리에만 적용한다.

### 테스트 템플릿 (Hibernate Statistics 직접 사용)

```java
@DataJpaTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class TestRepositoryN1Test {

    @Autowired
    private TestRepository testRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistics() {
        return entityManager
            .unwrap(Session.class)
            .getSessionFactory()
            .getStatistics();
    }

    @BeforeEach
    void enableStats() {
        statistics().setStatisticsEnabled(true);
        statistics().clear();
    }

    @Test
    void findAllWithJoinDoesNotTriggerN1() {
        // When
        List<TestEntity> tests = testRepository.findAllWithQuestions();
        tests.forEach(t -> t.getQuestions().size());

        // Then
        long queryCount = statistics().getPrepareStatementCount();
        assertThat(queryCount).isEqualTo(1);  // 단일 쿼리만 기대
    }
}
```

**주의:**
- `@PersistenceContext`로 EntityManager 주입
- `Session.getSessionFactory().getStatistics()` 경로로 Statistics 획득
- `test` 프로파일에서 `hibernate.generate_statistics=true` 설정 필요 (Spec 03에서 `application-test.yml`에 추가됨)

### 적용 범위 결정

M1 범위에서는 다음 기준으로 N+1 테스트 대상 선정:
- JPA 기반 리포지토리 중 `@OneToMany`·`@ManyToMany` 관계가 있는 것
- 또는 `findAllWithXxx`처럼 조인 의도가 있는 메서드를 가진 것

**산출물:**
- [ ] N+1 검증이 의미 있는 JPA 리포지토리 1~3개에 대한 테스트
- [ ] 테스트 파일명: `{RepositoryName}N1Test.java`

**검증:**
```bash
cd api && ./gradlew test --tests "*N1Test"
```
- 모든 테스트 통과
- 통계 로그에서 예상 쿼리 수와 일치

---

## 전체 완료 체크리스트

- [ ] Task 1.1: Testcontainers 의존성·설정·CI 갱신 및 커밋
- [ ] Task 1.2: Neo4j 리포지토리 통합 테스트 작성 및 커밋
- [ ] Task 1.3: 서비스·유틸 테스트 작성 및 커밋
- [ ] Task 1.4: JPA N+1 방지 테스트 작성 및 커밋
- [ ] CI에서 전체 테스트 통과 확인
- [ ] 테스트 실행 시간이 비합리적으로 길지 않은지 확인 (`.withReuse(true)` 효과 확인)

## 다음 Spec

완료 후 [spec-02-performance-baseline.md](spec-02-performance-baseline.md) 진행.
