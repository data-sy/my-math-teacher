package com.mmt.api.repository.concept;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.Concept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 Spec 01 Task 1.5: {@link MysqlConceptRepositoryCteImpl} 단위 테스트.
 *
 * 검증 범위:
 *  - depth 0 → 자기 자신만
 *  - depth 1 → 직접 선수 + 자기 자신
 *  - depth N → N 단계 이내 모든 선수
 *  - 다중 경로 → DISTINCT 평탄화
 *  - 고립 노드 → 자기 자신만
 *  - 존재하지 않는 conceptId → 빈 리스트
 *  - 음수 maxDepth → IllegalArgumentException
 *
 * 인프라: Testcontainers MySQL 8 + 본 테스트 전용 schema/seed (cte_test_*.sql).
 * production schema 와 동일한 인덱스 정의를 사용해 EXPLAIN 동작 일관성 보장.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {"/sql/cte_test_schema.sql", "/sql/cte_test_seed.sql"})
class MysqlConceptRepositoryCteTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MysqlConceptRepositoryCteImpl repository;

    @BeforeEach
    void setUp() {
        repository = new MysqlConceptRepositoryCteImpl(jdbcTemplate);
    }

    @Test
    void depth0ReturnsOnlyStartNode() {
        List<Integer> result = repository.findPrerequisiteConceptIds(10, 0);
        assertThat(result).containsExactly(10);
    }

    @Test
    void depth1ReturnsDirectPrerequisitesAndSelf() {
        List<Integer> result = repository.findPrerequisiteConceptIds(10, 1);
        assertThat(result).containsExactlyInAnyOrder(10, 1, 2);
    }

    @Test
    void depth5ReturnsAllReachablePrerequisites() {
        List<Integer> result = repository.findPrerequisiteConceptIds(10, 5);
        assertThat(result).containsExactlyInAnyOrder(10, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void multiplePathsToSameNodeAreDeduplicated() {
        // depth 2 에서 3 은 (10 → 1 → 3) 과 (10 → 2 → 3) 두 경로로 도달.
        // CTE 내부에서는 (3, 2) 가 두 row 로 쌓이지만 외부 SELECT DISTINCT 가 평탄화.
        List<Integer> result = repository.findPrerequisiteConceptIds(10, 2);
        assertThat(result).containsExactlyInAnyOrder(10, 1, 2, 3);
        assertThat(result).hasSize(4);
    }

    @Test
    void isolatedNodeReturnsOnlySelf() {
        List<Integer> result = repository.findPrerequisiteConceptIds(7, 5);
        assertThat(result).containsExactly(7);
    }

    @Test
    void nonExistentConceptIdReturnsEmpty() {
        List<Integer> result = repository.findPrerequisiteConceptIds(999, 5);
        assertThat(result).isEmpty();
    }

    @Test
    void negativeMaxDepthThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> repository.findPrerequisiteConceptIds(10, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth");
    }

    // ─── findPrerequisiteConcepts (객체 반환, ADR 0006: concepts JOIN chapters) ───

    @Test
    void findPrerequisiteConceptsDepth0ReturnsOnlyStartNodeWithChapterFields() {
        List<Concept> result = repository.findPrerequisiteConcepts(10, 0);

        assertThat(result).hasSize(1);
        Concept c = result.get(0);
        assertThat(c.getConceptId()).isEqualTo(10);
        assertThat(c.getName()).isEqualTo("start node");
        assertThat(c.getDesc()).isEqualTo("desc 10");
        assertThat(c.getChapterId()).isEqualTo(1);
        assertThat(c.getAchievementId()).isEqualTo(1);
        assertThat(c.getAchievementName()).isEqualTo("achievement 1");
        assertThat(c.getSchoolLevel()).isEqualTo("초등");
        assertThat(c.getGradeLevel()).isEqualTo("초1");
        assertThat(c.getSemester()).isEqualTo("1학기");
        assertThat(c.getChapterName()).isEqualTo("cte-test-chapter");
        assertThat(c.getChapterMain()).isEqualTo("");
        assertThat(c.getChapterSub()).isEqualTo("9까지의 수");
        // ADR 0006: section 매핑 생략 → null
        assertThat(c.getSection()).isNull();
    }

    @Test
    void findPrerequisiteConceptsDepth5ReturnsAllReachable() {
        List<Concept> result = repository.findPrerequisiteConcepts(10, 5);
        assertThat(result).extracting(Concept::getConceptId)
            .containsExactlyInAnyOrder(10, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void findPrerequisiteConceptsDeduplicatesMultiplePaths() {
        // depth 2 의 노드 3 은 두 경로로 도달 — 외부 SELECT 의 DISTINCT subquery 가 1 row 로 평탄화
        List<Concept> result = repository.findPrerequisiteConcepts(10, 2);
        assertThat(result).extracting(Concept::getConceptId)
            .containsExactlyInAnyOrder(10, 1, 2, 3);
        assertThat(result).hasSize(4);
    }

    @Test
    void findPrerequisiteConceptsIsolatedNodeReturnsOnlySelf() {
        List<Concept> result = repository.findPrerequisiteConcepts(7, 5);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConceptId()).isEqualTo(7);
    }

    @Test
    void findPrerequisiteConceptsNonExistentReturnsEmpty() {
        List<Concept> result = repository.findPrerequisiteConcepts(999, 5);
        assertThat(result).isEmpty();
    }

    @Test
    void findPrerequisiteConceptsNegativeMaxDepthThrows() {
        assertThatThrownBy(() -> repository.findPrerequisiteConcepts(10, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth");
    }

    /**
     * 인덱스 등록 + 재귀 CTE 의 옵티마이저 인식 회귀 보호.
     *
     * 작은 단위 테스트 시드에서는 옵티마이저가 인덱스를 실제로 선택하지 않을 수 있다 (full
     * scan 이 더 싸다고 판단). 따라서 EXPLAIN 의 {@code key} 컬럼이 NULL 일 수 있으나,
     * {@code possible_keys} 에는 의도한 인덱스가 잡혀야 한다. production 통계 하의 실제
     * 선택은 spec-03 Task 4.2 부하 테스트에서 다시 확인.
     *
     * 결과 dump 는 stdout 으로 출력 — PR 본문 첨부용.
     */
    @Test
    void cteRecursiveQueryUsesExpectedIndexes() {
        // 1) SHOW INDEX 로 정의 확인
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
            "SHOW INDEX FROM knowledge_space");
        Set<String> keyNames = indexes.stream()
            .map(row -> (String) row.get("Key_name"))
            .collect(Collectors.toSet());
        assertThat(keyNames).contains(
            "idx_knowledge_space_from",
            "idx_knowledge_space_to",
            "idx_knowledge_space_composite");

        System.out.println("=== SHOW INDEX FROM knowledge_space ===");
        indexes.forEach(row ->
            System.out.println("  " + row.get("Key_name") + " on " + row.get("Column_name")
                + " (seq=" + row.get("Seq_in_index") + ")"));

        // 2) EXPLAIN possible_keys 에 의도한 인덱스가 잡혀야 함
        List<Map<String, Object>> explain = jdbcTemplate.queryForList("""
            EXPLAIN
            WITH RECURSIVE prerequisite_path AS (
                SELECT concept_id, 0 AS depth
                FROM concepts WHERE concept_id = 10

                UNION ALL

                SELECT c.concept_id, pp.depth + 1
                FROM prerequisite_path pp
                JOIN knowledge_space ks ON pp.concept_id = ks.to_concept_id
                JOIN concepts         c ON ks.from_concept_id = c.concept_id
                WHERE pp.depth < 5
            )
            SELECT DISTINCT concept_id FROM prerequisite_path
            """);

        boolean possibleKeysHasKnowledgeSpaceIndex = explain.stream()
            .map(row -> (String) row.get("possible_keys"))
            .filter(pk -> pk != null)
            .anyMatch(pk -> pk.contains("idx_knowledge_space"));
        assertThat(possibleKeysHasKnowledgeSpaceIndex)
            .as("EXPLAIN possible_keys should reference idx_knowledge_space_*")
            .isTrue();

        System.out.println("=== EXPLAIN WITH RECURSIVE (start=10, maxDepth=5) ===");
        explain.forEach(row ->
            System.out.println("  id=" + row.get("id")
                + " select_type=" + row.get("select_type")
                + " table=" + row.get("table")
                + " type=" + row.get("type")
                + " possible_keys=" + row.get("possible_keys")
                + " key=" + row.get("key")
                + " rows=" + row.get("rows")));
    }
}
