# Spec 01: CTE 리포지토리 + 인덱스

**상위 마일스톤:** Milestone 2 (Neo4j → MySQL CTE 마이그레이션)
**대상 Phase:** Phase 1
**예상 소요:** 2일
**선행 spec:** —

---

## 범위

`JdbcTemplateConceptRepository`에 재귀 CTE 메서드를 추가하고, 성능 확보에 필요한 인덱스를 도입한다. 본 spec은 **데이터 레이어에 한정**되며, 서비스 레이어 통합·피처 플래그 분기·캐싱은 spec-02, 검증·출시·폐기는 spec-03 범위다.

---

## 📌 데이터 모델 노트 — `knowledge_space` 엣지 방향성

> **본 노트는 M2 전 spec(01·02·03)의 모든 SQL과 테스트 단정에 적용되는 의미 정의다. SQL 작성·해석 시 항상 이 노트를 기준으로 한다.**

### 컬럼 의미 (코드와 시드로부터 도출됨, 결정이 아닌 사실)

| 컬럼 | 의미 |
|---|---|
| `from_concept_id` | **후수** (해당 엣지에서 학습 시간상 뒤에 오는 개념) |
| `to_concept_id` | **선수** (해당 엣지에서 학습 시간상 앞서야 하는 개념) |

### 근거 1 — Neo4j 적재 코드

`api/src/test/java/com/mmt/api/performance/GraphQueryPerformanceTest.java`의 `LOAD CSV`:

```cypher
LOAD CSV WITH HEADERS FROM 'file:///knowledge_space.csv' AS row
MATCH (a:concept {concept_id: toInteger(row.to_concept_id)}),
      (b:concept {concept_id: toInteger(row.from_concept_id)})
CREATE (a)-[:KNOWLEDGE_SPACE]->(b)
```

엣지는 **`to_concept_id 노드 → from_concept_id 노드`** 방향으로 생성된다. 즉 그래프 화살표의 방향과 컬럼 이름의 방향이 정반대.

### 근거 2 — Cypher 쿼리 의미

`ConceptRepository.findToConceptsByConceptId`:

```cypher
MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)
```

`m`으로 들어오는 엣지의 시작점 `n`을 반환. M1 baseline 작성자가 이를 "선수 개념 조회 (들어오는 엣지)"로 명시 정의(`docs/benchmark/milestone-1-baseline.md:56`).

위 두 근거를 결합:
- 그래프 엣지: `to_id_node → from_id_node`
- "들어오는 엣지의 시작점 = 선수" → 그래프에서 화살표가 출발하는 쪽이 선수
- 따라서 **`to_concept_id` = 선수**, **`from_concept_id` = 후수**

### Trace — 시드 한 행으로 의미 검증

시드 `(id=1, to_concept_id=4659, from_concept_id=3)`:

```mermaid
graph LR
    T["concept 4659: to_concept_id, 선수"]
    F["concept 3: from_concept_id, 후수"]
    T -->|KNOWLEDGE_SPACE| F
```

- Cypher `MATCH (n)-[r]->(m{concept_id: 3}) RETURN n` → `[4659]` (concept 3의 선수)
- 의미: "concept 3을 학습하기 전에 concept 4659를 먼저 학습해야 한다"

### MySQL CTE에의 적용

"X의 선수를 N단계까지 거슬러 올라가기" 쿼리:
- 시작 노드의 `concept_id`를 **현재 노드 = 후수** 위치로 잡음 → `pp.concept_id = ks.from_concept_id`
- 다음 단계로 **선수**(`ks.to_concept_id`)를 가져옴

```sql
JOIN knowledge_space ks ON pp.concept_id = ks.from_concept_id  -- 현재=후수
JOIN concepts        c  ON ks.to_concept_id = c.concept_id     -- 다음=선수
```

이 JOIN 패턴은 spec-01·02·03의 모든 CTE와 ADR 0005의 객체 반환 SQL 예시에 동일하게 적용된다.

---

## 사전 조건 / 검증 필요

