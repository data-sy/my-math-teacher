# Spec M1-02: 성능 기준선 측정

**상위 마일스톤:** [Milestone 1](../../milestones/milestone-1-test-infrastructure.md)
**브랜치:** `chore/setup-test-infrastructure`
**예상 Claude Code 세션:** 1회 (60분)
**선행 Spec:** [spec-01-testcontainers-and-integration-tests.md](spec-01-testcontainers-and-integration-tests.md) 완료 필수

---

## 이 Spec의 범위

Neo4j → MySQL CTE 마이그레이션(M2) 전의 **성능 기준선을 실측**하여 기록한다. Neo4j 그래프 탐색 결과를 JSON 스냅샷으로 저장하여 M2의 동치성 검증 기준으로 삼는다.

## 확인된 전제

- Spec 01 완료: MySQL·Neo4j Testcontainers, `test` 프로파일, 통합 테스트 동작
- `ConceptService`의 그래프 메서드: `findNodesByConceptId`, `findNodesIdByConceptIdDepth2/3/5`, `findToConcepts`
- `ProbabilityRepository.findResults(Long userTestId)` 존재
- `BatchPreparedStatementSetter` 사용 중 (`JdbcTemplateAnswerRepository`, `JdbcTemplateProbabilityRepository`)
- `conceptId=4979`는 `api/cql/return.cql`·`api/sql/select.sql`의 placeholder로 존재. **실제 시드 DB에 존재하는지는 Task 2.1 첫 단계에서 확인**

## 작업 규칙

1. **수치는 실측**: 이 spec의 모든 성능 수치는 실측해서 기록한다. 원본 문서의 추측값(`~5ms` 등)은 **기준선으로 사용 금지**
2. **측정 환경 기록**: JVM 옵션, 컨테이너 스펙, 테스트 데이터 규모를 모든 측정에 기록
3. **반복 측정**: 각 쿼리는 최소 **warm-up 3회 + 측정 10회**로 평균·p95·p99 산출
4. **커밋 분리**: 벤치마크 코드와 측정 결과 기록은 별도 커밋

---

## Task 2.1: 벤치마크 대상 결정 및 테스트 작성

**입력:**
- Spec 01에서 작성한 통합 테스트 코드 (재활용 가능)
- `api/sql/`, `api/cql/` 하위의 쿼리 파일 (벤치마크 대상 SQL·Cypher 참고)

**작업 내용:**

### (1) 벤치마크 대상 ID 확정

원본 문서의 `conceptId=4979`는 placeholder일 가능성이 있다. Task 시작 시 다음 쿼리로 **실제 시드 데이터에 존재하는 대표 id 3~5개**를 선정:

```cypher
// Neo4j에서 관계가 풍부한 대표 노드 조회
MATCH (c:concept)-[r]->()
WITH c, count(r) AS out_degree
ORDER BY out_degree DESC
LIMIT 5
RETURN c.concept_id, out_degree
```

선정된 id를 spec 본문 표·벤치마크 코드에 반영.

### (2) 측정 대상 표

아래 표의 "실측값" 컬럼을 Task 2.2에서 채워 기준선으로 확정한다.

| # | 연산 | 실측값(avg / p95 / p99) | 비고 |
|---|------|------------------------|------|
| 1 | ID로 단일 Concept 조회 (Neo4j) | **[실측 필요]** | `ConceptRepository.findById(Mono)` |
| 2 | 깊이 2 그래프 탐색 | **[실측 필요]** | `findNodesIdByConceptIdDepth2` |
| 3 | 깊이 3 그래프 탐색 | **[실측 필요]** | `findNodesIdByConceptIdDepth3` |
| 4 | 깊이 5 그래프 탐색 | **[실측 필요]** | `findNodesIdByConceptIdDepth5` |
| 5 | 선수 개념 조회 (들어오는 엣지) | **[실측 필요]** | `findToConcepts` |
| 6 | 진단 결과 조회 (JPA 다중 조인) | **[실측 필요]** | `ProbabilityRepository.findResults(Long)` |
| 7 | 100개 확률 배치 삽입 | **[실측 필요]** | `BatchPreparedStatementSetter` 기반 |

**이 추측값은 어떠한 PR·테스트·인용에도 기준선으로 사용 금지.** 표의 "실측값" 컬럼만 Task 2.2의 최종 결과에 반영된다.

