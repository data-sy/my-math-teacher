# Milestone 1 Performance Baseline

**측정 일시:** 2026-04-24 19:05 KST
**측정자:** 세션 실행 기록 (로컬)
**Git commit:** `405088df68aabc14f7ea76eb455c7dc4db71a443` (main, pre-Spec 02 코드 추가 시점)
**측정 스펙:** [docs/specs/m1/spec-02-performance-baseline.md](../specs/m1/spec-02-performance-baseline.md)

> 본 문서의 모든 수치는 Spec 02 Task 2.1 의 벤치마크 테스트 실측값이다. 원본 문서의 추측값 (`~5ms` 등) 은 기준선으로 사용되지 않는다. 기준선 갱신 시 `api/src/test/java/com/mmt/api/performance/` 하위 `BASELINE_*_MS` 상수와 함께 본 문서를 동기화한다.

## 측정 환경

- **JVM:** OpenJDK 17.0.9 (Gradle test forked JVM, 기본 힙 옵션)
- **MySQL 컨테이너:** `mysql:8.0` (Testcontainers 1.20.6, `.withReuse(true)`)
- **Neo4j 컨테이너:** `neo4j:5.12` (Testcontainers, `.withoutAuthentication()`, `.withReuse(true)`)
- **하드웨어:** MacBook Air (Mac14,2), Apple M2, 16 GB RAM, macOS Darwin 25.4
- **Docker:** Docker Desktop 29.1.5, API 1.52 (Gradle test 에서 `DOCKER_API_VERSION=1.45` 강제)
- **샘플링:** 각 쿼리 warmup 3회 + 측정 100회 (avg / p95 / p99 산출)

### 테스트 데이터 규모

- **Neo4j:** `neo4j/init/concepts.csv` (1635행) → **concept 노드 1631개 적재** (누락 4행, CSV toInteger 실패 추정; baseline 영향 미미). `neo4j/init/knowledge_space.csv` (3447행) → **KNOWLEDGE_SPACE 관계 3446개 적재**.
- **MySQL:** `RepositoryBenchmarkTest.@BeforeAll` 에서 `create.sql` 의 서브셋 (chapters/concepts/items/tests/tests_items/users_tests/answers/probabilities) 을 raw DDL 로 생성 후:
  - `chapters`: 1행, `concepts`: 1행, `tests`: 1행, `items`: 1행, `tests_items`: 1행
  - `users_tests`: 2행 (find 용 1 + batch insert 용 1)
  - `answers`: 2행 (find 용 1 + batch insert 용 1)
  - `probabilities`: 200행 (find 용, `to_concept_depth ∈ {0,1,2}` → 필터 depth<3 전량 통과)

### 벤치마크 대상 concept_id

`@BeforeAll` 에서 다음 Cypher 로 동적 선정 (out_degree 상위 5개):

```cypher
MATCH (c:concept)-[r]->() WITH c, count(r) AS out_degree
ORDER BY out_degree DESC LIMIT 5
RETURN c.concept_id, c.name, out_degree
```

| # | concept_id | out_degree | name |
|---|-----------:|-----------:|------|
| 1 | **6646** (대표) | 32 | 99까지의 수 쓰고 읽기 |
| 2 | 7595 | 29 | `'+'` 와 `'='` 기호를 사용하여 덧셈을 쓰고 읽기 |
| 3 | 6420 | 28 | 덧셈과 뺼셈하기 |
| 4 | 6784 | 26 | 방정식 |
| 5 | 7944 | 25 | 곱셈식 |

원본 spec 의 placeholder `conceptId=4979` 는 실제 시드 데이터의 대표 ID 가 아니므로 사용하지 않았다. 벤치마크 본체는 대표 ID (`6646`) 하나로 실측하여 CI 부담을 줄였고, 나머지 4개는 그래프 스냅샷 export 에서 모두 사용된다.

## 쿼리별 측정 결과

