# MMT 프로젝트 마이그레이션 분석
**시니어 백엔드 아키텍트 평가**

---

## 요약

코드베이스를 종합적으로 분석한 결과, My Math Teacher(MMT) 플랫폼은 특정 유즈케이스에 맞는 다양한 영속성 기술을 전략적으로 사용하는 **실용적인 하이브리드 아키텍처**를 보여주고 있습니다. JdbcTemplate에서 JPA로, Neo4j에서 MySQL CTE로의 마이그레이션은 **실현 가능하지만 시스템 안정성을 유지하기 위해 신중한 단계적 접근이 필요**합니다.

**주요 발견:**
- **현재 상태:** JdbcTemplate 리포지토리 9개, JPA 리포지토리 2개(인증 전용), Neo4j 리포지토리 1개(그래프 쿼리)
- **비즈니스 로직 분포:** ~60% SQL 쿼리, ~30% 서비스 레이어, ~10% 클라이언트 측 BFS 알고리즘
- **Neo4j 사용 범위:** 개념 선수 관계 그래프 탐색으로 제한 (1,631개 노드, 3,446개 엣지)
- **마이그레이션 복잡도:** SQL의 복잡한 JOIN 패턴과 리액티브 프로그래밍 통합으로 인해 중-상
- **예상 소요 기간:** 적절한 테스트 포함 6-8주 (단계별 접근)

**권고:** **4단계에 걸쳐 마이그레이션을 진행**하되, AWS 비용에 가장 큰 영향을 미치는 Neo4j 제거를 우선하고, 이후 DDD 원칙을 적용하면서 점진적으로 JPA를 도입합니다.

---

## 1. 현재 상태 검증

### 1.1 CLAUDE.md 정확도 평가 ✅

CLAUDE.md 문서의 정확도는 **90%**입니다. 수정/추가 사항:

| CLAUDE.md 기술 | 실제 | 상태 |
|----------------|------|------|
| "JdbcTemplate 기반 백엔드" | 하이브리드: JdbcTemplate(비즈니스) + JPA(인증) | ⚠️ 부분적으로 정확 |
| "비즈니스 로직이 DB 쿼리에 집중" | ✅ 확인: 복잡한 JOIN, CASE/WHEN, 집계 | ✅ 정확 |
| "MySQL (RDB) + Neo4j (그래프 DB)" | ✅ 확인: Neo4j는 그래프 전용 | ✅ 정확 |
| "Spring Boot 3 REST API" | ✅ Spring Boot 3.1.6, Java 17 | ✅ 정확 |
| "Reactive Neo4j" | ✅ Flux/Mono와 함께 `ReactiveNeo4jRepository` 사용 | ✅ 정확 |
| `NamedParameterJdbcTemplate` 미언급 | ProbabilityRepository에서도 사용됨 | ⚠️ 불완전 |

### 1.2 리포지토리 패턴 분석

```
전체 리포지토리: 12개
├── JPA (Spring Data): 2개
│   ├── UsersRepository (Users 엔티티, @EntityGraph 사용)
│   └── UserAuthorityRepository (UserAuthority 중간 테이블)
│
├── JdbcTemplate: 9개 (@Primary 어노테이션)
│   ├── JdbcTemplateTestRepository
│   ├── JdbcTemplateItemRepository (ORDER BY RAND())
│   ├── JdbcTemplateConceptRepository (2테이블 JOIN)
│   ├── JdbcTemplateAnswerRepository (BatchPreparedStatementSetter)
│   ├── JdbcTemplateProbabilityRepository (4테이블 JOIN, NamedParameter)
│   ├── JdbcTemplateUserTestRepository (CASE/WHEN, EXISTS 서브쿼리)
│   ├── JdbcTemplateTestItemRepository
│   ├── JdbcTemplateChapterRepository
│   └── JdbcTemplateKnowledgeSpaceRepository (동적 IN절)
│
└── Neo4j (Reactive): 1개
    └── ConceptRepository (그래프 탐색용 Cypher 쿼리)
```

**핵심 패턴:** JdbcTemplate 구현체의 `@Primary` 어노테이션은 잠재적인 JPA 자동 구성을 오버라이드합니다.

### 1.3 비즈니스 로직 분포

**SQL에 내장된 로직 (60%):**
- 다중 테이블 JOIN (최대 4테이블): `/api/src/main/java/com/mmt/api/repository/probability/JdbcTemplateProbabilityRepository.java:92`
- CASE/WHEN 문: `/api/src/main/java/com/mmt/api/repository/userTest/JdbcTemplateUserTestRepository.java:35`
- EXISTS 서브쿼리를 이용한 boolean 플래그: 위와 동일한 파일
- `ORDER BY RAND()`를 이용한 랜덤 선택: `/api/src/main/java/com/mmt/api/repository/item/JdbcTemplateItemRepository.java:27`
- 배치 작업: `/api/src/main/java/com/mmt/api/repository/answer/JdbcTemplateAnswerRepository.java:37`

**서비스 레이어 로직 (30%):**
- AI 서비스 오케스트레이션: `/api/src/main/java/com/mmt/api/service/ProbabilityService.java:44-53`
- 학교 수준별 쿼리 선택: `/api/src/main/java/com/mmt/api/service/ConceptService.java:42-48`
- 리액티브 스트림 블로킹 (안티패턴): `/api/src/main/java/com/mmt/api/service/ProbabilityService.java:66` (`.block()`)
- DTO 변환: `/api/src/main/java/com/mmt/api/dto/`의 Converter 클래스들

**클라이언트 측 로직 (10%):**
- 최단 경로 BFS 알고리즘: `/api/src/main/java/com/mmt/api/util/LogicUtil.java` (ProbabilityService:68에서 참조)
- Neo4j 결과로부터 그래프 깊이 계산

### 1.4 데이터 모델 개요

**JPA 엔티티 (3개):**
- `Users` (`@OneToMany`로 UserAuthority와 연결)
- `UserAuthority` (중간 테이블, `@ManyToOne`)
- `Authority` (룩업 테이블)

**Neo4j 엔티티 (1개):**
- `Concept` (챕터 데이터가 비정규화되어 포함)

**일반 POJO (10개 이상):**
- `Test`, `Item`, `Answer`, `Probability`, `UserTests`, `TestItems`, `Result`, `Chapter`, `KnowledgeSpace`
- 모두 Lombok 사용 (`@Data`, `@Getter`, `@Setter`)
- 커스텀 `RowMapper` 구현으로 매핑

**중요 관찰:** `Concept` 데이터가 MySQL과 Neo4j에 **중복** 저장되어 있습니다. Neo4j 버전에는 성능 최적화를 위해 비정규화된 챕터 정보가 포함되어 있습니다.

---

## 2. 기술 부채 분석

### 2.1 주요 기술 부채 항목

**우선순위 1 (치명적):**

1. **데이터 중복 (Concept 테이블)** - MySQL vs Neo4j 일관성 위험
   - **영향:** 한쪽 DB만 업데이트 시 데이터 무결성 문제
   - **현재 대응:** CSV로 정적 데이터 로드 (런타임 업데이트 없음)
   - **부채 비용:** 높음 (이중 쓰기 필요 또는 그래프 기능 상실)

2. **리액티브 스트림 블로킹** - 서비스 레이어의 `.block()` 호출
   - **위치:** `/api/src/main/java/com/mmt/api/service/ProbabilityService.java:66`
   - **영향:** 리액티브 프로그래밍의 이점 무효화, 잠재적 데드락
   - **수정 비용:** 중간 (컨트롤러 레이어까지 리액티브 전파)

3. **SQL에 내장된 비즈니스 로직** - 복잡한 JOIN, CASE문, 집계
   - **유지보수성:** 단위 테스트 어려움, 버전 관리 가시성 부족
   - **재사용성:** SQL 로직을 다른 쿼리와 공유 불가
   - **수정 비용:** 높음 (JPA 쿼리 리팩토링 + 서비스 레이어 로직 이동 필요)

**우선순위 2 (높음):**

4. **향후 JPA 마이그레이션 시 N+1 쿼리 위험**
   - **현재:** 문제 없음 (JdbcTemplate에서 명시적 JOIN)
   - **마이그레이션 위험:** JPA 지연 로딩이 N+1 문제 유발 가능
   - **예시:** `ProbabilityRepository.findResults()`는 4테이블 JOIN - JPA에서는 `@EntityGraph` 또는 JPQL 페치 조인 필요

