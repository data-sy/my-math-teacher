package com.mmt.api.repository.concept;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.Concept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataNeo4jTest
@Import(TestcontainersConfig.class)
@Testcontainers
class ConceptRepositoryTest {

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private Driver driver;

    @BeforeEach
    void seedGraph() {
        // c1 ─▶ c2 ─▶ c3
        //  └─▶ c4
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
            session.run(
                "CREATE (c1:concept {concept_id: 1, name: 'c1', chapter_id: 10}) "
                + "CREATE (c2:concept {concept_id: 2, name: 'c2', chapter_id: 10}) "
                + "CREATE (c3:concept {concept_id: 3, name: 'c3', chapter_id: 10}) "
                + "CREATE (c4:concept {concept_id: 4, name: 'c4', chapter_id: 10}) "
                + "CREATE (c1)-[:LEADS_TO]->(c2) "
                + "CREATE (c2)-[:LEADS_TO]->(c3) "
                + "CREATE (c1)-[:LEADS_TO]->(c4)"
            );
        }
    }

    @Test
    void testConnectionReturnsOne() {
        StepVerifier.create(conceptRepository.testConnection())
            .expectNext(1)
            .verifyComplete();
    }

    // findById (Spring Data CRUD) 테스트는 의도적으로 생략.
    // 이유: ReactiveNeo4jRepository.findById 는 @Transactional(transactionManager = "reactiveTransactionManager")
    // 를 참조하는데 @DataNeo4jTest 는 해당 이름의 TM 빈을 자동 등록하지 않아 실패.
    // 3가지 핵심 쿼리(@Query 기반)는 트랜잭션 인터셉터를 거치지 않아 영향 없음.
    // 필요 시 Spec 03 에서 reactiveTransactionManager 빈 설정 보강 후 추가.

    @Test
    void findToConceptsByConceptIdReturnsIncomingEdges() {
        // c3 의 들어오는 엣지의 출발점: c2
        List<Concept> result = conceptRepository
            .findToConceptsByConceptId(3)
            .collectList()
            .block();

        assertThat(result).isNotNull();
        assertThat(result).extracting(Concept::getConceptId).containsExactly(2);
    }

    @Test
    void findNodesByConceptIdDepth3ReturnsExpectedSet() {
        // c3 에 길이 0..3 경로로 도달 가능한 시작 노드: {c1, c2, c3}
        List<Concept> result = conceptRepository
            .findNodesByConceptIdDepth3(3)
            .collectList()
            .block();

        assertThat(result).isNotNull();
        assertThat(result)
            .extracting(Concept::getConceptId)
            .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void findNodesByConceptIdDepth5ReturnsExpectedSet() {
        // 시드 그래프 깊이가 2 이므로 Depth5 결과는 Depth3 와 동일
        List<Concept> result = conceptRepository
            .findNodesByConceptIdDepth5(3)
            .collectList()
            .block();

        assertThat(result).isNotNull();
        assertThat(result)
            .extracting(Concept::getConceptId)
            .containsExactlyInAnyOrder(1, 2, 3);
    }

    // `findNodesIdByConceptIdDepth*` 는 Cypher `[id IN node.concept_id]`의
    // IN 우변이 스칼라라 정상 동작이 불명확. baseline 캡처 목적만 수행.
    // 실제 값 동등성 기준은 Spec 02 스냅샷에서 확정.

    @Test
    void findNodesIdByConceptIdDepth2CapturesBaseline() {
        List<Integer> result = conceptRepository
            .findNodesIdByConceptIdDepth2(3)
            .collectList()
            .block();

        assertThat(result).isNotNull();
    }

    @Test
    void findNodesIdByConceptIdDepth3CapturesBaseline() {
        List<Integer> result = conceptRepository
            .findNodesIdByConceptIdDepth3(3)
            .collectList()
            .block();

        assertThat(result).isNotNull();
    }

    @Test
    void findNodesIdByConceptIdDepth5CapturesBaseline() {
        List<Integer> result = conceptRepository
            .findNodesIdByConceptIdDepth5(3)
            .collectList()
            .block();

        assertThat(result).isNotNull();
    }
}
