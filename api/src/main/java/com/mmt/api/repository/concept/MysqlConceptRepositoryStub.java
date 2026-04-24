package com.mmt.api.repository.concept;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * M1 Spec 03 Task 3.2: {@link MysqlConceptRepository} 임시 스텁.
 *
 * {@code mmt.migration.use-mysql-cte-for-graph=true} 일 때만 등록되어,
 * 피처 플래그 토글 구조 자체를 검증 가능하게 한다. 실제 MySQL CTE 구현은
 * Milestone 2 에서 대체한다.
 *
 * 설계 의도:
 *  - 플래그 false (기본) → 이 스텁조차 로드되지 않음 → 기존 Neo4j 경로 완전 보존
 *  - 플래그 true → 스텁이 로드되어 UnsupportedOperationException 발생
 *    → "분기는 작동하지만 구현은 아직" 이라는 상태를 명시적으로 표현
 */
@Component
@ConditionalOnProperty(
    prefix = "mmt.migration",
    name = "use-mysql-cte-for-graph",
    havingValue = "true")
public class MysqlConceptRepositoryStub implements MysqlConceptRepository {

    @Override
    public List<Integer> findPrerequisiteConceptIds(int conceptId, int maxDepth) {
        throw new UnsupportedOperationException(
            "MySQL CTE 구현은 Milestone 2 에서 제공됩니다 "
                + "(conceptId=" + conceptId + ", maxDepth=" + maxDepth + ")");
    }
}
