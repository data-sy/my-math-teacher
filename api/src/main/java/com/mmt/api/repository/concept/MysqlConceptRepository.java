package com.mmt.api.repository.concept;

import java.util.List;

/**
 * Neo4j 그래프 탐색을 MySQL 재귀 CTE 로 대체하기 위한 인터페이스.
 *
 * - M1 Spec 03 Task 3.2: 인터페이스 + 임시 Stub 도입 (분기 토글 검증용)
 * - M2 Spec 01 Task 1.1: {@link MysqlConceptRepositoryCteImpl} 이 실제 구현 제공
 *
 * 구현체는 {@code mmt.migration.use-mysql-cte-for-graph=true} 일 때만 bean 으로 등록되어
 * Neo4j 경로와 분기로 공존한다.
 *
 * 객체 반환 메서드 (예: {@code findPrerequisiteConcepts}) 는 Spec 02 에서
 * ADR 0006 의 {@code concepts JOIN chapters} 패턴으로 추가 예정.
 */
public interface MysqlConceptRepository {

    /**
     * 시작 노드 (학생이 틀린 지식) 의 직접 + 재귀 선수 개념을 ADR 0003 의 backward 방향으로 탐색.
     * 자기 자신을 포함한 도달 가능한 모든 concept_id 를 반환 (DISTINCT 평탄화).
     *
     * @param conceptId 시작 노드의 concept_id
     * @param maxDepth  최대 깊이 (0 → 자기 자신만, N → N 단계 이내 모든 선수)
     * @return concept_id 목록 (시작 노드 포함, 중복 제거됨)
     */
    List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth);
}
