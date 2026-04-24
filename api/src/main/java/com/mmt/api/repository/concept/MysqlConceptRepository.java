package com.mmt.api.repository.concept;

import java.util.List;

/**
 * M1 Spec 03 Task 3.2: M2 에서 Neo4j 그래프 탐색을 MySQL 재귀 CTE 로 대체하기 위한 인터페이스.
 *
 * 이 인터페이스는 M1 단계에서는 {@link MysqlConceptRepositoryStub} 만 제공되며
 * {@code mmt.migration.use-mysql-cte-for-graph=true} 일 때만 bean 으로 등록된다.
 * 실제 CTE 구현은 Milestone 2 에서 대체한다.
 */
public interface MysqlConceptRepository {

    /**
     * 주어진 conceptId 로부터 {@code maxDepth} 까지 도달 가능한 모든 concept_id 를 반환.
     * M2 에서 MySQL 재귀 CTE (WITH RECURSIVE) 로 구현된다.
     *
     * @param conceptId 시작 노드의 concept_id
     * @param maxDepth  그래프 탐색 최대 깊이
     * @return 도달 가능한 concept_id 목록 (자기 자신 포함 여부는 M2 에서 의미론 확정)
     */
    List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth);
}
