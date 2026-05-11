# Spec 02: 서비스 통합 + 캐싱 + 리액티브 정리

**상위 마일스톤:** Milestone 2 (Neo4j → MySQL CTE 마이그레이션)
**대상 Phase:** Phase 2 + Phase 3
**예상 소요:** 3일
**선행 spec:** spec-01

---

## 범위

spec-01에서 도입한 CTE 메서드를 ConceptService 그래프 메서드 군에 통합한다. 캐싱 레이어를 추가하고, 피처 플래그 기반 분기를 구현하며, Neo4j 제거 시 자연 해소되는 `.block()` 호출을 정리한다.

본 spec은 **서비스 레이어에 한정**된다. 검증·점진 출시·폐기는 spec-03 범위.

---

## ⚠ 중요 — 피처 플래그 명명 정정

원본 M2 자료의 `mmt.use-neo4j-for-graph` 표기는 다음 두 가지 문제가 있어 본 spec 전체에서 사용하지 않는다:

1. **ADR 0002 §1 위반** — 영역 네임스페이스 누락 (`mmt.<영역>.<설정>` 2단계 위반)
2. **의미 반전** — Neo4j를 OFF하는 플래그로 정의되어 직관성 저하

M1에서 정착된 정식 키:

```yaml
mmt.migration.use-mysql-cte-for-graph: false   # 기본값 = 안전 측(Neo4j) fallback
```

- `true` → CTE 사용 (마이그레이션 진행)
- `false` → Neo4j 사용 (기본값, 롤백 상태)
- 본 spec과 spec-03 전체에서 이 키만 사용. 다른 표기 일체 금지.

---

## 사전 조건 / 검증 필요

