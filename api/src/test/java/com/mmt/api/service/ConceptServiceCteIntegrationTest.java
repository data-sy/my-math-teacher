package com.mmt.api.service;

import com.mmt.api.config.TestcontainersConfig;
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
 * spec-02 Task 3.1 분기 동등성 (CTE 경로 = mmt.migration.use-mysql-cte-for-graph=true).
 *
 * 목적: MysqlConceptRepositoryCteImpl 이 실제 SpringBootTest 컨텍스트에서 등록되고
 * ConceptService 의 CTE 분기 + 캐싱 헬퍼가 정상 라우팅되는지 검증.
 *
 * 시드 SQL (cte_test_*.sql) 은 spec-01 의 단위 테스트와 동일 — 동일 그래프
 * (6→5→4→3→{1,2}→10) 위에서 ConceptService 와 MysqlConceptRepositoryCteImpl 의
 * 결과가 일치함을 확인 (서비스 계층 wrapping 회귀 보호).
 *
 * Neo4j fallback 경로는 ConceptServiceFeatureFlagTest 에서 다룬다 (false 분기).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=true")
@Testcontainers
@Sql(scripts = {"/sql/cte_test_schema.sql", "/sql/cte_test_seed.sql"})
class ConceptServiceCteIntegrationTest {

    @Autowired
    private ConceptService service;

    @Test
    void cteBranchReturnsExpectedDepth3Ids() {
        List<Integer> result = service.findNodesIdByConceptIdDepth3(10)
            .collectList().block();
        assertThat(result).containsExactlyInAnyOrder(10, 1, 2, 3, 4);
    }

    @Test
    void cteBranchReturnsExpectedDepth5Ids() {
        List<Integer> result = service.findNodesIdByConceptIdDepth5(10)
            .collectList().block();
        assertThat(result).containsExactlyInAnyOrder(10, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void cteBranchReturnsExpectedDepth2Ids() {
        // 다중 경로 (3 → 1, 3 → 2) 가 외부 SELECT DISTINCT 로 평탄화되어 4 개만 반환
        List<Integer> result = service.findNodesIdByConceptIdDepth2(10)
            .collectList().block();
        assertThat(result).containsExactlyInAnyOrder(10, 1, 2, 3);
    }

    @Test
    void cteBranchFindToConceptsExcludesStartNode() {
        // ADR 0006: findPrerequisiteConcepts(?, 1) - {start} → Cypher (n)-[r]->(m{?}) 와 동치
        var result = service.findToConcepts(10).collectList().block();
        assertThat(result).hasSize(2);
        assertThat(result).extracting("conceptId").containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void cteBranchFindNodesByConceptIdReturnsAllPrerequisitesIncludingSelf() {
        // depth 결정: 학교급 = "초등" (시드의 chapter.school_level) → depth 3
        var result = service.findNodesByConceptId(10).collectList().block();
        assertThat(result).extracting("conceptId").containsExactlyInAnyOrder(10, 1, 2, 3, 4);
        // chapters JOIN 동작 확인 — 한 객체 검사
        assertThat(result).anySatisfy(r -> {
            assertThat(r.getConceptSchoolLevel()).isEqualTo("초등");
            assertThat(r.getConceptGradeLevel()).isEqualTo("초1");
            assertThat(r.getConceptChapterName()).isEqualTo("cte-test-chapter");
        });
    }
}