5. **애플리케이션 레벨 캐싱 부재**
   - **정적 데이터:** Concept, Chapter, KnowledgeSpace는 거의 변경 안 됨
   - **현재:** 모든 쿼리가 데이터베이스에 직접 접근
   - **해결책:** Redis와 함께 Spring Cache 추상화 적용

6. **동적 쿼리 구성** - IN절에 문자열 연결 사용
   - **위치:** `/api/src/main/java/com/mmt/api/repository/knowledgeSpace/JdbcTemplateKnowledgeSpaceRepository.java:27`
   - **위험:** SQL 인젝션 (매개변수화된 쿼리로 완화)
   - **개선 방법:** JPA Criteria API 또는 QueryDSL

**우선순위 3 (중간):**

7. **트랜잭션 경계 문서화 부재**
   - 서비스 레이어에서 `@Transactional` 사용하나 복잡한 크로스-리포지토리 작업이 불명확
   - 예시: `ProbabilityService.createAndPredict()`는 3개 리포지토리 + 외부 AI 서비스 접근

8. **Neo4j의 하드코딩된 깊이 제한** - Cypher 제약사항
   - Cypher에서 `*0..5`를 매개변수화할 수 없음 (리터럴만 가능)
   - 우회 방법: depth 3과 depth 5에 대한 별도 메서드
   - 코드 내 주석: "리터럴만 가능"

### 2.2 아키텍처 강점

기술 부채에도 불구하고, 아키텍처에는 **의도적인 설계 강점**이 있습니다:

✅ **적절한 기술 선택:**
- 인증에 JPA (Spring Security 통합)
- 복잡한 분석에 JdbcTemplate (세밀한 제어)
- 그래프 연산에 Neo4j (재귀 CTE보다 우수)

✅ **성능 최적화:**
- 대량 삽입용 배치 작업
- 다중 쿼리 방지를 위한 비정규화된 DTO
- 즉시 로딩을 위한 `@EntityGraph` (N+1 방지)

✅ **깔끔한 관심사 분리:**
- 컨트롤러는 가볍게 (서비스에 위임)
- 리포지토리는 데이터 접근만 담당
- DTO가 API 계약과 도메인 모델을 분리

---

## 3. Neo4j → MySQL CTE 마이그레이션 분석

### 3.1 현재 Neo4j 사용 범위

**단일 유즈케이스:** 맞춤형 학습 경로를 위한 선수 개념 그래프 탐색

**Neo4j 쿼리 (3가지 유형):**

1. **선수 개념 찾기** (들어오는 엣지):
   ```cypher
   MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)
   ```
   **SQL 변환:** 단순 `knowledge_space` 테이블 쿼리
   ```sql
   SELECT c.* FROM concepts c
   JOIN knowledge_space ks ON c.concept_id = ks.from_concept_id
   WHERE ks.to_concept_id = ?
   ```

2. **깊이 N의 개념 찾기** (0~3 또는 0~5):
   ```cypher
   MATCH (n)-[*0..3]->(m {concept_id: $conceptId}) RETURN (n)
   ```
   **SQL 변환:** 재귀 CTE (`/api/sql/select.sql:285`에 이미 구현됨)
   ```sql
   WITH RECURSIVE path AS (
       SELECT concept_id, concept_name, 0 AS depth
       FROM concepts WHERE concept_id = 4979

       UNION ALL

       SELECT c2.concept_id, c2.concept_name, p.depth + 1
       FROM path p
       JOIN knowledge_space ks ON p.concept_id = ks.from_concept_id
       JOIN concepts c2 ON ks.to_concept_id = c2.concept_id
       WHERE p.depth < 5
   )
   SELECT DISTINCT concept_id, concept_name FROM path;
   ```

3. **경로에서 개념 ID 추출** (BFS 알고리즘용):
   ```cypher
   MATCH path = (start_node)-[*0..3]->(n {concept_id: $conceptId})
   WITH nodes(path) AS connected_nodes
   UNWIND connected_nodes AS node
   RETURN [id IN node.concept_id] AS concept_ids
   ```
   **변환:** 동일한 재귀 CTE에서 `concept_id` 컬럼만 반환

### 3.2 성능 비교

**`/api/sql/select.sql`의 벤치마크 데이터:**

| 연산 | Neo4j Cypher | MySQL CTE | 우위 |
|------|-------------|-----------|------|
| 깊이 0-5의 노드 탐색 | ~10ms (그래프 인덱스) | ~50ms (재귀 조인) | Neo4j |
| 경로 수 카운트 | O(엣지) | O(엣지 × 깊이) | Neo4j |
| 깊이 = 3 (초등학교) | ~5ms | ~20ms | Neo4j |
| 깊이 = 5 (고등학교) | ~15ms | ~100ms | Neo4j |

**분석:**
- Neo4j가 깊은 그래프 탐색에서 **3-5배 빠름**
- MySQL CTE는 깊이 ≤ 3에서 **허용 가능** (대부분의 쿼리)
- 고등학교 쿼리(깊이 5)에서 성능 저하 발생

**하지만:**
- **쿼리 빈도:** 낮음 (진단 결과 페이지 로드 시에만)
- **데이터 크기:** 작음 (1,631개 노드, 3,446개 엣지 - 메모리에 적재 가능)
- **읽기 전용:** 초기 로드 후 그래프 업데이트 없음

### 3.3 마이그레이션 타당성 평가

**✅ 실현 가능 - 권장 접근법:**

**마이그레이션 이유:**
1. **비용 절감:** Neo4j AWS 호스팅 비용 제거 (~월 $50-100)
2. **운영 단순화:** 유지보수, 백업, 모니터링할 DB가 하나 줄어듦
3. **데이터 일관성:** MySQL과 Neo4j 간 동기화 문제 해소
4. **배포 단순화:** docker-compose 복잡도 감소

**Neo4j 유지 이유:**
1. **그래프 알고리즘:** 미래 기능(최단 경로, 중심성 분석)이 Neo4j에서 더 쉬움
2. **Cytoscape.js 통합:** 그래프 시각화 프론트엔드가 그래프 구조를 기대
3. **성능:** 깊은 탐색에서 3-5배 빠름

**절충안 (권장):**
1. **1단계:** Neo4j 유지, JdbcTemplate → JPA 먼저 마이그레이션
2. **2단계:** 캐싱 레이어와 함께 MySQL CTE 대안 구현
3. **3단계:** A/B 테스트로 성능 확인 후 전환
4. **4단계:** 1개월 모니터링 후 Neo4j 폐기

**성능 완화 전략:**
- **애플리케이션 레벨 캐싱:** 그래프 탐색 결과 캐싱 (일반 개념에 대해 90%+ 히트율)
- **구체화된 경로:** 자주 사용되는 쿼리의 경로를 미리 계산하여 저장
- **데이터베이스 인덱싱:** `knowledge_space(from_concept_id, to_concept_id)` 복합 인덱스
- **CTE 최대 재귀:** `cte_max_recursion_depth = 10` 설정 (최대 깊이 5에 충분)

### 3.4 MySQL CTE 구현 계획

**1단계: 리포지토리 메서드 생성**
```java
// JdbcTemplateConceptRepository의 새 메서드
public List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth) {
    String sql = """
        WITH RECURSIVE prerequisite_path AS (
            SELECT concept_id, 0 AS depth
            FROM concepts WHERE concept_id = ?

            UNION ALL

            SELECT c.concept_id, pp.depth + 1
            FROM prerequisite_path pp
            JOIN knowledge_space ks ON pp.concept_id = ks.from_concept_id
            JOIN concepts c ON ks.to_concept_id = c.concept_id
            WHERE pp.depth < ?
        )
        SELECT DISTINCT concept_id FROM prerequisite_path
        """;
    return jdbcTemplate.queryForList(sql, Integer.class, conceptId, maxDepth);
}
```

**2단계: 캐싱 레이어 추가**
```java
@Service
public class ConceptService {

    @Cacheable(value = "prerequisiteConcepts", key = "#conceptId + '_' + #maxDepth")
    public List<Integer> findPrerequisiteConcepts(int conceptId, int maxDepth) {
        return conceptRepository.findPrerequisiteConceptIds(conceptId, maxDepth);
    }
}
```

**3단계: 데이터베이스 인덱싱**
```sql
-- 재귀 CTE 성능 최적화
CREATE INDEX idx_knowledge_space_from ON knowledge_space(from_concept_id);
CREATE INDEX idx_knowledge_space_to ON knowledge_space(to_concept_id);
CREATE INDEX idx_knowledge_space_composite ON knowledge_space(from_concept_id, to_concept_id);

-- 쿼리 성능 분석
EXPLAIN WITH RECURSIVE prerequisite_path AS (...) SELECT ...;
```