> 📌 **데이터 모델 의미 정의는 [spec-01의 데이터 모델 노트](spec-01-cte-repository-and-indexes.md#-데이터-모델-노트--knowledge_space-엣지-방향성) 참조.** `from_concept_id`=후수, `to_concept_id`=선수. 본 spec의 모든 SQL·캐시 키 의미는 이 정의를 따른다.

- spec-01 완료 (CTE 메서드 사용 가능)
- M1 산출물:
  - 피처 플래그 `mmt.migration.use-mysql-cte-for-graph` 정의 완료
  - `findNodesIdByConceptIdDepth3`에 분기 시범 적용 완료 → spec-01에서 구조 변경됨(아래 ※ 참조)
- ✓ Spring Cache 미도입 상태 확인 — `@EnableCaching`/`@Cacheable`/`@CacheEvict`/`RedisCacheManager`/`CacheManager` 모두 부재. Redis는 `RedisUtil`(`api/src/main/java/com/mmt/api/util/RedisUtil.java`)을 통한 직접 호출로만 사용 중(JWT 토큰 저장 용도). 본 spec의 Task 2.1은 ADR 0003에 따라 RedisUtil 직접 호출 패턴으로 진행한다.

### ※ spec-01에서 갱신된 분기 패턴 (본 spec의 표준)

spec-01의 Analyze-Before-Change 결정으로 M1의 `MysqlConceptRepository` 인터페이스 + Stub은 삭제됐고, CTE 메서드는 `JdbcTemplateConceptRepository`에 누적된 상태다. 현재 `ConceptService.java:64-71`은 다음과 같다:

```java
@Transactional(readOnly = true)
public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
    if (useMysqlCte) {
        return Flux.fromIterable(
            jdbcTemplateConceptRepository.findPrerequisitesWithDepth(conceptId, 3)
                .stream().map(ConceptDepth::conceptId).toList());
    }
    return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
}
```

핵심 요소(본 spec 전체에 적용):
1. 리포지토리: **`JdbcTemplateConceptRepository`** (별도 클래스 아님, 기존 클래스에 누적)
2. 시그니처: `findPrerequisitesWithDepth(int conceptId, int maxDepth)` → `List<ConceptDepth>` (id + depth 페어 반환 — spec-01 A3 발견으로 depth 포함)
3. **가드 없음** (인터페이스가 제거되어 `Optional<>` 주입 불요)
4. ID 시그니처 보존 호출부: `.stream().map(ConceptDepth::conceptId).toList()` → `Flux.fromIterable` 래핑
5. spec-02는 위 패턴을 다른 4개 메서드(`findNodesIdByConceptIdDepth2/5`, `findNodesByConceptId`, `findToConcepts`)에 확산하고, 객체 반환 메서드는 ADR 0005 패턴으로 신규 도입한다(Task 3.1).

---

## Task 2.1 — 캐싱 통합

### 배경

그래프 데이터는 읽기 전용 + CSV 초기 로드 후 변경 없음. 캐싱으로 CTE 성능 열세를 충분히 보상 가능.

### 패턴: RedisUtil 직접 호출 (ADR 0003 결정)

**ADR 0003에 따라 Spring Cache(`@Cacheable`/`RedisCacheManager`)를 도입하지 않고, 기존 `RedisUtil`(`api/src/main/java/com/mmt/api/util/RedisUtil.java`)을 직접 호출하는 패턴으로 캐싱을 구현한다.** `@EnableCaching` 선언, 빈 등록, SpEL `condition` 검증 등 인프라 작업은 모두 비대상.

### 캐시 메서드 형태

ConceptService(또는 별도 캐시 래퍼)에서 CTE 호출 직전·직후에 명시적으로 캐시 조회·저장:

```java
@Service
public class ConceptService {

    private final RedisUtil redisUtil;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    // RedisUtil.set(key, o, duration) 의 duration 은 MILLISECONDS 단위 (TimeUnit.MILLISECONDS).
    // toSeconds() 사용 시 86.4초만 캐싱되어 의도(24h)와 불일치.
    private static final long TTL_24H = Duration.ofHours(24).toMillis();

    @Value("${mmt.migration.use-mysql-cte-for-graph:false}")
    private boolean useMysqlCte;

    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId) {
        if (useMysqlCte) {
            String key = "graph:prerequisites:ids:" + conceptId + ":3";
            @SuppressWarnings("unchecked")
            List<Integer> cached = (List<Integer>) redisUtil.get(key);
            if (cached != null) return Flux.fromIterable(cached);

            List<Integer> result = jdbcTemplateConceptRepository
                .findPrerequisitesWithDepth(conceptId, 3)
                .stream().map(ConceptDepth::conceptId).toList();
            redisUtil.set(key, result, TTL_24H);
            return Flux.fromIterable(result);
        }
        return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
    }
}
```

**ProbabilityService 경유(Task 3.4) 호환 메모:** ID 캐시 키는 위 형태(`graph:prerequisites:ids:<id>:<depth>`)로 충분하지만, ProbabilityService는 `Map<Integer, Integer>` (id→depth) 형태가 필요하므로 별도 헬퍼 `findPrerequisitesAsDepthMap(int, int)`을 추가하고 그 키는 `graph:prerequisites:depthmap:<id>:<depth>`로 분리한다(prefix는 ADR 0003 규약 확장 — Task 2.1 후속).

설계 메모:
- 키 prefix 규약 (ADR 0003 + 본 spec 확장):
  - `graph:prerequisites:ids:<conceptId>:<depth>` — `findPrerequisitesWithDepth(?, depth)` 의 id-only 변환 결과 (`List<Integer>`)
  - `graph:prerequisites:objs:<conceptId>:<depth>` — `findPrerequisiteConcepts(?, depth)` 결과 (`List<Concept>`, ADR 0005 패턴)
  - `graph:prerequisites:depthmap:<conceptId>:<depth>` — `findPrerequisitesWithDepth(?, depth)` 의 `Map<Integer,Integer>` 변환 결과 (Task 3.4 ProbabilityService 경유 전용)
  - `graph:to-concepts:<conceptId>` — `findToConcepts` 결과 (depth=1 전용, `findPrerequisiteConcepts(?, 1)`과 동치)
- **CTE 경로일 때만 캐시 적용** — Neo4j 경로는 캐시 미적용 (자체 그래프 인덱스 + 결과를 잘못 재사용할 위험 차단)
- TTL 24시간 단일 (CSV 재로드 주기 ≥ 일 단위 가정 — Task 2.2)
- 깊이를 키에 포함하여 depth 2/3/5 결과를 분리

### 보일러플레이트 축소 (선택)

if-else 6줄이 여러 메서드에 반복되므로 helper 메서드 1개로 축소 가능 (선택 사항):

```java
private <T> List<T> cachedOrCompute(String key, Supplier<List<T>> compute) {
    @SuppressWarnings("unchecked")
    List<T> cached = (List<T>) redisUtil.get(key);
    if (cached != null) return cached;
    List<T> result = compute.get();
    redisUtil.set(key, result, TTL_24H);
    return result;
}
```

### 적용 대상 메서드

Task 3.1의 분기 매트릭스에 등장하는 모든 CTE 분기에 동일 패턴 적용. 메서드별 키 prefix는 위 규약 따름.

---

## Task 2.2 — 캐시 TTL / 무효화

- **TTL:** 24시간 (CSV 재로드 주기 ≥ 일 단위 가정)
- **무효화 트리거:**
  - 운영자 수동 무효화 endpoint (관리자 인증 필요) — `RedisUtil`에 prefix 기반 일괄 삭제 메서드를 추가하거나 `KEYS graph:*` + `DEL` (운영 환경 고려해 `SCAN` 권장)
  - 자동 재로드 트리거 불필요 (CSV는 정적 데이터, 자동 적재 로직 부재 — audit 확인됨)

검증 결과:
- ✓ CSV 재로드 코드 자동화 **없음** — `ApplicationRunner`/`@Scheduled`/`csv` 관련 자동 적재 로직 grep 결과 0건. 결론: 운영자 수동 endpoint 1개만 구현하면 충분.
- ✓ Spring Cache 추상화의 `allEntries = true` 동작 검토는 ADR 0003 결정에 따라 비대상 — `RedisUtil` 직접 호출 패턴이라 prefix 기반 삭제로 처리.

---

## Task 3.1 — 분기 적용

### 대상 메서드 (M1 audit + spec-02 audit 확정)

`ConceptService`만이 아니라 `KnowledgeSpaceService`도 `conceptRepository`(Neo4j)를 **직접** 호출(`KnowledgeSpaceService.java:33-34`)하지만, 본 spec은 **KnowledgeSpaceService → ConceptService 경유 리팩토링(B1)** 을 채택한다(분석-결정). 따라서 분기·캐시 패턴은 ConceptService 5개 메서드 한 곳에만 적용되고, KnowledgeSpaceService는 호출 경로만 갈아끼우면 모든 분기·캐시가 자동 상속된다.

**ConceptService 메서드 (5개):**

| 메서드 | 위치 | spec-01 후 분기 상태 | CTE 호출 형태 (id) | CTE 호출 형태 (객체) |
|--------|------|------------------|----------------|----------------|
| `findNodesByConceptId` | `ConceptService.java:50-57` | ✗ | — | 깊이 분기 후 각 호출을 **`findPrerequisiteConcepts(conceptId, 3 or 5)`** 로 (ADR 0005, 본 spec에서 신규 도입) |
| `findNodesIdByConceptIdDepth2` | `ConceptService.java:59-62` | ✗ | `findPrerequisitesWithDepth(conceptId, 2)` → `.map(ConceptDepth::conceptId)` | — |
| `findNodesIdByConceptIdDepth3` | `ConceptService.java:63-71` | ✓ (spec-01 시범) | `findPrerequisitesWithDepth(conceptId, 3)` → `.map(ConceptDepth::conceptId)` | — |
| `findNodesIdByConceptIdDepth5` | `ConceptService.java:72-75` | ✗ | `findPrerequisitesWithDepth(conceptId, 5)` → `.map(ConceptDepth::conceptId)` | — |
| `findToConcepts` | `ConceptService.java:45-48` | ✗ | — | `findPrerequisiteConcepts(conceptId, 1)` (ADR 0005, 신규) |

**KnowledgeSpaceService — B1 경유 리팩토링 (본 spec 결정):**

| 호출 지점 | 현재 | 변경 후 |
|----------|------|---------|
| `KnowledgeSpaceService.java:33` (초등) | `conceptRepository.findNodesIdByConceptIdDepth3(...)` (Neo4j 직접) | `conceptService.findNodesIdByConceptIdDepth3(...)` (ConceptService 경유) |
| `KnowledgeSpaceService.java:34` (그 외) | `conceptRepository.findNodesIdByConceptIdDepth5(...)` (Neo4j 직접) | `conceptService.findNodesIdByConceptIdDepth5(...)` (ConceptService 경유) |

생성자 의존성: `KnowledgeSpaceService`에 `ConceptService` 주입 추가. 기존 `ConceptRepository` 주입은 다른 사용처가 없으므로 제거(grep으로 확인). 의존 그래프는 단방향(KnowledgeSpaceService → ConceptService) 유지 — 순환 없음.

### 분기 패턴 (spec-01 후 갱신)

spec-01 결정에 따라 `MysqlConceptRepository` 인터페이스/Stub은 삭제됐고, CTE 메서드는 `JdbcTemplateConceptRepository`에 누적되어 가드 없는 직접 호출이 표준이다. ID 반환 메서드 표준:

```java
@Value("${mmt.migration.use-mysql-cte-for-graph:false}")
private boolean useMysqlCte;

private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
// ... 생성자 주입 (기존 그대로 — spec-01에서 Optional 제거됨)

@Transactional(readOnly = true)
public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
    if (useMysqlCte) {
        // 캐시 wrap(Task 2.1)을 적용한 최종 형태는 위 § Task 2.1 참조
        return Flux.fromIterable(
            jdbcTemplateConceptRepository.findPrerequisitesWithDepth(conceptId, 3)
                .stream().map(ConceptDepth::conceptId).toList());
    }
    return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
}
```

객체 반환 메서드 표준 (`findNodesByConceptId`, `findToConcepts`):

```java
@Transactional(readOnly = true)
public Flux<ConceptResponse> findToConcepts(int conceptId){
    if (useMysqlCte) {
        // 캐시 키 prefix: graph:to-concepts:<conceptId>
        return Flux.fromIterable(
            ConceptConverter.convertListToConceptResponseList(
                jdbcTemplateConceptRepository.findPrerequisiteConcepts(conceptId, 1)));
    }
    return ConceptConverter.convertToFluxConceptResponse(
        conceptRepository.findToConceptsByConceptId(conceptId));
}
```

핵심 요소:
- 리포지토리: **`JdbcTemplateConceptRepository`** (별도 클래스 아님)
- ID 메서드: `findPrerequisitesWithDepth(int, int)` → `List<ConceptDepth>` (spec-01 도입)
- 객체 메서드: `findPrerequisiteConcepts(int, int)` → `List<Concept>` (ADR 0005 패턴, **본 spec에서 신규 도입** — 아래 § Task 3.1a)
- 가드 없음 — `Optional<>` 주입 제거됨
- `Flux<T>` 반환 시 `Flux.fromIterable`로 동기 결과 래핑하여 시그니처 보존
- `ConceptConverter.convertListToConceptResponseList(List<Concept>)` 는 현재 **부재**(grep 확인: `ConceptConverter.java`는 `convertToFluxConceptResponse(Flux<Concept>)`와 `convertToConceptResponse(Concept)`만 보유) → 본 spec에서 추가

### Task 3.1a — 객체 반환 CTE 메서드 신규 도입 (ADR 0005)

`JdbcTemplateConceptRepository.findPrerequisiteConcepts(int conceptId, int maxDepth)` 를 ADR 0005의 `concepts JOIN chapters` 패턴으로 추가. `RowMapper<Concept>`는 기존 `findOneByConceptId`의 `conceptRowMapper()`와 동일 컬럼을 사용하므로 그대로 재사용 또는 사적 helper로 추출. `conceptSection` 매핑은 ADR 0005에 따라 생략.

CTE 본문은 ADR 0005 §Decision의 SQL 그대로:
```sql
WITH RECURSIVE prerequisite_path AS (
    SELECT concept_id, 0 AS depth FROM concepts WHERE concept_id = ?
    UNION ALL
    SELECT c.concept_id, pp.depth + 1
    FROM prerequisite_path pp
    JOIN knowledge_space ks ON pp.concept_id = ks.from_concept_id
    JOIN concepts c           ON ks.to_concept_id = c.concept_id
    WHERE pp.depth < ?
)
SELECT c.concept_id, c.concept_name, c.concept_description,
       c.concept_chapter_id, c.concept_achievement_id, c.concept_achievement_name,
       ch.school_level, ch.grade_level, ch.semester,
       ch.chapter_main, ch.chapter_sub, ch.chapter_name
FROM (SELECT DISTINCT concept_id FROM prerequisite_path) pp
JOIN concepts c  ON pp.concept_id = c.concept_id
JOIN chapters ch ON c.concept_chapter_id = ch.chapter_id;
```

단위 테스트는 spec-01의 `JdbcTemplateConceptRepositoryCteTest` 인프라(`cte_test_schema.sql` + `cte_test_seed.sql`)를 확장하여 동일 시드에 객체 매핑 검증 케이스 추가.

### `findToConcepts` 처리

실제 Cypher (`ConceptRepository.java:13`):
```cypher
MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)
```

[spec-01 데이터 모델 노트](spec-01-cte-repository-and-indexes.md#-데이터-모델-노트--knowledge_space-엣지-방향성)의 의미 정의에 따라, 이 Cypher는 "X의 직접 선수 1단계"를 반환. **Task 3.1a에서 도입하는 `findPrerequisiteConcepts(conceptId, 1)` 호출로 자연 처리**되며 별도 outgoing CTE 메서드 불필요. 캐시 키는 `graph:to-concepts:<conceptId>` (depth=1 전용 prefix).

호출처: `ConceptController.java:45` (REST 엔드포인트, 사용처 1곳).

---

## Task 3.2 — `.block()` 호출 정리

### 식별된 위치 (전수 조사 완료)

- ✓ `ProbabilityService.java:66` — `conceptIdFlux.collectList().block()` (M1 audit에서 식별)
- ✓ `KnowledgeSpaceService.java:36` — `conceptIdFlux.distinct().collectList().block()` (spec-02 audit에서 추가 식별)

전수 검증 명령:
```bash
grep -rn '\.block()' api/src/main/java/
```
본 spec 착수 시 위 명령을 재실행하여 누락 여부 재확인.

### 처리 방향

| 피처 플래그 상태 | 처리 |
|------------------|------|
| `true` (CTE) | CTE 메서드는 동기 반환 → `.block()` 호출 자체가 불필요해짐. 분기에서 자연 우회 |
| `false` (Neo4j) | 기존 `.block()` 유지 (Neo4j Reactive 결과를 동기 호출부에 맞추기 위해 필요) |

본 spec에서는 `.block()` 코드를 **삭제하지 않고**, 분기 분기점만 조정. Neo4j 완전 제거(spec-03 Task 5.3)에서 코드 자체 삭제.

---

## Task 3.3 — 학교 수준 분기 호환

학교 수준 분기는 두 곳에 존재 (확정):

- `ConceptService.java:57-63` (`findNodesByConceptId` 내부)
- `KnowledgeSpaceService.java:29-34`

매핑은 **이분**이며 동일한 패턴:
```java
String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);
if (schoolLevel.equals("초등")) → depth 3
else                            → depth 5
```

CTE 경로에서도 동일 의미가 보존되어야 한다. Task 3.1의 분기 적용 시 깊이 3·5 두 메서드가 모두 분기되므로 `findNodesByConceptId`의 두 호출 라인이 자동으로 CTE/Neo4j 양쪽 경로를 가지게 된다. 별도 작업 불필요.

KnowledgeSpaceService는 Task 3.1에서 B1(ConceptService 경유)을 채택했으므로 분기·캐시가 자동 상속되며, 학교 수준 분기 자체에 추가 작업 불필요.

---

## Task 3.4 — `LogicUtil.bfs` 제거 + ProbabilityService 마이그레이션 (spec-01 A3 위임분)

### 배경

spec-01의 A3 발견: `ProbabilityService.create`(`ProbabilityService.java:65-68`)는 `findNodesIdByConceptIdDepth3` 결과 `List<Integer>`를 `LogicUtil.bfs(conceptId, conceptIdList)`에 그대로 넘긴다. `LogicUtil.buildGraph`(`LogicUtil.java:12-29`)는 **리스트의 인접한 두 원소를 엣지로 해석**하여 그래프를 재구성한 뒤 BFS로 depth를 계산 — Neo4j Cypher path traversal 결과 시퀀스를 전제한 코드.

CTE의 `GROUP BY concept_id, MIN(depth)` 결과는 unique 노드 집합만 보장하고 path 정보는 손실 → `Probability.to_concept_depth`에 잘못된 값 저장. spec-01에서 시그니처에 `depth`를 포함한 이유.

### 처리

1. **ConceptService에 헬퍼 추가**:
   ```java
   @Transactional(readOnly = true)
   public Map<Integer, Integer> findPrerequisitesAsDepthMap(int conceptId, int maxDepth) {
       if (!useMysqlCte) {
           // Neo4j 경로는 기존 LogicUtil.bfs 호출이 사라지므로 동등 기능 직접 구현 또는
           // CTE 미사용 환경에서 호출하지 않도록 호출부 가드(현재 ProbabilityService는 depth 3 한 곳)
           List<Integer> ids = conceptRepository
               .findNodesIdByConceptIdDepth3(conceptId).collectList().block();
           return LogicUtil.bfs(conceptId, ids);  // 단, Task 3.4 완료 후 LogicUtil 삭제 → Neo4j 경로 자체가 함께 폐기
       }
       String key = "graph:prerequisites:depthmap:" + conceptId + ":" + maxDepth;
       @SuppressWarnings("unchecked")
       Map<Integer, Integer> cached = (Map<Integer, Integer>) redisUtil.get(key);
       if (cached != null) return cached;

       Map<Integer, Integer> result = jdbcTemplateConceptRepository
           .findPrerequisitesWithDepth(conceptId, maxDepth)
           .stream()
           .collect(Collectors.toMap(ConceptDepth::conceptId, ConceptDepth::depth));
       redisUtil.set(key, result, TTL_24H);
       return result;
   }
   ```

2. **ProbabilityService.create 마이그레이션** (`ProbabilityService.java:62-75`):
   ```java
   for (Probability probability : depth0) {
       int conceptId = probability.getConceptId();
       Map<Integer, Integer> depthDic =
           conceptService.findPrerequisitesAsDepthMap(conceptId, 3);
       for (Map.Entry<Integer, Integer> entry : depthDic.entrySet()) {
           Probability prerequisite = new Probability();
           prerequisite.setAnswerId(probability.getAnswerId());
           prerequisite.setConceptId(entry.getKey());
           prerequisite.setToConceptDepth(entry.getValue());
           depthN.add(prerequisite);
       }
   }
   ```
   - 기존 `conceptService.findNodesIdByConceptIdDepth3(conceptId)` 호출과 `.collectList().block()`과 `LogicUtil.bfs(...)` 3줄이 단일 헬퍼 호출로 치환됨
   - `.block()` 호출 자체가 사라짐 (Task 3.2의 `ProbabilityService.java:66` 자동 해소)

3. **삭제 (spec-02 PR 후반부 또는 별도 task 커밋)**:
   - `api/src/main/java/com/mmt/api/util/LogicUtil.java`
   - `api/src/test/java/com/mmt/api/util/LogicUtilTest.java`
   - `ProbabilityService` import 정리

### Neo4j 경로 처리 (플래그 false 상태)

플래그 OFF 상태에서 `findPrerequisitesAsDepthMap`을 호출하려면 Neo4j 경로에서도 `Map<Integer, Integer>`를 만들어야 한다. 두 가지 선택지:

- **간이 처리**: 플래그 false 상태에선 ProbabilityService를 호출하지 않는 것이 보장되지 않음 → 동등성 유지를 위해 Neo4j 경로에서도 `findNodesIdByConceptIdDepth3` + `LogicUtil.bfs` 조합을 헬퍼 내부에 유지. spec-03 Neo4j 폐기 시 LogicUtil도 함께 삭제.
- **단순화**: 플래그 ON 가정 — Neo4j 경로 호출 시 `IllegalStateException` (로그 후). 운영 정책으로 보장.

**결정**: 간이 처리(LogicUtil은 spec-03 Neo4j 폐기 시 함께 삭제) — 분기 동등성 유지를 위한 가장 안전한 경로. 본 spec에서는 `LogicUtil` 코드 자체는 보존하고, 단지 ProbabilityService의 외부 호출만 헬퍼로 옮긴다. **`LogicUtilTest`도 spec-03까지 보존.** 단, 메모리 경고("플래그 ON 금지")는 본 spec 완료 시 해소됨 — ProbabilityService 경로가 더 이상 CTE 결과를 `LogicUtil.bfs`에 잘못 흘리지 않기 때문.

### 회귀 테스트

- ProbabilityService unit test(`ProbabilityServiceTest`)에서 `findNodesIdByConceptIdDepth3` mock을 `findPrerequisitesAsDepthMap` mock으로 변경
- 신규 통합 테스트: CTE 시드(`cte_test_seed.sql`의 300→310→320→330 체인)로 `findPrerequisitesAsDepthMap(300, 3)` 호출 시 `{300:0, 310:1, 320:2, 330:3}` 단정
- spec-01 A3 회귀 케이스 보강: 다중 경로 시드(A→B→D, A→C→D)에서 D가 `MIN(depth)`인 1로 단정

---

## 테스트

### 분기 동등성 테스트

분기 양쪽 (`use-mysql-cte-for-graph: true | false`)에서 결과가 동등한지 검증:

```java
@SpringBootTest(properties = "mmt.migration.use-mysql-cte-for-graph=true")
class ConceptServiceCteIntegrationTest { ... }

@SpringBootTest(properties = "mmt.migration.use-mysql-cte-for-graph=false")
class ConceptServiceNeo4jIntegrationTest { ... }
```

[검증 필요] M1에서 정착된 통합 테스트 패턴 (Testcontainers MySQL + Neo4j 동시 기동) 활용 가능 여부.

### 캐시 동작 테스트

- 동일 입력 2회 호출 → 2번째 호출은 리포지토리 미접근
- 캐시 무효화 후 → 다시 리포지토리 접근
- 피처 플래그 `false` (Neo4j) 경로 → 캐시 우회

### N+1 회귀 검증

ADR 0002 §3에 따라 Hibernate Statistics API로 N+1 회귀 확인 (JPA 리포지토리 한정). 본 spec의 변경은 JdbcTemplate 경로이므로 직접 영향 없으나, ConceptService에서 다른 JPA 리포지토리 호출이 변경 영향을 받지 않는지 확인.

---

## 완료 기준

- [ ] ConceptService 5개 메서드 분기 적용 — id 3개는 `findPrerequisitesWithDepth` + `.map(ConceptDepth::conceptId)`, 객체 2개(`findNodesByConceptId`/`findToConcepts`)는 `findPrerequisiteConcepts` (Task 3.1a, ADR 0005)
- [ ] **Task 3.1a — `findPrerequisiteConcepts(int, int)` + `RowMapper<Concept>` 신규 도입** (`JdbcTemplateConceptRepository`)
- [ ] KnowledgeSpaceService B1 경유 리팩토링 (ConceptService 주입, ConceptRepository 직접 호출 2지점 교체)
- [ ] 캐시 적용 (4종 prefix 분리: `graph:prerequisites:ids:*`, `:objs:*`, `:depthmap:*`, `graph:to-concepts:*`)
- [ ] 무효화 endpoint (관리자 인증, RedisUtil SCAN+DEL prefix 메서드 추가)
- [ ] **Task 3.4 — `ProbabilityService.create` 마이그레이션** (`findPrerequisitesAsDepthMap` 헬퍼 사용, `LogicUtil.bfs` 외부 호출 0으로)
- [ ] `.block()` 호출 전수 목록 PR 설명에 첨부 — `ProbabilityService.java:66`은 Task 3.4 마이그레이션으로 자동 해소, `KnowledgeSpaceService.java:36`은 spec-03까지 보존
- [ ] 분기 양쪽 통합 테스트 통과
- [ ] 캐시 히트/미스/invalidate 단위 테스트 통과
- [ ] ProbabilityService depthmap 회귀 테스트 (다중 경로 시드에서 `MIN(depth)` 단정)
- [ ] PR 설명에 분기 매트릭스(메서드 × 플래그 × 결과 동등성) 첨부
- [ ] Analyze-Before-Change 결과(영향받는 호출부, 롤백 시나리오) PR 설명 포함
- [ ] 메모리 경고("플래그 ON 금지 — spec-02 LogicUtil.bfs 제거 전") 해소 확인 — Task 3.4 완료 후 PR 설명에 명시

---

## 비범위 (다른 spec에서 처리)

- ID 반환 CTE 메서드 자체 → spec-01 완료
- M1 스냅샷 대비 정확성 검증 → spec-03
- 성능 측정 + 캐시 히트율 측정 → spec-03
- Cytoscape.js / BFS 호환 검증 → spec-03
- `KnowledgeSpaceService.java:36`의 `.block()` 코드 삭제 → spec-03 Task 5.3 (Neo4j 폐기와 동시)
- `LogicUtil`/`LogicUtilTest` 코드 자체 삭제 → spec-03 (Neo4j 경로 폐기와 동시 — 본 spec에선 외부 호출만 제거하고 코드 보존)
- Neo4j 인프라 제거 → spec-03
