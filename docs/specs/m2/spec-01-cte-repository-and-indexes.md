# Spec 01: CTE 리포지토리 + 인덱스

**상위 마일스톤:** Milestone 2 (Neo4j → MySQL CTE 마이그레이션)
**대상 Phase:** Phase 1
**예상 소요:** 2일
**선행 spec:** —

---

## 범위

`JdbcTemplateConceptRepository`에 재귀 CTE 메서드를 추가하고, 성능 확보에 필요한 인덱스를 도입한다. 본 spec은 **데이터 레이어에 한정**되며, 서비스 레이어 통합·피처 플래그 분기·캐싱은 spec-02, 검증·출시·폐기는 spec-03 범위다.

## 사전 조건 / 검증 필요

- ✓ `api/sql/select.sql:285`에 CTE 프로토타입 실재 확인 (`WITH RECURSIVE path AS ...`, conceptId=4979 하드코딩)
- ✓ `JdbcTemplateConceptRepository` 위치: `api/src/main/java/com/mmt/api/repository/concept/JdbcTemplateConceptRepository.java`. 그래프 메서드는 부재. 기존 메서드 명명 패턴은 `find{무엇}By{기준}` (예: `findOneByConceptId`, `findAllByChapterId`, `findSchoolLevelByConceptId`, `findSkillIdByConceptId`). 본 spec에서 신규 도입할 메서드도 동일 패턴을 따른다.
- ✓ 스키마 확인 (`api/sql/create.sql:52-58`): `knowledge_space(knowledge_space_id INT PK, to_concept_id INT FK, from_concept_id INT FK)`. `concepts.concept_id`도 INT.
- ✓ DB 마이그레이션 도구 미도입 — `api/sql/`의 수동 SQL 스크립트로 관리(DDL은 `create.sql`, 시드는 `insert_*.sql`). 본 spec의 인덱스 추가·`cte_max_recursion_depth` 설정은 (A) `api/sql/`에 신규 스크립트로 추가하거나 (B) Hikari `connection-init-sql`로 적용.
- ✓ **방향성 확정 (ADR 0003)** — `(from_concept_id, to_concept_id) = (선수, 후수)`. M2 CTE는 backward 방향으로 작성(아래 Task 1.1). `select.sql:285` forward 프로토타입은 폐기.

### ConceptService ↔ ConceptRepository ↔ CTE 매핑 표

ConceptService(5개 그래프 메서드)와 ConceptRepository(6개 Cypher 그래프 메서드)의 매핑, 그리고 본 spec에서 도입할 CTE 메서드의 매핑:

| ConceptService (5) | ConceptRepository (6) | 깊이 | 반환 타입 | CTE 호출 형태 |
|---|---|---|---|---|
| `findNodesByConceptId` (초등) | `findNodesByConceptIdDepth3` | 3 | `Flux<Concept>` | `findPrerequisiteConcepts(?, 3)` (객체 반환, RowMapper) |
| `findNodesByConceptId` (그 외) | `findNodesByConceptIdDepth5` | 5 | `Flux<Concept>` | `findPrerequisiteConcepts(?, 5)` (객체 반환, RowMapper) |
| `findNodesIdByConceptIdDepth2` | `findNodesIdByConceptIdDepth2` | 2 | `Flux<Integer>` | `findPrerequisiteConceptIds(?, 2)` |
| `findNodesIdByConceptIdDepth3` | `findNodesIdByConceptIdDepth3` | 3 | `Flux<Integer>` | `findPrerequisiteConceptIds(?, 3)` |
| `findNodesIdByConceptIdDepth5` | `findNodesIdByConceptIdDepth5` | 5 | `Flux<Integer>` | `findPrerequisiteConceptIds(?, 5)` |
| `findToConcepts` | `findToConceptsByConceptId` | 1 | `Flux<Concept>` | `findPrerequisiteConcepts(?, 1)` (ADR 0003에 따라 깊이 1 == 직접 선수) |

본 spec은 위 매핑에 따라 **두 CTE 메서드**를 신규 도입한다:
- `findPrerequisiteConceptIds(int conceptId, int maxDepth)` — `List<Integer>` 반환 (ID만)
- `findPrerequisiteConcepts(int conceptId, int maxDepth)` — `List<Concept>` 반환 (객체 전체, `RowMapper<Concept>` 사용)

---

## Task 1.1 — 재귀 CTE 메서드 도입

### Neo4j 쿼리 3종 → MySQL CTE 매핑

**ADR 0003에 따라 모든 쿼리는 backward 방향(시작점에서 from 측으로 재귀 진행 = "?의 선수 찾기")으로 통일한다.** `select.sql:285`의 forward 프로토타입은 폐기.

**쿼리 1 — 직접 선수 개념 (depth 1)**

Neo4j 원본:
```cypher
MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)
```

MySQL 변환 (backward, "?의 선수 1단계"):
```sql
SELECT c.* FROM concepts c
JOIN knowledge_space ks ON c.concept_id = ks.from_concept_id
WHERE ks.to_concept_id = ?
```

**쿼리 2 — 깊이 N 재귀 탐색 (핵심)**

