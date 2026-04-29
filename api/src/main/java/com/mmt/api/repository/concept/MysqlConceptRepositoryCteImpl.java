package com.mmt.api.repository.concept;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * M2 Spec 01 Task 1.1: {@link MysqlConceptRepository} 의 실제 CTE 구현.
 *
 * 본 클래스는 M1 의 임시 Stub ({@code MysqlConceptRepositoryStub}) 을 대체한다.
 * 피처 플래그 {@code mmt.migration.use-mysql-cte-for-graph=true} 일 때만 등록되며,
 * Neo4j 경로와 분기로 공존한다 (Spec 02 에서 ConceptService 분기 확산).
 *
 * 방향성: ADR 0003 에 따라 backward 단일안. 매 재귀 단계는 현재 노드를
 * {@code ks.to_concept_id} 위치로 잡고 그 엣지의 {@code ks.from_concept_id} 를 다음 노드로 가져온다.
 * 즉 시작 노드 (학생이 틀린 지식) 에서 한 단계씩 선수 쪽으로 거슬러 올라간다.
 *
 * 다중 경로 (같은 conceptId 가 여러 깊이로 도달 가능) 는 외부 {@code SELECT DISTINCT} 로 평탄화.
 * 무한 루프 안전망은 application.yml 의 hikari connection-init-sql 로 적용된
 * {@code cte_max_recursion_depth = 10} 이 담당한다.
 */
@Repository
@ConditionalOnProperty(
    prefix = "mmt.migration",
    name = "use-mysql-cte-for-graph",
    havingValue = "true")
public class MysqlConceptRepositoryCteImpl implements MysqlConceptRepository {

    private static final String SQL_FIND_PREREQUISITE_IDS = """
        WITH RECURSIVE prerequisite_path AS (
            SELECT concept_id, 0 AS depth
            FROM concepts WHERE concept_id = ?

            UNION ALL

            SELECT c.concept_id, pp.depth + 1
            FROM prerequisite_path pp
            JOIN knowledge_space ks ON pp.concept_id = ks.to_concept_id
            JOIN concepts         c ON ks.from_concept_id = c.concept_id
            WHERE pp.depth < ?
        )
        SELECT DISTINCT concept_id FROM prerequisite_path
        """;

    private final JdbcTemplate jdbcTemplate;

    public MysqlConceptRepositoryCteImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException(
                "maxDepth must be >= 0 (got " + maxDepth + ")");
        }
        return jdbcTemplate.queryForList(
            SQL_FIND_PREREQUISITE_IDS, Integer.class, conceptId, maxDepth);
    }
}