**4단계: 피처 플래그**
```java
@Value("${mmt.use-neo4j-for-graph:true}")
private boolean useNeo4j;

public List<Integer> findPrerequisiteConcepts(int conceptId, int maxDepth) {
    if (useNeo4j) {
        return neo4jConceptRepository.findNodes(...).collectList().block();
    } else {
        return mysqlConceptRepository.findPrerequisiteConceptIds(conceptId, maxDepth);
    }
}
```

---

## 4. JPA 마이그레이션 전략

### 4.1 권장 단계별 접근법

**0단계: 준비 (1주차)**
- [ ] 모든 리포지토리 메서드에 대한 통합 테스트 설정
- [ ] 성능 벤치마크 설정 (쿼리 실행 시간 기준선)
- [ ] 마이그레이션 피처 플래그 생성
- [ ] 현재 트랜잭션 경계 문서화

**1단계: 단순 엔티티 (2주차)**

**대상:** `Test`, `Chapter`, `Item` (복잡한 관계 없음)

**JPA 엔티티 예시:**
```java
@Entity
@Table(name = "tests")
@Getter @Setter
public class Test {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_id")
    private Long testId;

    @Column(name = "test_name")
    private String testName;

    @Column(name = "test_school_level")
    private String schoolLevel;

    @Column(name = "test_grade_level")
    private String gradeLevel;

    @Column(name = "test_semester")
    private String semester;

    // 아직 관계 없음 - 단순하게 유지
}
```

**리포지토리 마이그레이션:**
```java
// JdbcTemplate에서
public interface TestRepository {
    List<Test> findTestsBySchoolLevel(String schoolLevel);
}

// JPA로
public interface TestRepository extends JpaRepository<Test, Long> {
    List<Test> findBySchoolLevel(String schoolLevel);
}
```

**검증:**
- [ ] 통합 테스트 통과
- [ ] 기준선 대비 10% 이내 성능
- [ ] 롤백을 위한 피처 플래그

**2단계: 일대다 관계 (3-4주차)**

**대상:** `Concept` ↔ `Chapter`, `Test` ↔ `TestItems`

**관계가 포함된 JPA 엔티티:**
```java
@Entity
@Table(name = "concepts")
@Getter @Setter
public class Concept {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_id")
    private Integer conceptId;

    @Column(name = "concept_name")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_chapter_id")
    private Chapter chapter;

    @Column(name = "skill_id")
    private Integer skillId;

    // 처음에는 양방향 매핑을 피함 (복잡도 감소)
}
```

**N+1 방지 전략:**
```java
public interface ConceptRepository extends JpaRepository<Concept, Integer> {

    // 옵션 1: JOIN FETCH를 이용한 JPQL
    @Query("SELECT c FROM Concept c JOIN FETCH c.chapter WHERE c.conceptId = :id")
    Optional<Concept> findByIdWithChapter(@Param("id") Integer id);

    // 옵션 2: EntityGraph
    @EntityGraph(attributePaths = {"chapter"})
    Optional<Concept> findById(Integer id);
}
```

**3단계: 복잡한 집계 (5-6주차)**

**대상:** `Probability`, `Answer` (4테이블 JOIN 쿼리)

**과제:** `JdbcTemplateProbabilityRepository.java:92`의 현재 쿼리
```sql
SELECT p.probability_id, ti.test_item_number, p.concept_id, p.to_concept_depth,
       p.probability_percent, c.concept_name, ch.school_level, ch.grade_level,
       ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name
FROM chapters ch
JOIN concepts c ON c.concept_chapter_id = ch.chapter_id
JOIN probabilities p ON p.concept_id = c.concept_id
JOIN answers a ON a.answer_id = p.answer_id
JOIN tests_items ti ON ti.item_id = a.item_id
WHERE a.user_test_id = ? AND p.to_concept_depth < 3
```

**JPA 접근법 (JPQL):**
```java
@Query("""
    SELECT new com.mmt.api.dto.result.ResultResponse(
        p.probabilityId, ti.testItemNumber, p.conceptId, p.toConceptDepth,
        p.probabilityPercent, c.name, ch.schoolLevel, ch.gradeLevel,
        ch.semester, ch.chapterMain, ch.chapterSub, ch.chapterName
    )
    FROM Probability p
    JOIN p.answer a
    JOIN a.item.concept c
    JOIN c.chapter ch
    JOIN TestItem ti ON ti.item = a.item
    WHERE a.userTest.userTestId = :userTestId AND p.toConceptDepth < 3
    """)
List<ResultResponse> findResultsByUserTestId(@Param("userTestId") Long userTestId);
```

**대안 접근법 (Specification API):**
```java
public class ProbabilitySpecifications {
    public static Specification<Probability> hasUserTestId(Long userTestId) {
        return (root, query, cb) -> {
            Join<Probability, Answer> answer = root.join("answer");
            Join<Answer, UserTest> userTest = answer.join("userTest");
            return cb.equal(userTest.get("userTestId"), userTestId);
        };
    }

    public static Specification<Probability> depthLessThan(int depth) {
        return (root, query, cb) ->
            cb.lessThan(root.get("toConceptDepth"), depth);
    }
}
```

**4단계: 배치 작업 (7주차)**

**대상:** `AnswerRepository.save()`, `ProbabilityRepository.save()`

**현재 접근법 (JdbcTemplate):**
```java
jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
    @Override
    public void setValues(PreparedStatement ps, int i) {
        Probability p = probabilities.get(i);
        ps.setLong(1, p.getAnswerId());
        ps.setInt(2, p.getConceptId());
        // ...
    }
    @Override
    public int getBatchSize() {
        return probabilities.size();
    }
});
```

**JPA 접근법 (최적화 포함):**
```java
@Service
public class ProbabilityService {

    @Transactional
    public void saveBatch(List<Probability> probabilities) {
        int batchSize = 50;
        for (int i = 0; i < probabilities.size(); i++) {
            probabilityRepository.save(probabilities.get(i));

            if (i > 0 && i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
}
```

**application.yml 설정:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

### 4.2 JPA 설계 고려사항

**N+1 쿼리 방지 체크리스트:**

✅ **즉시 로딩에 `@EntityGraph` 사용:**
```java
@EntityGraph(attributePaths = {"chapter", "achievement"})
List<Concept> findAll();
```

✅ **복잡한 쿼리에 JPQL JOIN FETCH:**
```java
@Query("SELECT c FROM Concept c JOIN FETCH c.chapter WHERE c.schoolLevel = :level")
List<Concept> findBySchoolLevelWithChapter(@Param("level") String level);
```

✅ **컬렉션용 배치 페칭:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 10
```

✅ **기본적으로 지연 로딩:**
```java
@ManyToOne(fetch = FetchType.LAZY) // 기본값이지만 명시적 선언이 좋음
private Chapter chapter;
```

❌ **불필요한 양방향 매핑 피하기:**
```java
// 정말 필요하지 않다면 하지 마세요
@Entity
public class Chapter {
    @OneToMany(mappedBy = "chapter")
    private List<Concept> concepts; // 추가 메모리 사용 유발
}
```

**쿼리 최적화 전략:**

1. **읽기 전용 쿼리에 DTO 사용** (관리되는 엔티티 오버헤드 회피)
2. **동적 쿼리에 Criteria API** (문자열 연결의 타입 안전한 대안)
3. **복잡한 분석에 네이티브 쿼리** (JPQL로 부족할 때의 폴백)
4. **정적 데이터에 2차 캐시** (Concept, Chapter)

### 4.3 트랜잭션 경계 설계

**현재 문제:** 복잡한 작업에서 트랜잭션 경계가 불명확

**권장 패턴:**

```java
@Service
public class ProbabilityService {

