package com.mmt.api.service;

import com.mmt.api.config.TestcontainersConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 피처 플래그 {@code mmt.migration.use-mysql-cte-for-graph} 토글에 따른
 * {@link ConceptService#findNodesIdByConceptIdDepth3(int)} 경로 전환 검증.
 *
 * 내부 @Nested 클래스 각각 별도 Spring 컨텍스트 (다른 @TestPropertySource) 로 기동.
 *
 * <ul>
 *   <li>false 경로: Neo4j Reactive 리포지토리. Testcontainer 에 시드 미주입이므로
 *       빈 결과. 라우팅 분기의 작동만 검증.</li>
 *   <li>true 경로: MySQL 재귀 CTE
 *       ({@link com.mmt.api.repository.concept.JdbcTemplateConceptRepository#findPrerequisitesWithDepth}).
 *       @Sql 로 cte_test_schema/seed.sql 을 적용해 시드 300 체인의 4개 노드 반환을 단정.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {"classpath:cte_test_schema.sql", "classpath:cte_test_seed.sql"})
class ConceptServiceFeatureFlagTest {

    @Nested
    @TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=false")
    class WhenFlagFalse {

        @Autowired
        private ConceptService service;

        @Test
        void usesNeo4jPath() {
            List<Integer> result = service.findNodesIdByConceptIdDepth3(300)
                .collectList().block();
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=true")
    class WhenFlagTrue {

        @Autowired
        private ConceptService service;

        @Test
        void usesMysqlCtePath() {
            // 시드 체인 300 → 310 → 320 → 330. depth 3 호출은 자기 자신 포함
            // 4개 노드 ID 만 반환 (ConceptDepth → Integer 매핑).
            List<Integer> result = service.findNodesIdByConceptIdDepth3(300)
                .collectList().block();
            assertThat(result).containsExactlyInAnyOrder(300, 310, 320, 330);
        }
    }
}