- ✓ `api/sql/select.sql:285`에 CTE 프로토타입 실재 확인 (`WITH RECURSIVE path AS ...`, conceptId=4979 하드코딩). 본 spec의 출발점.
- ✓ `JdbcTemplateConceptRepository` 위치: `api/src/main/java/com/mmt/api/repository/concept/JdbcTemplateConceptRepository.java`. 그래프 메서드는 부재. 기존 메서드 명명 패턴은 `find{무엇}By{기준}` (예: `findOneByConceptId`, `findAllByChapterId`, `findSchoolLevelByConceptId`, `findSkillIdByConceptId`). **본 spec에서 신규 도입할 CTE 메서드는 이 클래스에 누적**한다. M1에서 토글 검증용으로 도입된 `MysqlConceptRepository` 인터페이스 + `MysqlConceptRepositoryStub`은 역할 종료로 본 spec에서 **삭제**한다 (Analyze-Before-Change 결정).
- ✓ 스키마 확인 (`api/sql/create.sql:52-58`): `knowledge_space(knowledge_space_id INT PK, to_concept_id INT FK, from_concept_id INT FK)`. `concepts.concept_id`도 INT.
- ✓ DB 마이그레이션 도구 미도입 — `api/sql/`의 수동 SQL 스크립트로 관리(DDL은 `create.sql`, 시드는 `insert_*.sql`). 본 spec의 적용 방식 (Analyze-Before-Change 결정):
  - **인덱스 추가** → `api/sql/add_knowledge_space_indexes.sql` 신규 스크립트 (Task 1.3)
  - **`cte_max_recursion_depth = 10`** → Hikari `connection-init-sql` (`application.yml`, Task 1.4)

### ConceptService ↔ ConceptRepository ↔ CTE 매핑 표

ConceptService(5개 그래프 메서드)와 ConceptRepository(6개 Cypher 그래프 메서드)의 매핑. 본 spec(spec-01)은 **ID + depth 반환 메서드 한정**이고, 객체(Concept) 반환은 spec-02에서 ADR 0005의 JOIN 패턴으로 처리:

| ConceptService (5) | ConceptRepository (6) | 깊이 | 반환 타입 | 처리 spec |
|---|---|---|---|---|
| `findNodesByConceptId` (초등) | `findNodesByConceptIdDepth3` | 3 | `Flux<Concept>` | spec-02 (ADR 0005) |
| `findNodesByConceptId` (그 외) | `findNodesByConceptIdDepth5` | 5 | `Flux<Concept>` | spec-02 (ADR 0005) |
| `findNodesIdByConceptIdDepth2` | `findNodesIdByConceptIdDepth2` | 2 | `Flux<Integer>` | **spec-01** — `findPrerequisitesWithDepth(?, 2)` |
| `findNodesIdByConceptIdDepth3` | `findNodesIdByConceptIdDepth3` | 3 | `Flux<Integer>` | **spec-01** — `findPrerequisitesWithDepth(?, 3)` |
| `findNodesIdByConceptIdDepth5` | `findNodesIdByConceptIdDepth5` | 5 | `Flux<Integer>` | **spec-01** — `findPrerequisitesWithDepth(?, 5)` |
| `findToConcepts` | `findToConceptsByConceptId` | 1 | `Flux<Concept>` | spec-02 (ADR 0005, 깊이 1 == 직접 선수) |