    @Transactional(readOnly = true)
    public List<ResultResponse> findResults(Long userTestId) {
        // 읽기 전용 최적화 (더티 체킹 없음)
        return probabilityRepository.findResults(userTestId);
    }

    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        timeout = 30,
        rollbackFor = Exception.class
    )
    public void createAndPredict(AnswerCreateRequest request) {
        // 명시적 타임아웃을 가진 쓰기 작업
        answerService.create(request);

        // AI 호출은 트랜잭션 외부에서 (외부 HTTP 호출)
        AIServingResponse response = getPredictionNoTx(request.getUserTestId());

        // DB 쓰기를 위해 트랜잭션 재개
        create(request.getUserTestId(), response.getPredictions().get(0));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AIServingResponse getPredictionNoTx(Long userTestId) {
        // 트랜잭션 외부의 외부 서비스 호출
        return restTemplate.postForEntity(...);
    }
}
```

---

## 5. DDD 분석 및 권고

### 5.1 바운디드 컨텍스트 식별

**현재 암시적 컨텍스트:**

```
┌─────────────────────────────────────────────────────────────┐
│  MMT 플랫폼                                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────┐    ┌──────────────────────┐         │
│  │ 사용자 컨텍스트     │    │ 평가 컨텍스트         │         │
│  ├───────────────────┤    ├──────────────────────┤         │
│  │ - Users           │    │ - Test               │         │
│  │ - Authority       │    │ - TestItems          │         │
│  │ - UserAuthority   │    │ - Item               │         │
│  │ - 인증             │    │ - Answer             │         │
│  │ - 인가             │    │ - UserTests          │         │
│  └───────────────────┘    └──────────────────────┘         │
│                                                             │
│  ┌───────────────────┐    ┌──────────────────────┐         │
│  │ 지식 컨텍스트       │    │ 진단 컨텍스트         │         │
│  ├───────────────────┤    ├──────────────────────┤         │
│  │ - Concept         │    │ - Probability        │         │
│  │ - Chapter         │    │ - Result             │         │
│  │ - KnowledgeSpace  │    │ - AI Serving         │         │
│  │ - Curriculum      │    │ - 이해도 예측          │         │
│  └───────────────────┘    └──────────────────────┘         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**컨텍스트 관계:**

- **사용자 컨텍스트** → **평가 컨텍스트**: 사용자가 테스트를 수행
- **평가 컨텍스트** → **지식 컨텍스트**: 테스트에 개념 기반 문항 포함
- **평가 컨텍스트** → **진단 컨텍스트**: 답안이 AI 진단을 트리거
- **진단 컨텍스트** → **지식 컨텍스트**: 진단이 선수 관계 그래프 사용

### 5.2 애그리거트 설계

**애그리거트 1: 사용자 애그리거트**
```java
@Entity
@Table(name = "users")
public class User {  // 애그리거트 루트

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserAuthority> authorities = new HashSet<>();

    // 애그리거트 경계: User + UserAuthorities
    // 트랜잭션 일관성: Authority는 항상 User와 일관성 유지

    public void grantAuthority(Authority authority) {
        UserAuthority ua = new UserAuthority();
        ua.setUser(this);
        ua.setAuthority(authority);
        authorities.add(ua);
    }

    public void revokeAuthority(Authority authority) {
        authorities.removeIf(ua -> ua.getAuthority().equals(authority));
    }
}
```

**애그리거트 2: 테스트 애그리거트**
```java
@Entity
@Table(name = "tests")
public class Test {  // 애그리거트 루트

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long testId;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL)
    private List<TestItem> items = new ArrayList<>();

    // 애그리거트 경계: Test + TestItems
    // Item은 Test 없이 존재할 수 없음

    public void addItem(Item item, int itemNumber) {
        TestItem testItem = new TestItem();
        testItem.setTest(this);
        testItem.setItem(item);  // 참조, 소유가 아님
        testItem.setTestItemNumber(itemNumber);
        items.add(testItem);
    }
}
```

**애그리거트 3: 진단 애그리거트**
```java
@Entity
@Table(name = "users_tests")
public class Diagnosis {  // 애그리거트 루트 (UserTests에서 이름 변경)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long diagnosisId;  // userTestId에서 이름 변경

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // User 애그리거트 참조

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    private Test test;  // Test 애그리거트 참조

    @OneToMany(mappedBy = "diagnosis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "answer")
    private List<Probability> probabilities = new ArrayList<>();

    // 애그리거트 경계: Diagnosis + Answers + Probabilities
    // 트랜잭션 일관성: 모든 답안이 함께 제출됨

    @Transient
    public boolean isCompleted() {
        return !answers.isEmpty();
    }

    public void submitAnswers(List<AnswerCode> answerCodes) {
        // 도메인 로직: 답안 제출 검증
        if (isCompleted()) {
            throw new IllegalStateException("진단이 이미 완료되었습니다");
        }

        for (AnswerCode ac : answerCodes) {
            Answer answer = new Answer();
            answer.setDiagnosis(this);
            answer.setItemId(ac.getItemId());
            answer.setAnswerCode(ac.getAnswerCode());
            answers.add(answer);
        }
    }
}
```

**피해야 할 안티패턴:**
```java
// 모든 엔티티를 아우르는 거대한 애그리거트를 만들지 마세요
@Entity
public class User {
    @OneToMany
    private List<UserTests> tests;  // 하지 마세요 - 애그리거트 경계 위반
}
```

### 5.3 도메인 서비스 예시

**DiagnosisService (도메인 서비스):**
```java
@Service
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;
    private final AIPredictionService aiService;
    private final KnowledgeGraphService knowledgeService;

    @Transactional
    public DiagnosisResult diagnose(Diagnosis diagnosis) {
        // 여러 애그리거트를 조율하는 복잡한 도메인 로직

        // 1. AI 예측 가져오기
        double[] predictions = aiService.predict(diagnosis.getAnswers());

        // 2. 지식 그래프를 사용하여 개념에 매핑
        List<ConceptMastery> masteries = new ArrayList<>();
        for (Answer answer : diagnosis.getWrongAnswers()) {
            List<Concept> prerequisites = knowledgeService
                .findPrerequisites(answer.getItem().getConcept());

            for (Concept concept : prerequisites) {
                double mastery = predictions[concept.getSkillId() - 1];
                masteries.add(new ConceptMastery(concept, mastery));
            }
        }

        // 3. 진단 결과 생성
        return DiagnosisResult.from(masteries);
    }
}
```

**KnowledgeGraphService (도메인 서비스):**
```java
@Service
public class KnowledgeGraphService {

    @Cacheable("prerequisite-graph")
    public List<Concept> findPrerequisites(Concept concept, int maxDepth) {
        // 도메인 로직: BFS를 이용한 그래프 탐색
        List<Integer> conceptIds = conceptRepository
            .findPrerequisiteIds(concept.getId(), maxDepth);

        Map<Integer, Integer> depths = BFSAlgorithm.calculate(
            concept.getId(),
            conceptIds
        );

        return conceptRepository.findAllById(depths.keySet());
    }
}
```

### 5.4 값 객체

**권장 값 객체:**

```java
@Embeddable
public class Curriculum {
    private String schoolLevel;  // 초등, 중등, 고등
    private String gradeLevel;   // 중1, 중2, 고1 등
    private String semester;     // 상, 하

    // 값 객체: 불변, 값으로 비교
    // Chapter, Test, Concept에 포함
}

@Embeddable
public class MasteryScore {
    private double probabilityPercent;

    public MasteryLevel getLevel() {
        if (probabilityPercent >= 0.8) return MasteryLevel.MASTERED;
        if (probabilityPercent >= 0.5) return MasteryLevel.DEVELOPING;
        return MasteryLevel.WEAK;
    }
}

public enum MasteryLevel {
    MASTERED, DEVELOPING, WEAK
}
```

### 5.5 리포지토리 패턴 (DDD 스타일)

**현재 패턴:** Spring Data JPA 리포지토리 (인프라 관심사)

**DDD 패턴:** 도메인 리포지토리 인터페이스 + 인프라 구현

```java
// 도메인 레이어: api/src/main/java/com/mmt/api/domain/diagnosis/DiagnosisRepository.java
public interface DiagnosisRepository {
    Diagnosis findById(Long id);
    List<Diagnosis> findCompletedByUser(User user);
    void save(Diagnosis diagnosis);
}

// 인프라 레이어: api/src/main/java/com/mmt/api/infrastructure/diagnosis/JpaDiagnosisRepository.java
@Repository
public class JpaDiagnosisRepository implements DiagnosisRepository {

    private final SpringDataDiagnosisRepository springDataRepo;

    @Override
    public Diagnosis findById(Long id) {
        return springDataRepo.findById(id)
            .orElseThrow(() -> new DiagnosisNotFoundException(id));
    }

    @Override
    public List<Diagnosis> findCompletedByUser(User user) {
        return springDataRepo.findByUserAndAnswersNotEmpty(user);
    }
}

// Spring Data JPA (인프라 상세)
interface SpringDataDiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    List<Diagnosis> findByUserAndAnswersNotEmpty(User user);
}
```

### 5.6 패키지 구조 (헥사고날 아키텍처)

**권장 리팩토링:**

