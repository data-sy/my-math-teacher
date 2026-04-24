package com.mmt.api.service;

/**
 * ConceptService 통합 테스트는 M1 Task 1.3 범위에서 의도적으로 defer.
 *
 * 사유:
 *  1) reactive 메서드 (findToConcepts, findNodesByConceptId, findNodesIdByConceptIdDepth*) 는
 *     @Transactional(readOnly = true) 가 걸려 있어 `reactiveTransactionManager` bean 을 요구함.
 *     이 bean 은 @DataNeo4jTest / @SpringBootTest 기본 구성에서 자동 등록되지 않아
 *     Task 1.2 findById 테스트와 동일한 이슈가 발생함. 해결은 Spec 03 에서 reactive TM
 *     bean 을 TestcontainersConfig 에 추가하는 작업과 함께 처리하는 것이 응집도 높음.
 *
 *  2) reactive 메서드들은 ConceptRepository 의 얇은 래퍼이며, 핵심 동작은 이미
 *     Task 1.2 ConceptRepositoryTest 에서 검증됨.
 *
 *  3) JDBC 메서드 (findOne, findSkillIdByConceptId, findConceptNameByChapterId) 는
 *     M2 Neo4j → MySQL CTE 마이그레이션 대상이 아니므로 M1 baseline 목적상
 *     회귀 기준선이 불필요함.
 *
 *  4) ConceptService 전체 통합 테스트는 MySQL 스키마 (concepts, chapters) + 시드 SQL
 *     + OAuth2/Security 컨텍스트 격리가 선행되어야 하며, 이는 Spec 03 의
 *     application-test.yml 확장·피처 플래그 작업과 함께 도입하는 것이 자연스러움.
 *
 * 재개 시점: Spec 03 완료 후, 또는 M2 마이그레이션 이후 동치성 검증 단계.
 */
class ConceptServiceTest {
    // 의도적으로 테스트 메서드 없음. 사유는 클래스 주석 참조.
}