본 spec은 위 매핑 중 **ID 반환 3 row**에 대응하는 단일 CTE 메서드를 도입한다:
- `findPrerequisitesWithDepth(int conceptId, int maxDepth)` — `List<ConceptDepth>` 반환 (`record ConceptDepth(int conceptId, int depth)`)
- 반환 타입에 `depth`를 포함하는 이유는 본 spec 하단의 [A3 발견](#a3-발견--logicutilbfs-순서-의존성) 참조. 단순 ID 집합만 반환하면 `ProbabilityService` 경로의 `LogicUtil.bfs`가 silent regression을 일으킴.
- ConceptService 분기 호출부는 `.stream().map(ConceptDepth::conceptId).toList()` → `Flux.fromIterable`로 기존 `Flux<Integer>` 시그니처 보존. `LogicUtil.bfs` 대체는 spec-02 책임.

객체 반환 메서드(`findPrerequisiteConcepts`) + `RowMapper<Concept>` 매핑은 spec-02에서 ADR 0005의 `concepts JOIN chapters` 패턴으로 처리.

---

## Task 1.1 — 재귀 CTE 메서드 도입

> JOIN 방향은 위 [데이터 모델 노트](#-데이터-모델-노트--knowledge_space-엣지-방향성) 의미 정의를 따른다. `from_concept_id`=후수, `to_concept_id`=선수.

### Neo4j 쿼리 3종 → MySQL CTE 매핑

`api/sql/select.sql:285`의 CTE 프로토타입을 기반으로 깊이 매개변수만 변수화하여 통합 메서드 형태로 정착시킨다.

**쿼리 1 — 직접 선수 개념 (depth 1)**

Neo4j 원본:
```cypher
MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)
```

MySQL 변환 (X의 직접 선수: `from=X`인 행의 `to`):
```sql
SELECT c.* FROM concepts c
JOIN knowledge_space ks ON c.concept_id = ks.to_concept_id
WHERE ks.from_concept_id = ?
```

**쿼리 2 — 깊이 N 재귀 탐색 (핵심)**

Neo4j 원본:
```cypher
MATCH (n)-[*0..N]->(m {concept_id: $conceptId}) RETURN (n)
```

MySQL 변환 (시작 노드를 후수로 잡고, 선수 방향으로 N단계 거슬러 올라감):
```sql
WITH RECURSIVE prerequisite_path AS (
    SELECT concept_id, 0 AS depth
    FROM concepts WHERE concept_id = ?

    UNION ALL

    SELECT c.concept_id, pp.depth + 1
    FROM prerequisite_path pp
    JOIN knowledge_space ks ON pp.concept_id = ks.from_concept_id  -- 현재=후수
    JOIN concepts c           ON ks.to_concept_id = c.concept_id   -- 다음=선수
    WHERE pp.depth < ?
)
SELECT concept_id, MIN(depth) AS depth
FROM prerequisite_path
GROUP BY concept_id
```

**DISTINCT 대신 `GROUP BY concept_id, MIN(depth)`** — 다중 경로로 같은 노드에 여러 depth로 도달 가능 (예: A→B→C가 1단계, A→X→Y→C가 2단계). 두 경로 모두 path에 누적되므로 외부 SELECT에서 `MIN(depth)`로 최단 거리만 살린다. 단순 `SELECT DISTINCT concept_id`로는 depth가 path 순회 우연에 따라 결정됨 — `ProbabilityService` 경로의 정확성 위해 최단 거리 의미 명시화 필수 (A3 발견).

쿼리 1은 쿼리 2의 `maxDepth=1` 호출과 동일 결과이므로 **별도 메서드 불필요** — `findPrerequisitesWithDepth(?, 1)` 호출로 처리한다.

**쿼리 3 — 경로 상의 concept_id + depth 추출 (BFS 대체 입력용)**

쿼리 2와 동일한 결과셋. `ConceptDepth` record로 (id, depth) 쌍을 직접 반환하여 spec-02에서 `LogicUtil.bfs` 호출을 제거할 때 입력으로 그대로 사용.

### 메서드 시그니처 (Analyze-Before-Change 결정으로 변경됨)

M1에서 토글 검증용으로 도입한 `MysqlConceptRepository` 인터페이스 + `MysqlConceptRepositoryStub`은 **삭제**한다. 대신 기존 `JdbcTemplateConceptRepository`에 신규 메서드를 누적한다.

**신규 record** (`api/src/main/java/com/mmt/api/repository/concept/ConceptDepth.java`):
```java
public record ConceptDepth(int conceptId, int depth) {}
```

**신규 메서드** (`JdbcTemplateConceptRepository`에 누적):
```java
public List<ConceptDepth> findPrerequisitesWithDepth(int conceptId, int maxDepth);
```

**구현 위치 결정 근거**:
- M1의 인터페이스 + Stub은 "토글이 코드 경로를 갈아끼우는가" 검증이 목적이었고 M2 진입 시점에 역할 종료
- `JdbcTemplateConceptRepository`는 이미 concept 도메인의 JdbcTemplate 쿼리 집합지 → CTE 메서드도 동일 카테고리, 자연스러운 누적
- 인터페이스 제거로 `ConceptService`의 `Optional<MysqlConceptRepository>` 주입 + `isPresent()` 가드도 함께 제거 → 분기 코드 단순화

**ConceptService 분기 단순화** (`ConceptService.java:73-80` 정리):
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

`LogicUtil.bfs` 대체는 spec-02 책임 (A3 발견 참조). 본 spec에서는 시그니처에 depth를 포함시켜 spec-02 작업이 단순 "BFS 제거 + depth 직접 사용" 형태가 되도록 준비만 한다.

객체 반환 메서드(`findPrerequisiteConcepts`)는 spec-02에서 ADR 0005의 `concepts JOIN chapters` 패턴으로 추가한다.

---

## Task 1.2 — 편의 메서드 도입 결정 (변경됨: 미도입)

기존 Neo4j 쪽이 `findNodesIdByConceptIdDepth2/3/5`로 분리된 이유는 Cypher의 깊이 매개변수가 리터럴만 허용하기 때문. CTE는 매개변수화 가능하므로 통합 메서드 하나로 충분하다.

**결정: 편의 메서드 미도입.** M1 시범 분기 패턴(`ConceptService.java:73-80`)이 통합 메서드 + 깊이 인자 형태를 채택했으므로, 본 spec도 통합 메서드 `findPrerequisitesWithDepth(conceptId, N)`만 도입하여 spec-02의 분기 적용과 정합성을 유지한다.

호출부 사용 깊이 확인 결과: 2/3/5 세 가지만 사용 (그 외 값 없음). 통합 메서드의 `maxDepth` 인자로 모두 처리 가능.

---

## Task 1.3 — 인덱스 도입 (Analyze-Before-Change 결과 확정)

**`SHOW INDEX FROM knowledge_space` 실측 결과** (2026-05-11, 시드 3446 row):

| Key_name | Column | Cardinality | 출처 |
|---|---|---|---|
| `PRIMARY` | `knowledge_space_id` | 3446 | DDL `PRIMARY KEY` |
| `to_concept_id` | `to_concept_id` | 1054 | FK 자동 인덱스 |
| `from_concept_id` | `from_concept_id` | 1428 | FK 자동 인덱스 |

단일 인덱스 2개는 이미 FK 자동 인덱스로 존재. **신규 추가는 복합 인덱스 1개만**:

```sql
CREATE INDEX idx_knowledge_space_composite
    ON knowledge_space(from_concept_id, to_concept_id);
```

**spec 1차 작성의 단일 인덱스 2개(`idx_knowledge_space_from`, `idx_knowledge_space_to`)는 추가하지 않음**:
- 동등 인덱스가 이미 존재 → 추가 시 중복
- FK 제약이 자동 인덱스에 의존하므로 DROP 시도 시 의존성 거부됨 → 명명 일관성을 위한 재구축은 운영 위험 대비 이득 없음

**복합 인덱스의 의미**: CTE 재귀 단계 JOIN(`ks.from_concept_id = pp.concept_id`, SELECT `ks.to_concept_id`)에서 covering index로 작동 → `knowledge_space`에 대한 row 페치 0.

**적용 위치**: 마이그레이션 도구 미도입(사전 조건 참조) → `api/sql/add_knowledge_space_indexes.sql` 신규 스크립트로 추가하고 PR에 적용 절차 명시.

**EXPLAIN 검증**: 쿼리 2의 실행 계획에서 재귀 단계마다 `idx_knowledge_space_composite`이 사용되는지 (Extra에 `Using index` 표시 여부) 확인 후 PR 설명에 첨부.

---

## Task 1.4 — 재귀 깊이 설정 (Analyze-Before-Change 결과 확정)

MySQL 기본값 `cte_max_recursion_depth = 1000`은 충분하지만, 안전상 명시적 설정. 최대 깊이 5에 충분하며 데이터 이상 시 무한 루프를 빠르게 차단.

**적용 위치 확정: (A) Hikari `connection-init-sql`** (`application.yml`).

근거:
- 커넥션 단위 격리 → 운영 측 영향 0
- 시크릿 아니므로 `application.yml`에 두면 모든 프로파일(`securelocal`, `test`) 자동 상속
- Testcontainers MySQL도 자동 적용 (별도 설정 불필요)
- GLOBAL 설정(B)은 다른 세션·다른 앱에 영향, 메서드 호출 시점(C)은 매 호출마다 네트워크 round-trip 추가

```yaml
# application.yml
spring:
  datasource:
    hikari:
      connection-init-sql: "SET SESSION cte_max_recursion_depth = 10"
```

---

## Task 1.5 — 단위 테스트 (Analyze-Before-Change 결과 확정)

테스트 위치: `api/src/test/java/com/mmt/api/repository/concept/JdbcTemplateConceptRepositoryCteTest.java`
사용 인프라: M1의 Testcontainers MySQL + `application-test.yml` (ADR 0002 §2)
시드 파일: 신규 `api/src/test/resources/cte_test_seed.sql` (test classpath, `@Sql(scripts="classpath:cte_test_seed.sql")` 로 참조)

**시드 데이터 정책**: prod `insert_*.sql` 재활용 대신 신규 작성. 이유: 단위 테스트는 의도된 케이스(다중 경로/2-cycle/빈 결과)를 핀포인트로 검증해야 디버깅 비용이 시드 크기에 비례하지 않음. prod 시드 재활용은 spec-03 회귀 테스트에서.

테스트 케이스 (`findPrerequisitesWithDepth` 한정):

| 케이스 | 입력 | 기대 결과 |
|---|---|---|
| depth 0 | 임의 conceptId | 자기 자신 1개 (depth=0) |
| depth 1 | 직접 선수 1개 존재하는 conceptId | 자기 자신 + 직접 선수, depth 각각 0, 1 |
| depth N 다단계 | 깊이 3 이상 체인 | 모든 노드, 각자 최단 depth |
| 빈 결과 | 존재하지 않는 conceptId | 빈 리스트 |
| 음수 깊이 | maxDepth=-1 | **`IllegalArgumentException`** (방어적 가드, 음수만 차단; depth 0은 spec상 유효) |
| 다중 경로 | A→B→D, A→C→D 양쪽 존재 | D는 `MIN(depth)`로 1번만 등장 |
| **2-cycle** | A↔B 양방향 엣지 | 무한 누적 없이 종료, A의 선수 탐색 결과에 자기 자신 미포함 (anchor에서 depth=0으로 1번만, 재귀에선 다른 conceptId만 들어오는 시드 설계) |

**A2 발견 — 시드 데이터 검사 결과**:
- 자기 루프(`to == from`): 0건
- 2-cycle (A→B, B→A): 20+ 쌍 (예: 605↔606, 7913↔7912, 5835↔5836). 이는 데이터 입력 정확성의 별도 이슈이나, CTE는 `pp.depth < ?` 종료 조건 + `MIN(depth)` 집계로 안전. 단 테스트 케이스에 명시적으로 포함하여 회귀 가드.

객체 반환 메서드(`findPrerequisiteConcepts`)의 단위 테스트는 spec-02에서 추가.

---

## A3 발견 — `LogicUtil.bfs` 순서 의존성

Analyze-Before-Change에서 발견된 잠재적 silent regression. 본 spec 시그니처 결정의 핵심 근거.

### 발견 내용

`ProbabilityService.create`(`ProbabilityService.java:65`)는 `ConceptService.findNodesIdByConceptIdDepth3` 결과 `List<Integer>`를 `LogicUtil.bfs(conceptId, conceptIdList)`에 그대로 넘긴다. `LogicUtil.buildGraph`는 다음과 같이 동작:

```java
for (int i = 1; i < integerList.size() - 1; i++) {
    int current = integerList.get(i);
    int next = integerList.get(i + 1);
    // current-next 무방향 엣지 추가
}
```

즉 **리스트의 인접한 두 원소를 엣지로 해석**하여 그래프를 재구성한 뒤 BFS로 depth를 계산. 이는 명백히 Neo4j Cypher path traversal 결과(인접 노드가 인접 위치에 등장하는 시퀀스)를 전제한 코드.

### CTE로 전환 시 발생할 문제

CTE의 `GROUP BY concept_id, MIN(depth)` 결과는 unique 노드 집합만 보장하고 path 정보는 손실. `LogicUtil.bfs.buildGraph`가 무작위 엣지를 만들어내 `depthDic` 값이 깨짐 → `Probability.to_concept_depth` 컬럼에 잘못된 값 저장 → 진단 결과 UI에서 "선수지식 depth" 표시 회귀.

**파급 범위**:
- 단위 테스트 영향: 없음 (CTE의 ID 집합 검증은 통과)
- ConceptServiceFeatureFlagTest 영향: 없음 (collectList 결과 단정만)
- **플래그 ON 시점의 실 운영 영향: 있음** (답안 제출 → AI 분석 → `to_concept_depth` 저장 경로)

즉 spec-01만으로는 unit test 그린이지만 플래그를 켜는 순간 silent regression 발생 가능.

### 본 spec의 대응

**시그니처에 depth 포함**: `findPrerequisitesWithDepth` 반환 타입을 `List<ConceptDepth>`로 정착. CTE의 `MIN(depth)` 집계로 정확한 최단 거리 제공. spec-02 진입 시 `LogicUtil.bfs` 호출 제거 + CTE depth 직접 사용으로 fragility 자체 제거.

본 spec(spec-01) 범위에서는 ConceptService 분기 호출부가 `.map(ConceptDepth::conceptId).toList()`로 `Flux<Integer>` 시그니처를 보존하므로 ProbabilityService의 LogicUtil.bfs 경로는 그대로 작동. 단 silent regression 위험은 유효하므로 **spec-02 완료 전까지 플래그 ON 운영 금지**를 PR 설명에 명시.

### spec-02로 위임되는 작업

- `LogicUtil.bfs` 호출 제거
- `ProbabilityService.create`가 `findPrerequisitesWithDepth` 결과의 `Map<Integer, Integer>` 변환을 직접 수행
- `LogicUtil.bfs` + `LogicUtilTest` 삭제 (호출자 0이 되므로)

---

## Task 1.6 — (이전: Concept 엔티티 매핑) — spec-02로 이전됨

본 task는 객체 반환 메서드(`findPrerequisiteConcepts`)를 위한 `RowMapper<Concept>` 도입을 다뤘으나, 본 spec은 ID 반환 한정이므로 **spec-02로 이전**한다.

매핑 정책은 ADR 0005(`concepts JOIN chapters` 패턴, conceptSection 매핑 생략, `Concept` 어노테이션은 spec-03 Task 5.3에서 일괄 정리)으로 확정. spec-02 Task 3.1에서 ConceptResponse 변환 분기와 함께 도입.

---

## 참조 데이터

정확성 검증(M1 결과 스냅샷 `shared/benchmark/` 대비)은 spec-03에서 수행. 본 spec의 단위 테스트는 격리된 작은 데이터셋(`cte_test_seed.sql`)을 사용한다.

### M1 시드 형태와 본 spec 단위 테스트 인프라 (audit 발견)

M1의 시드 적재 방식은 **MySQL CTE 단위 테스트에 직접 재활용 불가**:
- M1 테스트 시드는 `api/src/test/java/.../performance/GraphQueryPerformanceTest.java:85, 99`에서 LOAD CSV (Cypher)로 `concepts.csv` + `knowledge_space.csv` 적재 — **Neo4j 전용 형태**
- TestcontainersConfig가 CSV 파일을 마운트하는 방식 (`TestcontainersConfig.java:25` 근방 주석 참조)

본 spec(spec-01) Task 1.5 단위 테스트 인프라:
- **스키마 부트스트랩**: `@JdbcTest`는 JPA 엔티티 스캔을 하지 않으므로 `application-test.yml`의 `ddl-auto: create-drop`이 작동하지 않음. → `@Sql(scripts="classpath:cte_test_schema.sql")`로 명시적 스키마 적용 필요. 본 spec 단위 테스트의 schema는 CTE 검증에 필요한 최소 컬럼만 정의(`concepts(concept_id)`, `knowledge_space(knowledge_space_id, to_concept_id, from_concept_id)` + 복합 인덱스). FK 제약은 시드 단순화 위해 생략.
- **시드 위치**: `api/src/test/resources/cte_test_seed.sql` (test classpath). prod `api/sql/` 디렉토리와 분리해 단위 테스트 격리성 명확화.
- **적용**: `@JdbcTest + @Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class}) + @AutoConfigureTestDatabase(replace = NONE) + @Sql(scripts={"classpath:cte_test_schema.sql", "classpath:cte_test_seed.sql"})`
- spec-03 회귀 테스트는 prod 시드 재활용(`@Sql(scripts="file:../api/sql/insert_*.sql")` 패턴)로 별도 인프라 — 스키마 공유 안 함.

---

## 완료 기준

- [ ] `ConceptDepth` record 신규 (`api/src/main/java/com/mmt/api/repository/concept/ConceptDepth.java`)
- [ ] `JdbcTemplateConceptRepository.findPrerequisitesWithDepth(int, int)` CTE 구현 — 편의 메서드 미도입 (Task 1.2)
- [ ] `MysqlConceptRepository.java` + `MysqlConceptRepositoryStub.java` 삭제 (M1 토글 검증 하네스 역할 종료)
- [ ] `ConceptService` 분기 단순화 (`Optional<MysqlConceptRepository>` 제거, `jdbcTemplateConceptRepository` 직접 호출)
- [ ] 복합 인덱스 1개 추가 (`api/sql/add_knowledge_space_indexes.sql`) — 단일 인덱스 2개는 FK 자동 인덱스로 기존재 확인됨 (Task 1.3)
- [ ] `cte_max_recursion_depth = 10` Hikari `connection-init-sql` 적용 (`application.yml`, Task 1.4)
- [ ] 신규 `api/src/test/resources/cte_test_schema.sql` + `cte_test_seed.sql` 작성 (2-cycle 케이스 포함)
- [ ] `JdbcTemplateConceptRepositoryCteTest` 단위 테스트 통과 (Testcontainers 기반, ID+depth 반환 검증)
- [ ] `ConceptServiceFeatureFlagTest` 갱신 (Stub `UnsupportedOperationException` → 실제 결과 단정)
- [ ] `EXPLAIN` 결과 PR 설명에 첨부 (복합 인덱스의 covering index 작동 확인)
- [ ] PR 설명에 명시할 항목:
  - ADR 0002 §3 (Hibernate Statistics는 JPA 한정 — 본 spec은 JdbcTemplate이므로 비대상)
  - Analyze-Before-Change 결과 (A1 SHOW INDEX, A2 순환 참조 통계, A3 LogicUtil.bfs 발견)
  - **spec-02 완료 전까지 `mmt.migration.use-mysql-cte-for-graph` 플래그 ON 운영 금지** (A3 silent regression 위험)
  - 롤백 시나리오

---

## 비범위 (다른 spec에서 처리)

- 객체 반환 그래프 메서드(`findNodesByConceptId`, `findToConcepts` 등) → spec-02 (ADR 0005의 `concepts JOIN chapters` 매핑 패턴 적용)
- `RowMapper<Concept>` 도입 → spec-02
- ConceptService의 나머지 그래프 메서드 분기 확산 → spec-02
- 캐싱 적용 → spec-02
- **`LogicUtil.bfs` 호출 제거 + `ProbabilityService` 마이그레이션** → spec-02 (A3 발견, 본 spec에서 시그니처만 준비)
- 정확성 검증 (M1 스냅샷 대비) → spec-03
- 성능 측정 → spec-03
- `mmt.migration.use-mysql-cte-for-graph` 플래그 ON 운영 → spec-02 완료 후 (A3 silent regression 위험)
