package com.mmt.api.service;

import com.mmt.api.config.TestcontainersConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M1 Spec 03 Task 3.2: 피처 플래그 `mmt.migration.use-mysql-cte-for-graph` 토글에 따른
 * {@link ConceptService#findNodesIdByConceptIdDepth3(int)} 경로 전환 검증.
 *
 * 내부 @Nested 클래스 각각 별도 Spring 컨텍스트 (다른 @TestPropertySource) 로 기동된다.
 * Neo4j Testcontainer 에 시드 데이터는 없으므로 false 경로는 빈 리스트를 반환하지만,
 * 분기 논리 자체는 empty list != null 로 검증 가능.
 *
 * Mysql 경로에서는 {@link com.mmt.api.repository.concept.MysqlConceptRepositoryStub} 이
 * UnsupportedOperationException 을 던진다. Flux.fromIterable 은 인자를 eager 평가 →
 * 서비스 호출 시점에 예외가 즉시 전파됨.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
class ConceptServiceFeatureFlagTest {

    @Nested
    @TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=false")
    class WhenFlagFalse {

        @Autowired
        private ConceptService service;

        @Test
        void usesNeo4jPath() {
            List<Integer> result = service.findNodesIdByConceptIdDepth3(4979)
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
        void throwsFromStub() {
            assertThatThrownBy(() -> service.findNodesIdByConceptIdDepth3(4979))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Milestone 2");
        }
    }
}
