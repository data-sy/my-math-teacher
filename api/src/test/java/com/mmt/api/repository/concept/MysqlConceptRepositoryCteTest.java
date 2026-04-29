package com.mmt.api.repository.concept;

import com.mmt.api.config.TestcontainersConfig;
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
}