Neo4j 원본:
```cypher
MATCH (n)-[*0..N]->(m {concept_id: $conceptId}) RETURN (n)
```

MySQL 변환 (backward, "?의 선수 N단계"):
```sql
WITH RECURSIVE prerequisite_path AS (
    SELECT concept_id, 0 AS depth
    FROM concepts WHERE concept_id = ?

    UNION ALL

    SELECT c.concept_id, pp.depth + 1
    FROM prerequisite_path pp
    JOIN knowledge_space ks ON pp.concept_id = ks.to_concept_id
    JOIN concepts c           ON ks.from_concept_id = c.concept_id
    WHERE pp.depth < ?
)
SELECT DISTINCT concept_id FROM prerequisite_path
```

쿼리 1은 쿼리 2의 `maxDepth=1` 호출과 동일 결과이므로 **별도 메서드 불필요** — `findPrerequisiteConceptIds(?, 1)` 또는 `findPrerequisiteConcepts(?, 1)` 호출로 처리한다.

**쿼리 3 — 경로 상의 concept_id 추출 (BFS 입력용)**

쿼리 2와 동일한 결과셋. 별도 메서드를 두지 않고 쿼리 2 결과를 BFS 입력으로 그대로 사용.

### 메서드 시그니처

`MysqlConceptRepository`(M1 도입)에 다음 두 메서드를 신규 추가한다:

```java
// ID만 반환 (findNodesIdByConceptIdDepth* 대체)
public List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth);

// 객체 전체 반환 (findNodesByConceptIdDepth*, findToConceptsByConceptId 대체)
public List<Concept> findPrerequisiteConcepts(int conceptId, int maxDepth);
```

명명 패턴 결정: 기존 `find{무엇}By{기준}` 컨벤션을 변형하되 "선수(prerequisite)" 도메인 의미를 명시. M1 시범 코드(`ConceptService.java:73-80`)가 이미 `findPrerequisiteConceptIds(int, int)` 사용 중이므로 그 시그니처를 그대로 보존하고, 객체 반환용으로 `findPrerequisiteConcepts`를 페어 도입한다.

---

## Task 1.2 — 편의 메서드 도입 결정 (변경됨: 미도입)

기존 Neo4j 쪽이 `findNodesIdByConceptIdDepth2/3/5`로 분리된 이유는 Cypher의 깊이 매개변수가 리터럴만 허용하기 때문. CTE는 매개변수화 가능하므로 통합 메서드 하나로 충분하다.

**결정: 편의 메서드 미도입.** M1 시범 분기 패턴(`ConceptService.java:73-80`)이 통합 메서드 + 깊이 인자 형태 `findPrerequisiteConceptIds(conceptId, 3)`를 채택했으므로, 본 spec도 통합 메서드만 도입하여 spec-02의 분기 적용과 정합성을 유지한다.

호출부 사용 깊이 확인 결과: 2/3/5 세 가지만 사용 (그 외 값 없음). 통합 메서드의 `maxDepth` 인자로 모두 처리 가능.

---

## Task 1.3 — 인덱스 도입

```sql
CREATE INDEX idx_knowledge_space_from ON knowledge_space(from_concept_id);
CREATE INDEX idx_knowledge_space_to   ON knowledge_space(to_concept_id);
CREATE INDEX idx_knowledge_space_composite
    ON knowledge_space(from_concept_id, to_concept_id);
```

[검증 필요]
- 위 인덱스 또는 동등 인덱스가 이미 존재하는지: `SHOW INDEX FROM knowledge_space`
- PRIMARY KEY 또는 UNIQUE 제약이 동일 컬럼 조합을 커버하는지
- 참고: `create.sql:52-58`의 `knowledge_space`는 `PRIMARY KEY(knowledge_space_id)` + FK 2개만 명시. MySQL은 FK 컬럼에 자동 인덱스를 생성하므로 단일 인덱스 2개(`from_concept_id`, `to_concept_id`)는 사실상 이미 존재할 가능성이 높음 → **신규는 복합 인덱스 1개일 가능성**. `SHOW INDEX`로 중복 회피 후 결정.
- 적용 위치: 마이그레이션 도구 미도입(사전 조건 참조) → `api/sql/`에 신규 SQL 스크립트(예: `add_knowledge_space_indexes.sql`)로 추가하고 PR에 적용 절차 명시.

`EXPLAIN`으로 쿼리 2의 실행 계획에서 재귀 단계마다 `idx_knowledge_space_from`이 사용되는지 확인.

---

## Task 1.4 — 재귀 깊이 설정

MySQL 기본값 `cte_max_recursion_depth = 1000`은 충분하지만, 안전상 명시적 설정 권장:

```sql
SET SESSION cte_max_recursion_depth = 10;
```

최대 깊이 5에 충분하며, 데이터 이상 시 무한 루프를 빠르게 차단.

[검증 필요] 적용 위치 결정:
- (A) Hikari `connection-init-sql` (`spring.datasource.hikari.connection-init-sql`)
- (B) 마이그레이션 스크립트의 GLOBAL 설정
- (C) 메서드 호출 시점에 `JdbcTemplate.execute("SET SESSION ...")`