### (3) 벤치마크 테스트 템플릿

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class GraphQueryPerformanceTest {

    @Autowired
    private ConceptService conceptService;

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 10;

    @Test
    void benchmarkDepth3GraphTraversal() {
        int conceptId = 4979;  // Task 2.1 (1)에서 확정된 id로 교체

        // Warm-up
        for (int i = 0; i < WARMUP_RUNS; i++) {
            conceptService.findNodesIdByConceptIdDepth3(conceptId);
        }

        // Measure
        long[] nanos = new long[MEASURED_RUNS];
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            conceptService.findNodesIdByConceptIdDepth3(conceptId);
            nanos[i] = System.nanoTime() - start;
        }

        // Report
        Arrays.sort(nanos);
        long avgMs = Arrays.stream(nanos).sum() / MEASURED_RUNS / 1_000_000;
        long p95Ms = nanos[(int)(MEASURED_RUNS * 0.95) - 1] / 1_000_000;
        long p99Ms = nanos[(int)(MEASURED_RUNS * 0.99) - 1] / 1_000_000;

        System.out.printf("[Benchmark] depth=3 conceptId=%d: avg=%dms, p95=%dms, p99=%dms%n",
            conceptId, avgMs, p95Ms, p99Ms);
    }

    // 깊이 2, 5, findToConcepts, findById 등 나머지 쿼리에 대해서도 동일 패턴
}
```

### (4) JPA 쿼리 벤치마크 템플릿

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class RepositoryBenchmarkTest {

    @Autowired
    private ProbabilityRepository probabilityRepository;

    @Test
    void benchmarkFindResults() {
        Long userTestId = 1L;  // 시드 데이터 id로 교체

        for (int i = 0; i < 3; i++) probabilityRepository.findResults(userTestId);

        long[] nanos = new long[10];
        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            probabilityRepository.findResults(userTestId);
            nanos[i] = System.nanoTime() - start;
        }

        Arrays.sort(nanos);
        long avgMs = Arrays.stream(nanos).sum() / 10 / 1_000_000;
        System.out.printf("[Benchmark] findResults: avg=%dms%n", avgMs);
    }
}
```

### (5) 회귀 감지 테스트 (Task 2.2 결과 반영 후 완성)

```java
@Test
void shouldNotRegressFindResultsPerformance() {
    // Task 2.2의 실측 기준선으로 교체
    long baselineMs = /* [Task 2.2 결과로 주입] */ 50L;

    long start = System.nanoTime();
    probabilityRepository.findResults(1L);
    long actualMs = (System.nanoTime() - start) / 1_000_000;

    // +15% 허용
    assertThat(actualMs).isLessThan((long)(baselineMs * 1.15));
}
```

기준선은 직접 하드코딩 대신 `application-test.yml`의 설정 값으로 주입하는 방향을 권장 (Spec 03 Task 3.1에서 `mmt.benchmark.baseline.*` 키 추가):

```java
@Value("${mmt.benchmark.baseline.find-results-ms:50}")
private long baselineFindResultsMs;
```

**산출물:**
- [ ] `api/src/test/java/.../performance/GraphQueryPerformanceTest.java`
- [ ] `api/src/test/java/.../performance/RepositoryBenchmarkTest.java`
- [ ] 벤치마크 대상 id 3~5개 결정 기록 (Task 2.2 문서에 반영)
- [ ] 회귀 감지 테스트 스텁 (Task 2.2 완료 후 기준선 주입)

**검증:**
```bash
cd api && ./gradlew test --tests "*PerformanceTest" --tests "*BenchmarkTest" --info
```
- 모든 벤치마크가 수치를 표준 출력
- 수치를 표 형태로 수집 가능

---

## Task 2.2: 기준선 결과 기록 및 그래프 스냅샷 저장

**입력:**
- Task 2.1에서 출력된 실측 수치
- Neo4j 그래프 탐색 결과셋 (마이그레이션 후 정확성 비교용)

**작업 내용:**

### (1) 기록 디렉토리 준비

```bash
mkdir -p docs/benchmark
mkdir -p shared/benchmark
```

- `docs/benchmark/` — 읽는 문서 (기준선 보고서)
- `shared/benchmark/` — 자산 (JSON 스냅샷)

분리 근거: CLAUDE.md의 원칙에 따라 "읽는 것"과 "자산"을 구분.

### (2) 기준선 문서 작성

위치: `docs/benchmark/milestone-1-baseline.md`