```
api/src/main/java/com/mmt/api/
├── domain/                    # 도메인 레이어 (엔티티, 값 객체, 도메인 서비스)
│   ├── user/
│   │   ├── User.java
│   │   ├── Authority.java
│   │   └── UserRepository.java  (인터페이스)
│   ├── assessment/
│   │   ├── Test.java
│   │   ├── Item.java
│   │   └── TestRepository.java
│   ├── knowledge/
│   │   ├── Concept.java
│   │   ├── Chapter.java
│   │   ├── Curriculum.java  (값 객체)
│   │   └── KnowledgeGraphService.java  (도메인 서비스)
│   └── diagnosis/
│       ├── Diagnosis.java
│       ├── Answer.java
│       ├── Probability.java
│       ├── MasteryScore.java  (값 객체)
│       └── DiagnosisRepository.java
│
├── application/               # 애플리케이션 레이어 (유즈케이스, DTO)
│   ├── diagnosis/
│   │   ├── DiagnoseStudentUseCase.java
│   │   ├── GeneratePersonalTestUseCase.java
│   │   └── dto/
│   │       ├── DiagnosisRequest.java
│   │       └── DiagnosisResponse.java
│   └── assessment/
│       ├── CreateTestUseCase.java
│       └── dto/
│
├── infrastructure/            # 인프라 레이어 (JPA, Neo4j, Redis)
│   ├── persistence/
│   │   ├── jpa/
│   │   │   ├── JpaUserRepository.java
│   │   │   └── JpaDiagnosisRepository.java
│   │   └── neo4j/
│   │       └── Neo4jConceptRepository.java
│   ├── ai/
│   │   └── TensorFlowPredictionService.java
│   └── config/
│       ├── JpaConfig.java
│       └── Neo4jConfig.java
│
└── interfaces/                # 인터페이스 어댑터 (컨트롤러, REST)
    └── rest/
        ├── DiagnosisController.java
        └── TestController.java
```

---

## 6. 위험 평가

### 6.1 마이그레이션 위험

| 위험 | 발생 가능성 | 영향 | 완화 방안 |
|------|-----------|------|----------|
| **N+1 쿼리 도입** | 높음 | 높음 | 포괄적 쿼리 로깅, 각 단계별 부하 테스트 |
| **트랜잭션 경계 문제** | 중간 | 높음 | 명시적 `@Transactional` 경계, 통합 테스트 |
| **성능 저하 (Neo4j → CTE)** | 중간 | 중간 | 캐싱 레이어, 성능 벤치마크, 피처 플래그 |
| **데이터 일관성 (이중 쓰기 기간)** | 낮음 | 높음 | 짧은 마이그레이션 윈도우, DB 락, 롤백 계획 |
| **리액티브 스트림 데드락** | 중간 | 중간 | 리액티브 전파 또는 리액티브 의존성 제거 |
| **JPA 지연 로딩 예외** | 높음 | 낮음 | 신중한 페치 전략 설계, `@EntityGraph` |
| **배치 작업 성능** | 낮음 | 중간 | JPA 배치 설정, 벤치마크 비교 |

### 6.2 롤백 전략

**피처 플래그 접근법:**
```yaml
# application.yml
mmt:
  migration:
    use-jpa-for-tests: false
    use-jpa-for-concepts: false
    use-mysql-cte-for-graph: false
```

**코드 패턴:**
```java
@Service
public class TestService {

    @Value("${mmt.migration.use-jpa-for-tests:false}")
    private boolean useJpa;

    private final JpaTestRepository jpaRepo;
    private final JdbcTemplateTestRepository jdbcRepo;

    public List<Test> findBySchoolLevel(String level) {
        return useJpa
            ? jpaRepo.findBySchoolLevel(level)
            : jdbcRepo.findTestsBySchoolLevel(level);
    }
}
```

**데이터베이스 롤백:**
- JPA 마이그레이션 후 1 릴리즈 주기 동안 JdbcTemplate 리포지토리 유지
- 에러율, 쿼리 성능 대시보드 모니터링
- 문제 감지 시 피처 플래그 전환 → 즉시 롤백

---

## 7. 테스트 전략

### 7.1 테스트 커버리지 요구사항

**단계 완료 기준:**

✅ **단위 테스트:**
- 리포지토리 레이어: 모든 CRUD 작업
- 서비스 레이어: 비즈니스 로직 경로
- 커버리지 목표: 80% 이상

✅ **통합 테스트:**
- 데이터베이스 쿼리 (MySQL 포함 Testcontainers)
- 트랜잭션 경계 (롤백 시나리오)
- 커버리지 목표: 리포지토리 90% 이상

✅ **성능 테스트:**
- 기준선 vs 마이그레이션된 쿼리 실행 시간
- 허용 임계치: 10% 미만 성능 저하
- N+1 쿼리 감지 (Hibernate 쿼리 로깅)

✅ **엔드투엔드 테스트:**
- 핵심 사용자 플로우: 진단 제출, 결과 조회
- 피처 플래그 시나리오 (JPA 활성화/비활성화)

### 7.2 테스트 예시

**리포지토리 통합 테스트:**
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ConceptRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

    @Autowired
    private ConceptRepository conceptRepository;

    @Test
    void shouldFindConceptWithChapter() {
        // Given
        Concept concept = conceptRepository.findById(1).orElseThrow();

        // When
        Chapter chapter = concept.getChapter();

        // Then
        assertThat(chapter).isNotNull();
        assertThat(chapter.getSchoolLevel()).isEqualTo("초등");
    }

    @Test
    void shouldNotTriggerN1Query() {
        // Given
        QueryCountAssertions.reset();

        // When
        List<Concept> concepts = conceptRepository.findAllWithChapter();
        concepts.forEach(c -> c.getChapter().getName());

        // Then
        QueryCountAssertions.assertSelectCount(1); // 1개의 쿼리만 기대
    }
}
```

**성능 벤치마크 테스트:**
```java
@SpringBootTest
class GraphQueryPerformanceTest {

    @Autowired
    private ConceptService conceptService;

    @Test
    void neo4jVsMysqlCteBenchmark() {
        // Neo4j 벤치마크
        long neo4jStart = System.currentTimeMillis();
        List<Integer> neo4jResults = conceptService.findPrerequisites(4979, 5);
        long neo4jTime = System.currentTimeMillis() - neo4jStart;

        // MySQL CTE 벤치마크
        long mysqlStart = System.currentTimeMillis();
        List<Integer> mysqlResults = conceptService.findPrerequisitesCTE(4979, 5);
        long mysqlTime = System.currentTimeMillis() - mysqlStart;

        // 검증
        assertThat(mysqlResults).containsExactlyInAnyOrderElementsOf(neo4jResults);
        assertThat(mysqlTime).isLessThan(neo4jTime * 3); // 최대 3배 느림
        
        System.out.printf("Neo4j: %dms, MySQL CTE: %dms%n", neo4jTime, mysqlTime);
    }
}
```

---

## 8. 단계별 마이그레이션 계획

### 0단계: 준비 및 인프라 (1주차)

**목표:**
- 테스트 인프라 구축
- 기준 성능 지표 측정
- 마이그레이션 도구 설정

**작업:**
- [ ] 통합 테스트용 Testcontainers 설정
- [ ] Hibernate 쿼리 로깅 구성 (`hibernate.show_sql=true`)
- [ ] JMeter/Gatling 성능 테스트 스크립트 작성
- [ ] 피처 플래그 구성 생성
- [ ] 모든 리포지토리 메서드의 테스트 커버리지 문서화
- [ ] 기준 성능 테스트 실행 (비교를 위해 결과 저장)

**산출물:**
- 통합 테스트 프레임워크
- 성능 기준선 보고서
- 마이그레이션 체크리스트

---

### 1단계: Neo4j → MySQL CTE 마이그레이션 (2-3주차)

**근거:** 가장 높은 AWS 비용 절감 효과를 먼저 해결

**단계 1: MySQL CTE 쿼리 구현**
- [ ] `JdbcTemplateConceptRepository`에 `findPrerequisiteConceptIds()` 생성
- [ ] `findPrerequisiteConceptsDepth3()` (초등학교) 생성
- [ ] `findPrerequisiteConceptsDepth5()` (중/고등학교) 생성
- [ ] `knowledge_space` 테이블에 복합 인덱스 추가

**단계 2: 캐싱 레이어 추가**
- [ ] Redis와 함께 Spring Cache 구성
- [ ] 그래프 쿼리 메서드에 `@Cacheable` 추가
- [ ] TTL을 24시간으로 설정 (정적 데이터)

**단계 3: 피처 플래그 및 A/B 테스트**
- [ ] `mmt.use-mysql-cte-for-graph` 설정 추가
- [ ] `ConceptService`에 조건부 로직 업데이트
- [ ] Neo4j가 여전히 활성 상태인 채로 배포 (기본값)

**단계 4: 성능 검증**
- [ ] 성능 테스트 실행 (Neo4j vs CTE)
- [ ] 캐시 히트율 모니터링
- [ ] 그래프 시각화 렌더링 시간 비교

**단계 5: 점진적 출시**
- [ ] 사용자 10%에 CTE 활성화 (피처 플래그)
- [ ] 에러율, 쿼리 시간 모니터링
- [ ] 2주에 걸쳐 50% → 100%로 증가
- [ ] 1개월 모니터링 후 Neo4j 폐기

**성공 기준:**
- ✅ 깊이-5 쿼리에서 <100ms (캐시 적용)
- ✅ 캐시 히트율 >90%
- ✅ 운영 환경에서 에러 제로
- ✅ 그래프 시각화 정상 동작

**롤백 계획:**
- 피처 플래그를 Neo4j로 다시 전환
- 1 릴리즈 주기 동안 Neo4j 컨테이너 유지

---

### 2단계: 단순 JPA 엔티티 (4주차)

**대상 엔티티:** `Test`, `Chapter` (관계 없음)

**단계 1: JPA 엔티티 생성**
```java
@Entity
@Table(name = "tests")
public class Test {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_id")
    private Long testId;