(A)가 가장 안전 (모든 커넥션 적용, 운영 측 영향 없음).

---

## Task 1.5 — 단위 테스트

테스트 위치: `api/src/test/java/com/mmt/api/repository/concept/MysqlConceptRepositoryCteTest.java`
사용 인프라: M1의 Testcontainers MySQL + `application-test.yml` (ADR 0002 §2)

테스트 케이스 (두 메서드 모두):

- 단일 conceptId, depth 0 → 자기 자신 1개만 반환
- depth 1 → 직접 선수 개념만 (자기 자신 포함)
- depth N → N 단계 이내 모든 선수 개념
- 존재하지 않는 conceptId → 빈 리스트
- 깊이 매개변수가 음수 → IllegalArgumentException 또는 빈 리스트 (정책 결정 필요)
- `findPrerequisiteConcepts`(객체 반환) — `RowMapper<Concept>` 매핑 결과가 `concept_id`/`concept_name`/`concept_chapter_id` 등 모든 필드를 채우는지 확인

[검증 필요] 데이터에 순환 참조 존재 여부. 존재한다면 CTE는 `cte_max_recursion_depth`에 도달할 때까지 무한히 노드를 누적하므로 정확성 검증 케이스 추가 필수.

---

## Task 1.6 — Concept 엔티티의 MySQL 호환 매핑

`Concept.java:9`는 `@Node("concept")` Neo4j 어노테이션을 사용한다. `findPrerequisiteConcepts`가 `List<Concept>`를 반환하려면 MySQL의 `concepts` 테이블 row를 `Concept` 객체로 매핑할 수 있어야 한다.

### 결정 사항 (본 spec 진행 시)

두 옵션 중 채택:

- **(A) 기존 `Concept` 클래스에 RowMapper 한정 매핑만 추가** — `@Node` 어노테이션은 그대로 두고, `MysqlConceptRepository` 내부에 `private RowMapper<Concept> conceptRowMapper()`만 정의. Neo4j 컨테이너 폐기(spec-03 Task 5.3) 시 `@Node` 제거하면 자연 정리.
- **(B) MySQL 전용 도메인을 별도 클래스로 신규 도입** — `Concept`은 Neo4j 전용으로 두고 `ConceptRow` 같은 별도 클래스 신설. 변환 boilerplate 추가 부담.

권장: (A). M2 기간(Neo4j와 CTE 공존)에 도메인 클래스를 두 개 운영하는 부담을 피하고, `@Node`는 spec-03 Task 5.3에서 일괄 제거.

### RowMapper 위치

`MysqlConceptRepository` 내부 private 메서드로 정의 (외부 노출 불필요).

```java
private RowMapper<Concept> conceptRowMapper() {
    return (rs, rowNum) -> {
        Concept c = new Concept();
        c.setConceptId(rs.getInt("concept_id"));
        c.setConceptName(rs.getString("concept_name"));
        // ... concepts 테이블의 모든 컬럼 매핑
        return c;
    };
}
```

[검증 필요] `concepts` 테이블 컬럼 전체 목록과 `Concept` 클래스의 setter 가용성 — 본 task 시작 시 `api/sql/create.sql`의 `concepts` DDL과 `Concept.java` 필드를 1:1 비교.

---

## 참조 데이터

테스트는 M1에서 확보한 결과 스냅샷(`shared/benchmark/`)과 정확성 검증을 spec-03에서 수행한다. 본 spec의 단위 테스트는 격리된 작은 데이터셋을 사용한다.

[검증 필요] M1에서 사용한 테스트 시드 데이터의 위치 및 형태. 동일 시드를 재활용할지, 본 spec 전용 시드를 작성할지 결정.

---

## 완료 기준

- [ ] CTE 통합 메서드 2개 도입: `findPrerequisiteConceptIds(int, int)` + `findPrerequisiteConcepts(int, int)` (편의 메서드 미도입 결정 — Task 1.2)
- [ ] `RowMapper<Concept>` 매핑 도입 (Task 1.6)
- [ ] 인덱스 3종 적용 (또는 기존재 시 검증 결과를 PR 설명에 명시)
- [ ] `cte_max_recursion_depth` 설정 적용 위치 결정 및 반영
- [ ] 단위 테스트 모두 통과 (Testcontainers 기반, ID/객체 반환 모두 커버)
- [ ] `EXPLAIN` 결과 PR 설명에 첨부
- [ ] PR 설명에 ADR 0002 §3(Hibernate Statistics는 JPA 한정 — 본 spec은 JdbcTemplate이므로 비대상) 명시
- [ ] PR 설명에 ADR 0003(방향성 결정) 인용
- [ ] Analyze-Before-Change 결과(영향받는 호출부, 롤백 시나리오) PR 설명 포함

---

## 비범위 (다른 spec에서 처리)

- ConceptService 통합 → spec-02
- 캐싱 적용 → spec-02
- 피처 플래그 분기 → spec-02
- 정확성 검증 (M1 스냅샷 대비) → spec-03
- 성능 측정 → spec-03