```markdown
# Milestone 1 Performance Baseline

**측정 일시:** YYYY-MM-DD HH:MM
**측정자:** (이름 또는 익명)
**Git commit:** (측정 시점 HEAD 해시)

## 측정 환경

- JVM: OpenJDK 17.0.x
- MySQL 컨테이너: mysql:8.0 (Testcontainers, `.withReuse(true)`)
- Neo4j 컨테이너: neo4j:5 (실제 버전)
- 테스트 데이터:
  - Neo4j: concept N개, 관계 M개 (실측)
  - MySQL: (테이블별 레코드 수)
- JVM 옵션: -Xms512m -Xmx2g (또는 실제 옵션)
- 하드웨어: (로컬 머신 사양)
- 벤치마크 대상 concept_id: [A, B, C, ...]

## 쿼리별 측정 결과

| # | 연산 | avg | p95 | p99 | 비고 |
|---|------|-----|-----|-----|------|
| 1 | ID로 단일 Concept 조회 | XXms | XXms | XXms | Neo4j |
| 2 | 깊이 2 그래프 탐색 | XXms | XXms | XXms | conceptId=4979 기준 |
| ... | ... | ... | ... | ... | ... |

## Neo4j 그래프 탐색 결과 스냅샷

| 입력 (conceptId, depth) | 결과 개수 | 해시 (sha256) | 파일 |
|-------------------------|-----------|---------------|------|
| (4979, 3) | N | hash... | shared/benchmark/neo4j-snapshot-YYYYMMDD.json |

## 비고

(측정 중 관찰된 특이사항, 변동성, 환경별 차이 등)
```

### (3) 그래프 결과 스냅샷 JSON 생성

위치: `shared/benchmark/neo4j-snapshot-YYYYMMDD.json`

벤치마크 테스트에서 결과셋을 추출하여 JSON으로 저장하는 유틸 추가:

```java
@Test
void exportNeo4jResultsSnapshot() throws IOException {
    List<Integer> sampleConceptIds = List.of(4979, /* 다른 대표 id들 */);
    Map<String, Object> snapshot = new LinkedHashMap<>();

    for (Integer conceptId : sampleConceptIds) {
        for (int depth : List.of(2, 3, 5)) {
            List<Integer> results = callByDepth(conceptId, depth);
            Collections.sort(results);
            
            String key = "conceptId=" + conceptId + ",depth=" + depth;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("count", results.size());
            entry.put("concept_ids", results);
            entry.put("sha256", sha256(results.toString()));
            snapshot.put(key, entry);
        }
    }

    snapshot.put("generated_at", Instant.now().toString());
    snapshot.put("git_commit", System.getenv().getOrDefault("GIT_COMMIT", "unknown"));

    Path outPath = Paths.get("../shared/benchmark/neo4j-snapshot-" 
        + LocalDate.now().toString().replace("-", "") + ".json");
    Files.writeString(outPath, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
}
```

- `callByDepth(conceptId, depth)`는 depth별로 `findNodesIdByConceptIdDepth2/3/5` 분기 호출
- 결과를 정렬한 뒤 sha256 해시 계산 → M2에서 MySQL CTE 결과와 해시 비교만으로 동치성 검증 가능

### (4) 회귀 감지 테스트에 기준선 주입

Task 2.1에서 준비한 회귀 감지 테스트에 실측값 반영:
- `application-test.yml`에 `mmt.benchmark.baseline.*` 추가 (Spec 03 Task 3.1과 연동)
- 또는 상수로 하드코딩 (간단한 방법)

**산출물:**
- [ ] `docs/benchmark/milestone-1-baseline.md` (실측 결과 전부 반영)
- [ ] `shared/benchmark/neo4j-snapshot-YYYYMMDD.json` (해시 포함)
- [ ] 회귀 감지 테스트에 기준선 주입 완료

**검증:**
```bash
# 파일 존재
ls docs/benchmark/milestone-1-baseline.md
ls shared/benchmark/neo4j-snapshot-*.json

# 기준선 문서의 모든 [실측 필요] 자리가 채워졌는지 확인
! grep -n "\[실측 필요\]" docs/benchmark/milestone-1-baseline.md

# 회귀 감지 테스트 통과
cd api && ./gradlew test --tests "*shouldNotRegress*"
```

---

## 전체 완료 체크리스트

- [ ] Task 2.1: 벤치마크 대상 id 확정, 벤치마크 테스트 작성 및 커밋
- [ ] Task 2.2: 기준선 문서 작성, 그래프 스냅샷 저장, 회귀 테스트 반영 및 커밋
- [ ] 모든 `[실측 필요]` 셀이 채워졌는지 확인
- [ ] 원본 추측값(`~5ms` 등)이 결과 문서 어디에도 기준선으로 남지 않았는지 확인
- [ ] 측정 환경이 재현 가능하도록 충분히 기록됨

## 다음 Spec

완료 후 [spec-03-feature-flags-and-observability.md](spec-03-feature-flags-and-observability.md) 진행.
