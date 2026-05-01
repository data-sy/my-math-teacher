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
  - `findNodesIdByConceptIdDepth3`에 분기 시범 적용 완료
- ✓ Spring Cache 미도입 상태 확인 — `@EnableCaching`/`@Cacheable`/`@CacheEvict`/`RedisCacheManager`/`CacheManager` 모두 부재. Redis는 `RedisUtil`(`api/src/main/java/com/mmt/api/util/RedisUtil.java`)을 통한 직접 호출로만 사용 중(JWT 토큰 저장 용도). 본 spec의 Task 2.1은 ADR 0003에 따라 RedisUtil 직접 호출 패턴으로 진행한다.
- ✓ M1 분기 패턴 확인 (`ConceptService.java:73-80`):
  ```java
  @Transactional(readOnly = true)
  public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
      if (useMysqlCte && mysqlConceptRepository.isPresent()) {
          return Flux.fromIterable(
              mysqlConceptRepository.get().findPrerequisiteConceptIds(conceptId, 3));
      }
      return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
  }
  ```
  핵심 요소: ① `MysqlConceptRepository`(JdbcTemplate 아닌 별도 클래스) ② 통합 시그니처 `findPrerequisiteConceptIds(int, int)` ③ `Optional` 가드 `isPresent()` ④ `Flux.fromIterable` 래핑으로 시그니처 보존. 본 spec은 이 패턴을 다른 메서드에도 동일하게 확산한다.

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
    private final Optional<MysqlConceptRepository> mysqlConceptRepository;
    // RedisUtil.set(key, o, duration) 의 duration 은 MILLISECONDS 단위 (TimeUnit.MILLISECONDS).
    // toSeconds() 사용 시 86.4초만 캐싱되어 의도(24h)와 불일치.
    private static final long TTL_24H = Duration.ofHours(24).toMillis();

    @Value("${mmt.migration.use-mysql-cte-for-graph:false}")
    private boolean useMysqlCte;

    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId) {
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            String key = "graph:prerequisites:ids:" + conceptId + ":3";
            @SuppressWarnings("unchecked")
            List<Integer> cached = (List<Integer>) redisUtil.get(key);
            if (cached != null) return Flux.fromIterable(cached);

            List<Integer> result = mysqlConceptRepository.get()
                .findPrerequisiteConceptIds(conceptId, 3);
            redisUtil.set(key, result, TTL_24H);
            return Flux.fromIterable(result);
        }
        return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
    }
}
```

설계 메모:
- 키 prefix 규약 (ADR 0003):
  - `graph:prerequisites:ids:<conceptId>:<depth>` — `findPrerequisiteConceptIds` 결과
  - `graph:prerequisites:objs:<conceptId>:<depth>` — `findPrerequisiteConcepts` 결과 (객체 반환)
  - `graph:to-concepts:<conceptId>` — `findToConcepts` 결과 (depth=1 전용, `findPrerequisiteConcepts(?, 1)`과 동일)
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

`ConceptService`만이 아니라 `KnowledgeSpaceService`도 `conceptRepository`(Neo4j)를 **직접** 호출(`KnowledgeSpaceService.java:29-34`)하므로 분기 대상에 포함한다.

**ConceptService 메서드 (5개):**

| 메서드 | 위치 | M1에서 분기 적용 | CTE 호출 형태 |
|--------|------|------------------|----------------|
| `findNodesByConceptId` | `ConceptService.java:57-63` | ✗ | 깊이 분기(이분: 초등=3, else=5) 그대로 두고 내부 두 호출 각각에 분기 |
| `findNodesIdByConceptIdDepth2` | `ConceptService.java:66-68` | ✗ | `findPrerequisiteConceptIds(conceptId, 2)` |
| `findNodesIdByConceptIdDepth3` | `ConceptService.java:73-80` | ✓ (시범) | `findPrerequisiteConceptIds(conceptId, 3)` |
| `findNodesIdByConceptIdDepth5` | `ConceptService.java:81-84` | ✗ | `findPrerequisiteConceptIds(conceptId, 5)` |
| `findToConcepts` | `ConceptService.java:52-53` | ✗ | spec-01의 `findPrerequisiteConcepts(conceptId, 1)` 호출로 처리 |

**KnowledgeSpaceService 메서드 (2개 호출 지점):**

| 호출 지점 | M1에서 분기 적용 | CTE 호출 형태 |
|----------|------------------|----------------|
| `KnowledgeSpaceService.java:33` (초등 → `findNodesIdByConceptIdDepth3`) | ✗ | `conceptRepository` 직접 호출 → ConceptService 경유로 변경하거나 동일 분기 적용 |
| `KnowledgeSpaceService.java:34` (그 외 → `findNodesIdByConceptIdDepth5`) | ✗ | 위와 동일 |

권장 — KnowledgeSpaceService를 `ConceptService.findNodesIdByConceptIdDepthN`을 경유하도록 리팩토링하면 ConceptService 5개 메서드의 분기가 자동 상속되어 KnowledgeSpaceService에 별도 분기 로직이 불필요. 본 spec 착수 시 채택 여부 결정.

### 분기 패턴

M1에서 `findNodesIdByConceptIdDepth3`에 적용한 패턴(`ConceptService.java:73-80`)을 표준으로 채택:

```java
@Value("${mmt.migration.use-mysql-cte-for-graph:false}")
private boolean useMysqlCte;