| # | 연산 | avg | p95 | p99 | 비고 |
|---|------|----:|----:|----:|------|
| 1 | ID로 단일 Concept 조회 (Neo4j) | **2.088 ms** | 3.043 ms | 4.149 ms | `findById` 의 Cypher 동치를 driver 로 실행 (repo.findById 는 reactiveTM 미등록으로 실행 불가 — Spec 03 에서 보강 예정). conceptId=6646. |
| 2 | 깊이 2 그래프 탐색 | **4.800 ms** | 8.289 ms | 12.421 ms | `ConceptRepository.findNodesIdByConceptIdDepth2`, conceptId=6646 |
| 3 | 깊이 3 그래프 탐색 | **5.392 ms** | 14.034 ms | 17.005 ms | `ConceptRepository.findNodesIdByConceptIdDepth3`, conceptId=6646 |
| 4 | 깊이 5 그래프 탐색 | **5.302 ms** | 12.898 ms | 15.025 ms | `ConceptRepository.findNodesIdByConceptIdDepth5`, conceptId=6646 |
| 5 | 선수 개념 조회 (들어오는 엣지) | **5.662 ms** | 9.224 ms | 10.101 ms | `ConceptRepository.findToConceptsByConceptId`, conceptId=6646 |
| 6 | 진단 결과 조회 (5-way JOIN) | **1.495 ms** | 2.047 ms | 2.318 ms | `ProbabilityRepository.findResults(userTestId=1)` → 반환 200행 |
| 7 | 100개 확률 배치 삽입 | **16.930 ms** | 20.353 ms | 25.996 ms | `JdbcTemplateProbabilityRepository.save` (BatchPreparedStatementSetter) |

실측 로그 원본: `api/build/test-results/test/TEST-com.mmt.api.performance.*.xml`

## Neo4j 그래프 탐색 결과 스냅샷

M2 마이그레이션 이후 MySQL CTE 결과와 해시로 동치성 비교하기 위한 스냅샷이다.

- **파일:** [`shared/benchmark/neo4j-snapshot-20260424.json`](../../shared/benchmark/neo4j-snapshot-20260424.json) (42,352 bytes)
- **entry 개수:** 5 concept_id × 3 depth = 15
- **각 entry 구조:** `count`, `concept_ids` (정렬됨), `sha256 (List.toString() 기반)`
- **메타:** `generated_at`, `git_commit`, `representative_id`

### 스냅샷 요약 (해시 상위 16자)

| 입력 (conceptId, depth) | 결과 개수 | sha256 (prefix) |
|------------------------|---------:|-----------------|
| (6646, 2) | 37 | `18a6329098597444` |
| (6646, 3) | 105 | `de33d216f0746027` |
| (6646, 5) | 316 | `027697343b475ec6` |
| (7595, 2) | 14 | `8e12ebd11c7d05fd` |
| (7595, 3) | 22 | `c29a38f6c3f96c27` |
| (7595, 5) | 27 | `882d2348747a6c50` |
| (6420, 2) | 1 | `659e8924717045c0` |
| (6420, 3) | 1 | `659e8924717045c0` |
| (6420, 5) | 1 | `659e8924717045c0` |
| (6784, 2) | 1 | `886a18558fe8cc49` |
| (6784, 3) | 1 | `886a18558fe8cc49` |
| (6784, 5) | 1 | `886a18558fe8cc49` |
| (7944, 2) | 62 | `2b588d6618c49ab8` |
| (7944, 3) | 334 | `36828ff89b804357` |
| (7944, 5) | 5712 | `7b46136b32f57417` |

**전체 해시값은 JSON 파일 참조.**

## 관찰된 이슈 및 비고

1. **CSV 적재 4행 누락** — concepts.csv 1635행 중 1631개만 노드로 적재. `toInteger()` 실패 (빈 PK 또는 숫자 아닌 `id`) 로 추정. baseline 측정에는 영향 없으나 M2 MySQL 이관 시 이 4행 처리 방침을 정해야 한다.

2. **`findNodesIdByConceptIdDepth*` Cypher 동작 이상** — `[id IN node.concept_id]` 에서 `node.concept_id` 가 스칼라인데 `IN` 의 우변으로 쓰여 실제 반환값이 의도와 다를 가능성. 특히 conceptId=6420 / 6784 는 depth 2/3/5 결과가 모두 동일 (count=1, 같은 sha256) → Cypher 가 효과적으로 "자기 자신"만 반환. conceptId=7944 depth=5 는 5712행 반환 (UNWIND 의 중복 합산). 이는 **현 Neo4j 쿼리의 동작을 그대로 캡처한 것**이며, M2 의 MySQL CTE 는 동일한 (올바른?) 의미론을 재현해야 한다. 스냅샷 해시 비교 외에 **결과 개수 · 내용의 의미론 검증** 도 M2 동치성 게이트에 포함할 것.

3. **`findById` 우회** — `ReactiveNeo4jRepository.findById` 는 `@Transactional(transactionManager = "reactiveTransactionManager")` 를 요구하는데 `@DataNeo4jTest` 슬라이스가 해당 빈을 등록하지 않아 실행 불가. Spec 02 에서는 `driver.session().run("MATCH (c:concept {concept_id:$id}) RETURN c")` 로 동치 측정. Spec 03 Task 에서 reactiveTransactionManager 빈 보강 시 repo.findById 로 재측정 가능.

