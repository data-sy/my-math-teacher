package com.mmt.api.performance;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.Concept;
import com.mmt.api.repository.concept.ConceptRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * M1 Spec 02 Task 2.1: Neo4j 그래프 쿼리 성능 기준선 측정.
 *
 * 설계 결정:
 *  - @DataNeo4jTest 슬라이스 사용 → 전체 Spring 컨텍스트(securelocal, OAuth2) 우회.
 *  - ConceptService 가 아닌 ConceptRepository 를 직접 호출: 서비스 메서드는
 *    @Transactional(readOnly = true) 로 reactiveTransactionManager 를 요구하지만
 *    @DataNeo4jTest 가 해당 bean 을 자동 등록하지 않아 실패 (Spec 03 에서 보강 예정).
 *    서비스는 리포지토리의 얇은 래퍼이므로 기준선 측정에는 영향 없음.
 *  - findById 는 동일 이유로 repo 메서드가 @Transactional 인터셉터를 거쳐 실패.
 *    동치 Cypher (`MATCH (c:concept {concept_id: $id}) RETURN c`) 를 driver 로 직접 실행해
 *    동일한 서버-사이드 비용을 측정한다.
 *  - 시드 데이터는 TestcontainersConfig 가 마운트한 CSV 를 LOAD CSV 로 읽어
 *    프로덕션과 동일 크기 (concept 1635 + relation 3446) 로 주입.
 *  - @TestInstance(PER_CLASS) + @BeforeAll 로 시드를 클래스당 1회만 실행.
 *
 * 대상 concept_id 선정:
 *  - out_degree 상위 5 개를 동적으로 선정하여 로그 (Task 2.2 문서 반영용).
 *  - 벤치마크 본체는 top-1 ID 를 대표값으로 사용 (5 × 5 × 100 회 실행은 CI 부담).
 *    필요 시 Task 2.2 에서 수동으로 다른 ID 에 대해 재측정 가능.
 */
@DataNeo4jTest
@Import(TestcontainersConfig.class)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphQueryPerformanceTest {

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 100;  // p99 산출을 위해 100 회 필수

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private Driver driver;

    private final List<Integer> topConceptIds = new ArrayList<>();
    private int representativeId;

    @BeforeAll
    void seedRealGraphAndSelectTargetIds() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");

            // neo4j/init/init.cypher 와 동일한 적재 로직 (LOAD CSV WITH HEADERS).
            session.run(
                "LOAD CSV WITH HEADERS FROM 'file:///concepts.csv' AS row "
                    + "CREATE (:concept {"
                    + "  name: row.name, concept_id: toInteger(row.id), "
                    + "  desc: row.description, section: row.section, "
                    + "  school_level: row.school_level, grade_level: row.grade_level, "
                    + "  semester: row.semester, chapter_id: toInteger(row.chapter_id), "
                    + "  chapter_main: row.chapter_main, chapter_sub: row.chapter_sub, "
                    + "  chapter_name: row.chapter_name, "
                    + "  achievement_id: toInteger(row.achievement_id), "
                    + "  achievement_name: row.achievement_name, "
                    + "  skill_id: toInteger(row.skill_id)"
                    + "})"
            );
            session.run(
                "LOAD CSV WITH HEADERS FROM 'file:///knowledge_space.csv' AS row "
                    + "MATCH (a:concept {concept_id: toInteger(row.to_concept_id)}), "
                    + "      (b:concept {concept_id: toInteger(row.from_concept_id)}) "
                    + "CREATE (a)-[:KNOWLEDGE_SPACE {knowledge_space_id: toInteger(row.id)}]->(b)"
            );

            long nodeCount = session.run("MATCH (n:concept) RETURN count(n) AS c")
                .single().get("c").asLong();
            long relCount = session.run("MATCH ()-[r:KNOWLEDGE_SPACE]->() RETURN count(r) AS c")
                .single().get("c").asLong();
            System.out.printf("[Benchmark seed] concept nodes=%d, knowledge_space rels=%d%n",
                nodeCount, relCount);

            // out_degree 상위 5 개 concept_id 동적 선정 (spec Task 2.1 (1)).
            var result = session.run(
                "MATCH (c:concept)-[r]->() "
                    + "WITH c, count(r) AS out_degree "
                    + "ORDER BY out_degree DESC LIMIT 5 "
                    + "RETURN c.concept_id AS concept_id, c.name AS name, out_degree"
            );
            for (Record rec : result.list()) {
                int id = rec.get("concept_id").asInt();
                String name = rec.get("name").asString();
                long deg = rec.get("out_degree").asLong();
                topConceptIds.add(id);
                System.out.printf(
                    "[Benchmark target] concept_id=%d out_degree=%d name=\"%s\"%n",
                    id, deg, name);
            }
            representativeId = topConceptIds.get(0);
            System.out.printf("[Benchmark target] representative_id=%d%n", representativeId);
        }
    }

    // (1) ID 로 단일 Concept 조회 — findById 의 Cypher 동치.
    //     repo.findById 는 reactiveTransactionManager 부재로 실행 불가 (클래스 주석 참조).
    @Test
    void benchmarkFindById() {
        runBenchmark("findById", representativeId, id -> {
            try (var s = driver.session()) {
                s.run("MATCH (c:concept {concept_id: $id}) RETURN c",
                    org.neo4j.driver.Values.parameters("id", id)).list();
            }
        });
    }

    // (2) 깊이 2 그래프 탐색.
    @Test
    void benchmarkDepth2GraphTraversal() {
        runBenchmark("depth2", representativeId,
            id -> conceptRepository.findNodesIdByConceptIdDepth2(id).collectList().block());
    }

    // (3) 깊이 3 그래프 탐색.
    @Test
    void benchmarkDepth3GraphTraversal() {
        runBenchmark("depth3", representativeId,
            id -> conceptRepository.findNodesIdByConceptIdDepth3(id).collectList().block());
    }

    // (4) 깊이 5 그래프 탐색.
    @Test
    void benchmarkDepth5GraphTraversal() {
        runBenchmark("depth5", representativeId,
            id -> conceptRepository.findNodesIdByConceptIdDepth5(id).collectList().block());
    }

    // (5) 선수 개념 조회 (들어오는 엣지 출발점).
    @Test
    void benchmarkFindToConcepts() {
        runBenchmark("findToConcepts", representativeId,
            id -> {
                List<Concept> list = conceptRepository
                    .findToConceptsByConceptId(id).collectList().block();
                // 결과셋을 소비해 lazy 평가를 막음
                if (list != null) list.size();
            });
    }

    private void runBenchmark(String label, int conceptId, IntConsumer op) {
        for (int i = 0; i < WARMUP_RUNS; i++) op.accept(conceptId);
        long[] nanos = new long[MEASURED_RUNS];
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            op.accept(conceptId);
            nanos[i] = System.nanoTime() - start;
        }
        BenchmarkStats.report(label + " (conceptId=" + conceptId + ")", nanos);
    }
}