private final Optional<MysqlConceptRepository> mysqlConceptRepository;
// ... 생성자 주입

@Transactional(readOnly = true)
public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
    if (useMysqlCte && mysqlConceptRepository.isPresent()) {
        return Flux.fromIterable(
            mysqlConceptRepository.get().findPrerequisiteConceptIds(conceptId, 3));
    }
    return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
}
```

핵심 요소:
- 리포지토리: **`MysqlConceptRepository`** (M1에서 도입한 스텁, JdbcTemplate 아님)
- 메서드 시그니처: **통합 형태** `findPrerequisiteConceptIds(int conceptId, int maxDepth)` (편의 메서드 미사용 — spec-01의 편의 메서드 도입 결정도 본 패턴에 맞춰 통합 단일로 통일 필요. spec-01 Task 1.2 참조)
- Optional 가드 `isPresent()` 필수 — 플래그 false 상태에서는 bean 미존재
- `Flux<T>` 반환 시 `Flux.fromIterable`로 동기 결과 래핑하여 시그니처 보존

### `findToConcepts` 처리

실제 Cypher (`ConceptRepository.java:13`):
```cypher
MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)
```

[spec-01 데이터 모델 노트](spec-01-cte-repository-and-indexes.md#-데이터-모델-노트--knowledge_space-엣지-방향성)의 의미 정의에 따라, 이 Cypher는 "X의 직접 선수 1단계"를 반환. spec-01 Task 1.1의 쿼리 1과 동일하므로 **`findPrerequisiteConcepts(conceptId, 1)` 호출로 자연 처리**되며 별도 outgoing CTE 메서드 도입 불필요.

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

KnowledgeSpaceService 측은 ConceptService 경유 리팩토링 채택 여부에 따라 분기되거나(직접 호출 유지 시) 자동 상속(경유 시) — Task 3.1 결정에 따른다.

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

- [ ] 5개 그래프 메서드 모두 분기 적용 (또는 `findToConcepts` 제외 정책 명시)
- [ ] 캐시 적용 (메서드별 키 prefix 분리)
- [ ] CSV 재로드 시점 식별 + 무효화 경로 구현
- [ ] `.block()` 호출 전수 목록 PR 설명에 첨부 (제거는 spec-03)
- [ ] 분기 양쪽 통합 테스트 통과
- [ ] 캐시 히트/미스 단위 테스트 통과
- [ ] PR 설명에 분기 매트릭스(메서드 × 플래그 × 결과 동등성) 첨부
- [ ] Analyze-Before-Change 결과(영향받는 호출부, 롤백 시나리오) PR 설명 포함

---

## 비범위 (다른 spec에서 처리)

- CTE 메서드 자체 → spec-01
- M1 스냅샷 대비 정확성 검증 → spec-03
- 성능 측정 + 캐시 히트율 측정 → spec-03
- Cytoscape.js / BFS 호환 검증 → spec-03
- `.block()` 코드 삭제 → spec-03 Task 5.3
- Neo4j 인프라 제거 → spec-03
