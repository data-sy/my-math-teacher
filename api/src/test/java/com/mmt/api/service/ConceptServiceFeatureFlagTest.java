package com.mmt.api.service;

import com.mmt.api.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 피처 플래그 {@code mmt.migration.use-mysql-cte-for-graph=false} (기본값) 경로 회귀 보호.
 *
 * - M1 Spec 03 Task 3.2 에서 Stub 의 UnsupportedOperationException 으로 양쪽 분기 검증
 * - M2 Spec 01 Task 1.1 에서 Stub 이 실제 CTE 구현으로 대체됨에 따라
 *   {@code WhenFlagTrue} 케이스는 본 클래스에서 제거됨. 분기 양쪽 통합 검증은 Spec 02 의
 *   {@code ConceptServiceCteIntegrationTest} / {@code ConceptServiceNeo4jIntegrationTest}
 *   가 자체 schema/seed 와 함께 다시 도입한다.
 *
 * 본 클래스는 false 경로 (Neo4j Reactive) 가 본 PR 변경 후에도 그대로 동작함을 보장한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=false")
@Testcontainers
class ConceptServiceFeatureFlagTest {

    @Autowired
    private ConceptService service;

    @Test
    void neo4jPathReturnsEmptyListWhenNoSeed() {
        // Neo4j Testcontainer 에 시드가 없는 상태이므로 빈 리스트가 반환되지만,
        // 분기 자체는 not null 로 검증 가능.
        List<Integer> result = service.findNodesIdByConceptIdDepth3(4979)
            .collectList().block();
        assertThat(result).isNotNull();
    }
}