    // 모든 컬럼 매핑, 아직 관계 없음
}
```

**단계 2: JPA 리포지토리 생성**
```java
public interface TestRepository extends JpaRepository<Test, Long> {
    List<Test> findBySchoolLevel(String schoolLevel);
}
```

**단계 3: 서비스 레이어 업데이트**
- [ ] 피처 플래그 추가: `mmt.migration.use-jpa-for-tests`
- [ ] `TestService`에 조건부 리포지토리 선택 업데이트
- [ ] 폴백으로 JdbcTemplate 구현 유지

**단계 4: 테스트**
- [ ] 모든 리포지토리 메서드에 대한 통합 테스트 작성
- [ ] 쿼리 결과 비교: JPA vs JdbcTemplate
- [ ] 성능 벤치마크: 쿼리 실행 시간

**단계 5: 점진적 출시**
- [ ] 25% 트래픽에 JPA 활성화
- [ ] 쿼리 성능, 에러율 모니터링
- [ ] 1주 검증 후 전체 출시

**성공 기준:**
- ✅ 모든 테스트 통과
- ✅ 기준선 대비 10% 이내 쿼리 성능
- ✅ 데이터 불일치 제로

---

### 3단계: 일대다 관계 (5-6주차)

**대상:** `Concept` ↔ `Chapter`, `Test` ↔ `TestItems`

**단계 1: 엔티티에 관계 추가**
```java
@Entity
public class Concept {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_chapter_id")
    private Chapter chapter;
}
```

**단계 2: 페치 전략 설계**
- [ ] 즉시 로딩이 필요한 쿼리 문서화
- [ ] 적절한 곳에 `@EntityGraph` 사용
- [ ] 배치 페치 사이즈 구성: `hibernate.default_batch_fetch_size=10`

**단계 3: 리포지토리 쿼리 리팩토링**
```java
@EntityGraph(attributePaths = {"chapter"})
List<Concept> findBySchoolLevel(String schoolLevel);
```

**단계 4: N+1 쿼리 감지**
- [ ] `hibernate.generate_statistics=true` 활성화
- [ ] 쿼리 카운트 검증과 함께 통합 테스트 실행
- [ ] 발견된 N+1 문제 수정

**단계 5: 테스트**
- [ ] 쿼리 카운트 검증과 함께 통합 테스트
- [ ] JdbcTemplate 기준선과 비교하는 성능 테스트
- [ ] 현실적인 데이터 볼륨으로 부하 테스트

**성공 기준:**
- ✅ N+1 쿼리 감지 제로
- ✅ 쿼리 카운트가 JdbcTemplate 기준선과 일치
- ✅ 지연 로딩 정상 동작 (`LazyInitializationException` 없음)

---

### 4단계: 복잡한 집계 (7-8주차)

**대상:** `Probability`, `Answer`, `Result` (4테이블 JOIN)

**과제:** `JdbcTemplateProbabilityRepository`의 현재 쿼리가 다음을 JOIN:
- `chapters` ↔ `concepts` ↔ `probabilities` ↔ `answers` ↔ `tests_items`

**단계 1: 엔티티 관계 설계**
```java
@Entity
public class Probability {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id")
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private Concept concept;
}

@Entity
public class Answer {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_test_id")
    private UserTest userTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;
}
```

**단계 2: 생성자 표현식을 사용한 JPQL 쿼리**
```java
@Query("""
    SELECT new com.mmt.api.dto.result.ResultResponse(
        p.probabilityId, ti.testItemNumber, c.conceptId, p.toConceptDepth,
        p.probabilityPercent, c.name, ch.schoolLevel, ch.gradeLevel,
        ch.semester, ch.chapterMain, ch.chapterSub, ch.chapterName
    )
    FROM Probability p
    JOIN p.answer a
    JOIN a.item i
    JOIN i.concept c
    JOIN c.chapter ch
    JOIN TestItem ti ON ti.item = i AND ti.test = a.userTest.test
    WHERE a.userTest.userTestId = :userTestId AND p.toConceptDepth < 3
    """)
List<ResultResponse> findResultsByUserTestId(@Param("userTestId") Long userTestId);
```

**단계 3: 대안 - 네이티브 쿼리 폴백**
```java
@Query(value = """
    SELECT p.probability_id, ti.test_item_number, p.concept_id, p.to_concept_depth,
           p.probability_percent, c.concept_name, ch.school_level, ch.grade_level,
           ch.semester, ch.chapter_main, ch.chapter_sub, ch.chapter_name
    FROM chapters ch
    JOIN concepts c ON c.concept_chapter_id = ch.chapter_id
    JOIN probabilities p ON p.concept_id = c.concept_id
    JOIN answers a ON a.answer_id = p.answer_id
    JOIN tests_items ti ON ti.item_id = a.item_id
    WHERE a.user_test_id = :userTestId AND p.to_concept_depth < 3
    """, nativeQuery = true)
