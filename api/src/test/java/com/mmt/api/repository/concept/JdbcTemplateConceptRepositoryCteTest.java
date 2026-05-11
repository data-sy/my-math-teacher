package com.mmt.api.repository.concept;

import com.mmt.api.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2 spec-01 Task 1.5: 재귀 CTE 단위 테스트.
 *
 * @Sql 의 스키마·시드는 src/test/resources/ 의 cte_test_*.sql 파일 (test classpath).
 * @JdbcTest 는 JPA 엔티티 스캔을 하지 않아 ddl-auto 가 작동하지 않으므로
 * @Sql 로 명시적 스키마 부트스트랩이 필요. @AutoConfigureTestDatabase(replace=NONE)
 * 로 in-memory DB 대체를 막고 Testcontainers MySQL 을 사용.
 */
@JdbcTest
@Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {"classpath:cte_test_schema.sql", "classpath:cte_test_seed.sql"})
class JdbcTemplateConceptRepositoryCteTest {

    @Autowired
    JdbcTemplateConceptRepository repository;

    @Test
    void anchorOnly_whenMaxDepthZero() {
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(200, 0);

        assertThat(result).containsExactly(new ConceptDepth(200, 0));
    }

    @Test
    void directPrerequisite_whenMaxDepthOne() {
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(200, 1);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(200, 0),
            new ConceptDepth(210, 1)
        );
    }

    @Test
    void multiStepChain() {
        // 시드 체인: 300 의 선수 = 310 → 320 → 330.
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(300, 3);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(300, 0),
            new ConceptDepth(310, 1),
            new ConceptDepth(320, 2),
            new ConceptDepth(330, 3)
        );
    }

    @Test
    void emptyResult_whenConceptIdNotExists() {
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(999, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void throwsIllegalArgument_whenNegativeMaxDepth() {
        assertThatThrownBy(() -> repository.findPrerequisitesWithDepth(200, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDepth");
    }

    @Test
    void multiplePathsAbsorbedToMinDepth() {
        // 400 → {410, 420}, 410 → 430, 420 → 430. 430 은 두 경로로 depth 2 도달.
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(400, 2);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(400, 0),
            new ConceptDepth(410, 1),
            new ConceptDepth(420, 1),
            new ConceptDepth(430, 2)
        );
    }

    @Test
    void twoCycleTerminatesAndAbsorbsBackEdge() {
        // 600 ↔ 601 양방향. 600 의 선수 탐색 시 600 자체가 back-edge 로 depth 2
        // 에 재등장하지만 MIN(depth) 로 anchor 의 depth 0 만 살아남음.
        List<ConceptDepth> result = repository.findPrerequisitesWithDepth(600, 3);

        assertThat(result).containsExactlyInAnyOrder(
            new ConceptDepth(600, 0),
            new ConceptDepth(601, 1)
        );
    }
}