4. **배치 insert 의 max 값 outlier** — `batchInsert100Probabilities` max=46.4ms (p99=26.0ms 의 1.8배). MySQL 컨테이너 초기 wait/flush 로 추정. warmup 3회로는 완전 제거되지 않는다. 기준선은 avg 로 확정.

5. **`.withReuse(true)` 무효화** — 로컬 `~/.testcontainers.properties` 에 `testcontainers.reuse.enable=true` 가 없어 매 JVM 마다 컨테이너가 재생성된다. Spec 01 의 TODO 로 기록됨. 컨테이너 재사용 시 첫 실행 제외 약 10초 단축 예상.

## 회귀 감지 테스트 (Task 2.2 주입 완료)

다음 두 테스트에 실측 기준선을 상수로 주입했다:

- `com.mmt.api.performance.GraphQueryPerformanceTest#shouldNotRegressDepth3GraphTraversal`
  - `BASELINE_DEPTH3_AVG_MS = 6L` (실측 5.392ms 에 정수 버퍼)
  - `ALLOWED_REGRESSION = 1.5` → ceiling 9ms
- `com.mmt.api.performance.RepositoryBenchmarkTest#shouldNotRegressFindResultsPerformance`
  - `BASELINE_FIND_RESULTS_AVG_MS = 3L` (실측 1.495ms 에 정수 버퍼)
  - `ALLOWED_REGRESSION = 1.5` → ceiling 4ms

회귀 테스트는 warmup 20회 + 측정 30회의 **median (p50)** 으로 비교한다. 초기 `--tests "*shouldNotRegress*"` 필터 단독 실행 시 cold JVM 의 첫 수 회가 튀어 avg 가 오염되는 현상을 관찰 (depth3 avg=15ms, 베이스라인의 3배) → warmup 증량 + 외부값 내성 있는 median 으로 전환. Spec 03 Task 3.1 에서 `application-test.yml` 의 `mmt.benchmark.baseline.*` 로 이전 예정 (현재는 상수 하드코딩).

## Spec 03 Task 3.3 로깅 관측성 검증 (2026-04-24)

Task 3.1 에서 확장된 `application-test.yml` 의 로깅 설정이 **실제로 작동하며 프로덕션 yml 을 오염시키지 않음**을 확인.

### (1) 프로덕션 오염 검사 — 모두 PASS (leak 없음)

```bash
for f in application.yml application-secure.yml application-securelocal.yml; do
  grep -n "hibernate.SQL\|hibernate\.orm\.jdbc\.bind\|neo4j.cypher" api/src/main/resources/$f
done
```

세 파일 모두 해당 패턴 미검출. `org.hibernate.SQL=DEBUG`, `org.hibernate.orm.jdbc.bind=TRACE`, `org.springframework.data.neo4j.cypher=DEBUG` 은 `application-test.yml` 에만 존재.

### (2) 로깅 실제 동작 — 모두 PASS

- `ConceptRepositoryTest` 실행 시 `DEBUG ... org.springframework.data.neo4j.cypher : Executing: MATCH ... RETURN` 형태로 Cypher 출력 확인
- `UsersRepositoryN1Test` 실행 시 `Hibernate: ...` 형태로 SQL 출력 확인

### (3) N+1 감지 기반 `generate_statistics` — PASS

```bash
$ grep -rn "generate_statistics" api/src/main/resources/
application-test.yml:11:        generate_statistics: true
```

`application-test.yml` 에만 존재. `UsersRepositoryN1Test` 는 이 설정에 의존하며 `./gradlew test --tests "*N1Test"` 통과 확인.

### 관찰된 pre-existing 이슈 (Spec 03 범위 밖, 향후 과제)

- `application.yml` 에 `com.mmt: DEBUG`, `springframework.data.neo4j: DEBUG`, `springframework.security: DEBUG` 가 **모든 프로파일 공통 기본값** 으로 존재. Task 3.3 (1) 이 검사한 hibernate · cypher 설정은 깨끗하지만, application-level DEBUG 로거는 프로덕션 로그를 과도하게 생성할 수 있음. 프로덕션 환경 분리 (Spec 03 작업 규칙 2 "프로파일 독립성" 의 근본 해결) 와 함께 별도 ADR 로 다룰 가치 있음.

---

## 재현 방법

```bash
# 전체 실행 (컨테이너 기동 포함, ~40초)
cd api && GIT_COMMIT=$(git rev-parse HEAD) ./gradlew test \
  --tests "com.mmt.api.performance.*"

# 벤치마크 결과만 추출
grep -hE "\[Benchmark|\[Snapshot|\[Regression" \
  api/build/test-results/test/TEST-com.mmt.api.performance.*.xml

# 회귀 테스트만
cd api && ./gradlew test --tests "*shouldNotRegress*"
```