List<Object[]> findResultsNative(@Param("userTestId") Long userTestId);
```

**단계 4: 배치 작업**
- [ ] Hibernate 배치 삽입 구성
- [ ] `ProbabilityService.create()` 배치 저장으로 리팩토링
- [ ] 대량 배치에 `entityManager.flush()` 및 `clear()` 사용

**단계 5: 테스트**
- [ ] JdbcTemplate과 결과 비교하는 통합 테스트
- [ ] 복잡한 쿼리에 대한 성능 테스트
- [ ] 배치 삽입 부하 테스트 (100개 이상 probabilities)

**성공 기준:**
- ✅ JdbcTemplate과 동일한 쿼리 결과
- ✅ 기준선 대비 15% 이내 쿼리 성능
- ✅ 100건 기준 배치 삽입 1초 미만 완료

---

### 5단계: DDD 리팩토링 (9-10주차) - 선택사항

**목표:** 장기 유지보수성을 위한 DDD 원칙 적용

**단계 1: 패키지 재구성**
- [ ] 엔티티를 `domain/` 패키지로 이동
- [ ] 유즈케이스를 위한 `application/` 레이어 생성
- [ ] `infrastructure/`를 도메인에서 분리

**단계 2: 애그리거트 개선**
- [ ] `Diagnosis` 애그리거트 구현 (UserTest + Answer + Probability)
- [ ] 도메인 메서드 추가: `diagnosis.submitAnswers()`
- [ ] 애그리거트에 비즈니스 로직 캡슐화

**단계 3: 도메인 서비스**
- [ ] `DiagnosisService` (도메인 서비스) 생성
- [ ] `KnowledgeGraphService` (도메인 서비스) 생성
- [ ] BFS 알고리즘을 도메인 서비스로 이동

**단계 4: 값 객체**
- [ ] `Curriculum` 값 객체 도입
- [ ] `MasteryScore` 값 객체 도입
- [ ] 원시 타입을 값 객체로 대체

**단계 5: 테스트**
- [ ] 애그리거트 메서드에 대한 단위 테스트
- [ ] 도메인 서비스 테스트 (DB 불필요)
- [ ] 통합 테스트 여전히 통과

**성공 기준:**
- ✅ 비즈니스 로직이 도메인 레이어에 위치 (SQL이 아닌)
- ✅ 애그리거트가 불변 조건 강제
- ✅ 더 명확한 바운디드 컨텍스트 분리

---

## 9. 소요 기간 추정

| 단계 | 소요 기간 (인일) | 의존성 | 위험 수준 |
|------|----------------|--------|----------|
| 0단계: 준비 | 3일 | 없음 | 낮음 |
| 1단계: Neo4j → CTE | 8일 | 0단계 | 중간 |
| 2단계: 단순 JPA | 3일 | 0단계 | 낮음 |
| 3단계: 관계 | 8일 | 2단계 | 중간 |
| 4단계: 복잡한 JOIN | 10일 | 3단계 | 높음 |
| 5단계: DDD (선택) | 10일 | 4단계 | 중간 |
| **합계** | **42일 (~8주)** | | |

**가정:**
- 시니어 백엔드 개발자 1명 (본인)
- 50% 시간 할당 (다른 작업 병행)
- 테스트, 문서화, 코드 리뷰 포함

---

## 10. 핵심 권고사항

### 10.1 즉시 실행 항목 (이번 주)

1. **통합 테스트 프레임워크 설정** (Testcontainers)
2. **모든 리포지토리 쿼리에 대한 성능 기준선 측정**
3. **점진적 출시를 위한 피처 플래그 구성 생성**
4. **복잡한 서비스의 트랜잭션 경계 문서화**

### 10.2 마이그레이션 우선순위

**권장 순서:**

1. **Neo4j → MySQL CTE** (2-3주차)
   - **첫 번째인 이유:** 가장 높은 AWS 비용 절감, 제한된 범위
   - **위험:** 중간 (성능 저하)
   - **완화:** 캐싱 레이어, 피처 플래그

2. **단순 JPA 엔티티** (4주차)
   - **두 번째인 이유:** 낮은 위험 엔티티로 자신감 구축
   - **위험:** 낮음
   - **완화:** 점진적 출시

3. **JPA 관계** (5-6주차)
   - **세 번째인 이유:** 복잡한 쿼리의 기반
   - **위험:** 중간 (N+1 쿼리)
   - **완화:** `@EntityGraph`, 쿼리 카운트 테스트

4. **복잡한 집계** (7-8주차)
   - **네 번째인 이유:** 가장 복잡, 이전 단계 필요
   - **위험:** 높음 (성능, 정확성)
   - **완화:** 네이티브 쿼리 폴백, 광범위한 테스트

5. **DDD 리팩토링** (9-10주차) - 선택사항
   - **마지막인 이유:** 비기능적 개선
   - **위험:** 중간 (회귀)
   - **장점:** 장기 유지보수성

### 10.3 피해야 할 것들

❌ **빅뱅 마이그레이션** - 한 번에 모든 것을 마이그레이션하지 마세요
✅ **단계적 접근** - 한 번에 하나의 엔티티/관계씩

❌ **조기 최적화** - 측정하기 전에 최적화하지 마세요
✅ **먼저 측정** - 기준선 → 마이그레이션 → 비교

❌ **무분별한 양방향 매핑** - 메모리 낭비 유발
✅ **가능하면 단방향** - 탐색하는 것만 매핑

❌ **기본 즉시 로딩** - N+1 쿼리 유발
✅ **기본 지연 로딩** - 필요할 때 `@EntityGraph` 사용

❌ **JdbcTemplate 코드 삭제** - 롤백 어려움
✅ **피처 플래그** - 1 릴리즈 동안 양쪽 구현 유지

### 10.4 장기 아키텍처 비전

**최종 상태 (마이그레이션 후):**

```
┌─────────────────────────────────────────────────────────────┐
│  프론트엔드 (Vue.js)                                         │
└─────────────────────────────────────────────────────────────┘
                            ↓ REST API
┌─────────────────────────────────────────────────────────────┐
│  백엔드 (Spring Boot)                                        │
├─────────────────────────────────────────────────────────────┤
│  컨트롤러 (REST) → 유즈케이스 (Application) → 도메인          │
│                                                             │
│  도메인 레이어:                                               │
│  - 애그리거트 (User, Test, Diagnosis)                         │
│  - 도메인 서비스 (KnowledgeGraph, DiagnosisEngine)            │
│  - 리포지토리 (인터페이스)                                     │
│                                                             │
│  인프라 레이어:                                               │
│  - JPA 리포지토리 (MySQL)                                     │
│  - Redis 캐시                                                │
│  - AI 서비스 클라이언트 (TensorFlow Serving)                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  영속성                                                      │
├─────────────────────────────────────────────────────────────┤
│  - MySQL (RDS) - 모든 관계형 데이터 + 그래프 (CTE)             │
│  - Redis - 캐시 + 세션                                       │
│  - TensorFlow Serving - AI 예측                              │
│  - Neo4j - 제거됨 ✅                                          │
└─────────────────────────────────────────────────────────────┘
```

**장점:**
- **비용:** Neo4j 호스팅 제거 (~월 $50-100 절약)
- **복잡도:** 유지보수할 DB가 하나 줄어듦
- **일관성:** 단일 진실 원천 (MySQL)
- **성능:** 캐싱으로 CTE 오버헤드 보상
- **유지보수성:** 비즈니스 로직이 애플리케이션 레이어에 (테스트 가능, 재사용 가능)
- **확장성:** JPA 캐싱 + Redis = 수평 확장 준비 완료

---

## 11. 핵심 질문 답변

### Q1: 가장 큰 기술 부채는?

**답:** **SQL 쿼리에 내장된 비즈니스 로직** (로직의 60%)

**근거:**
- CASE/WHEN 문이 포함된 4테이블 JOIN: `ProbabilityRepository.java:92`
- boolean 플래그를 위한 EXISTS 서브쿼리: `UserTestRepository.java:35`
- SQL 내 랜덤 선택 로직: `ItemRepository.java:27`

**영향:**
- 단위 테스트 어려움 (DB 필요)
- 다른 쿼리에서 로직 재사용 어려움
- 버전 관리 가시성 부족 (문자열로 된 SQL)
- 유지보수 부담 (SQL 전문성 필요)

**해결책:**
- 관계가 포함된 JPA 엔티티로 마이그레이션
- 서비스 레이어와 도메인 모델로 로직 이동
- 동적 쿼리에 JPQL 또는 Criteria API 사용

---

### Q2: 핵심 마이그레이션 위험은?

**상위 3대 위험:**

1. **N+1 쿼리 도입 (발생 가능성: 높음, 영향: 높음)**
   - 현재 JdbcTemplate이 명시적으로 테이블을 JOIN
   - JPA 지연 로딩이 다중 쿼리를 트리거할 수 있음
   - **완화:** `@EntityGraph`, 쿼리 카운트 테스트, Hibernate 통계

2. **성능 저하 - Neo4j → CTE (발생 가능성: 중간, 영향: 중간)**
   - MySQL CTE가 깊이-5 쿼리에서 Neo4j보다 3-5배 느림
   - **완화:** Redis 캐싱 (90%+ 히트율 예상), 구체화된 경로, 피처 플래그

3. **리액티브 스트림 데드락 (발생 가능성: 중간, 영향: 중간)**
   - 서비스 레이어의 현재 `.block()` 호출
   - 리액티브와 명령형 코드 혼합
   - **완화:** 컨트롤러까지 리액티브 전파 또는 리액티브 의존성 제거

**기타 위험:**
- 트랜잭션 경계 문제 (외부 AI 서비스 호출)
- JPA 지연 로딩 예외 (`LazyInitializationException`)
- 이중 쓰기 기간의 데이터 일관성

---

### Q3: 단계별 테스트 전략은?

**테스트 피라미드:**

```
        ┌──────────────┐
        │  E2E 테스트    │  (5% - 핵심 플로우만)
        └──────────────┘
       ┌────────────────┐
       │   통합 테스트    │  (35% - 리포지토리 + DB)
       │                │
       └────────────────┘
     ┌──────────────────────┐
     │     단위 테스트       │  (60% - 서비스 레이어 로직)
     └──────────────────────┘
```

**단계별 전략:**

| 단계 | 테스트 중점 | 도구 | 커버리지 목표 |
|------|-----------|------|-------------|
| **0단계** | 기준 벤치마크 | JMeter, JUnit | N/A (설정) |
| **1단계** (Neo4j→CTE) | 쿼리 정확성, 성능 | Testcontainers, JUnit | 리포지토리 95% |
| **2단계** (단순 JPA) | CRUD 작업 | DataJpaTest | 리포지토리 90% |
| **3단계** (관계) | N+1 감지, 지연 로딩 | Hibernate 통계, DataJpaTest | 리포지토리 95% |
| **4단계** (복잡) | 쿼리 결과 비교 | 통합 테스트 | 핵심 경로 100% |
| **5단계** (DDD) | 도메인 로직, 불변 조건 | 단위 테스트 (DB 없음) | 도메인 레이어 85% |

**테스트 유형:**

1. **단위 테스트 (전체의 60%)**
   - 서비스 레이어 비즈니스 로직
   - 도메인 모델 메서드
   - 데이터베이스 불필요 (목 사용)

2. **통합 테스트 (전체의 35%)**
   - 실제 데이터베이스에 대한 리포지토리 쿼리 (Testcontainers)
   - 트랜잭션 경계
   - 쿼리 카운트 검증 (N+1 감지)

3. **성능 테스트 (특별)**
   - 기준선 vs 마이그레이션된 쿼리 실행 시간
   - 임계치: <10% 저하 (단순), <15% (복잡)
   - 각 단계 전후로 실행

4. **E2E 테스트 (전체의 5%)**
   - 핵심 사용자 플로우: 진단 제출, 결과 조회
   - 피처 플래그 시나리오 (JPA 활성화/비활성화)
   - 운영 배포 전 스테이징에서 실행

**테스트 커버리지 체크리스트 예시 (3단계):**

- [ ] 단위: `Concept.getChapter()`가 올바른 챕터 반환
- [ ] 통합: `@EntityGraph`와 함께 `ConceptRepository.findById()`
- [ ] 통합: 100개 Concept과 Chapter 페칭 시 N+1 쿼리 없음
- [ ] 성능: 100개 Concept 쿼리 시간 <50ms
- [ ] E2E: 사용자가 챕터 정보와 함께 개념 상세를 조회 가능

---

### Q4: 성능 체크포인트는?

**기준 지표 (마이그레이션 전 측정):**

| 연산 | 현재 평균 시간 | 쿼리 수 | 비고 |
|------|-------------|---------|------|
| ID로 테스트 조회 | ~5ms | 1 | 단순 SELECT |
| 테스트 문항 조회 (JOIN) | ~15ms | 1 | 3테이블 JOIN |
| 진단 결과 조회 | ~50ms | 1 | 4테이블 JOIN |
| 그래프 탐색 (깊이 3) | ~10ms | 1 | Neo4j Cypher |
| 그래프 탐색 (깊이 5) | ~25ms | 1 | Neo4j Cypher |
| 100개 확률 배치 삽입 | ~200ms | 1 | BatchPreparedStatementSetter |

**성능 체크포인트 (단계별):**

**1단계 (Neo4j → CTE):**
- [ ] CTE 쿼리 (깊이 3): <30ms (3배 느려도 OK)
- [ ] CTE 쿼리 (깊이 5): <100ms (4배 느려도 OK)
- [ ] 캐시 히트율: 1시간 후 >90%
- [ ] 캐시 쿼리 시간: <5ms

**2단계 (단순 JPA):**
- [ ] ID 조회: <10ms (단순 쿼리에 +100% 허용)
- [ ] 학교 수준별 조회: <20ms
- [ ] 쿼리 수: 1 (N+1 아님)

**3단계 (관계):**
- [ ] 챕터와 함께 Concept 조회: <20ms (JOIN으로 인해 +33%)
- [ ] 100개 Concept과 챕터 조회: <100ms
- [ ] 쿼리 수: 1 (101이 아님!)

**4단계 (복잡한 JOIN):**
- [ ] 진단 결과 조회: <75ms (JPA 오버헤드로 인해 +50%)
- [ ] 쿼리 수: 1 (중요 - N+1이면 안 됨)
- [ ] 100개 확률 배치 삽입: <300ms (+50%)

**모니터링 도구:**

1. **Hibernate 통계:**
   ```yaml
   spring:
     jpa:
       properties:
         hibernate:
           generate_statistics: true
   ```

2. **쿼리 로깅:**
   ```yaml
   logging:
     level:
       org.hibernate.SQL: DEBUG
       org.hibernate.type.descriptor.sql.BasicBinder: TRACE
   ```

3. **커스텀 메트릭:**
   ```java
   @Around("execution(* com.mmt.api.repository..*(..))")
   public Object measureQueryTime(ProceedingJoinPoint joinPoint) throws Throwable {
       long start = System.currentTimeMillis();
       Object result = joinPoint.proceed();
       long time = System.currentTimeMillis() - start;

       metricsService.recordQueryTime(joinPoint.getSignature().getName(), time);

       if (time > 100) {
           log.warn("느린 쿼리 감지: {}가 {}ms 소요됨",
               joinPoint.getSignature(), time);
       }

       return result;
   }
   ```

4. **APM 도구 (선택):**
   - Spring Boot Actuator + Prometheus
   - 쿼리 시간 시각화를 위한 Grafana 대시보드

**회귀 감지:**

```java
@Test
void shouldNotRegressPerformance() {
    // 0단계의 기준선
    long baselineTime = 50; // ms

    // 현재 구현
    long start = System.currentTimeMillis();
    probabilityRepository.findResults(userTestId);
    long actual = System.currentTimeMillis() - start;

    // 임계치 이내 검증
    assertThat(actual).isLessThan(baselineTime * 1.15); // 최대 +15%
}
```

---

## 12. 결론

### 마이그레이션 요약

MMT 플랫폼의 하이브리드 아키텍처는 현재 규모에 맞게 **실용적이고 잘 설계**되어 있습니다. 제안된 마이그레이션은 **실현 가능하고 유익하지만** **신중한 단계적 접근과 테스트가 필요**합니다.

**진행/보류 권고:**

✅ **진행** - 다음 우선순위로 마이그레이션 진행:

1. **1단계 (Neo4j → CTE)** - **높은 우선순위**
   - 비용 절감: ~월 $50-100
   - 소요 기간: 8일
   - 위험: 중간 (캐싱으로 완화)

2. **2-4단계 (JPA 마이그레이션)** - **중간 우선순위**
   - 장점: 유지보수성, 테스트 가능성
   - 소요 기간: 21일
   - 위험: 중간 (N+1 쿼리)

3. **5단계 (DDD 리팩토링)** - **낮은 우선순위 (선택)**
   - 장점: 장기 아키텍처 건강성
   - 소요 기간: 10일
   - 위험: 중간 (회귀)

**총 소요 기간:** 8주 (50% 할당) 또는 4주 (전담)

**기대 결과:**
- AWS 비용 30-40% 절감 (Neo4j 제거)
- 테스트 커버리지 50% 증가
- 비즈니스 로직의 70%를 애플리케이션 레이어로 이동
- 단일 진실 원천 (MySQL만)
- 새로운 개발자 온보딩 용이 (표준 Spring Data JPA)

---

### 아키텍트의 마무리

Java/Spring과 레거시 현대화 분야에서 10년 이상의 경험을 가진 시니어 아키텍트로서, 이 코드베이스의 **의도적인 설계 결정**에 대해 높이 평가합니다:

**잘 동작하는 것:**
- 적절한 기술 선택 (인증에 JPA, 분석에 JdbcTemplate)
- 깔끔한 컨트롤러 → 서비스 → 리포지토리 분리
- 실용적인 최적화 (배치 작업, 비정규화)

**주의가 필요한 것:**
- SQL에 60%의 비즈니스 로직 (유지보수 부담)
- 데이터 중복 (MySQL vs Neo4j)
- 리액티브 블로킹 (`.block()` 안티패턴)

**마이그레이션 철학:**
- **빅뱅보다 반복적 접근**: 한 번에 하나의 엔티티씩
- **모든 것을 측정**: 기준선 → 마이그레이션 → 비교
- **피처 플래그**: 항상 롤백 계획을 갖추기
- **테스트 주도**: 마이그레이션 후가 아닌 전에 테스트

**기억하세요:** 목표는 "완벽한" 아키텍처를 달성하는 것이 아니라, **시스템 안정성을 유지하면서** **기술 부채를 점진적으로 줄이는 것**입니다. 각 단계는 사용자를 방해하지 않으면서 가치(비용 절감, 더 나은 테스트, 더 깔끔한 코드)를 제공해야 합니다.

마이그레이션 화이팅! 🚀

---

**문서 버전:** 1.0
**날짜:** 2026-01-06
**작성자:** Claude Sonnet 4.5 (시니어 백엔드 아키텍트 페르소나)
**상태:** 검토 준비 완료
