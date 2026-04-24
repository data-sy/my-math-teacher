package com.mmt.api.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

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

    // Task 2.2 회귀 감지 기준선 (ms) — 2026-04-24 측정 실측 avg 기반.
    // 허용 배수 1.5 는 단일 JVM 내 GC/warmup 변동성 흡수 목적.
    // 기준선 갱신 시 docs/benchmark/milestone-1-baseline.md 와 동기화.
    private static final long BASELINE_DEPTH3_AVG_MS = 6L;   // 실측 5.392ms
    private static final double ALLOWED_REGRESSION = 1.5;

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

    // Task 2.2: M2 마이그레이션 후 결과 동치성 비교용 JSON 스냅샷.
    // shared/benchmark/ 에 날짜 기반 파일명으로 저장. 해시 기준으로 MySQL CTE 결과 대조 가능.
    @Test
    void exportNeo4jResultsSnapshot() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Integer conceptId : topConceptIds) {
            for (int depth : List.of(2, 3, 5)) {
                List<Integer> results = callByDepth(conceptId, depth);
                Collections.sort(results);

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("count", results.size());
                entry.put("concept_ids", results);
                entry.put("sha256", sha256(results.toString()));

                String key = "conceptId=" + conceptId + ",depth=" + depth;
                snapshot.put(key, entry);
            }
        }
        snapshot.put("generated_at", Instant.now().toString());
        snapshot.put("git_commit", System.getenv().getOrDefault("GIT_COMMIT", "unknown"));
        snapshot.put("representative_id", representativeId);

        Path outDir = Paths.get("../shared/benchmark").toAbsolutePath().normalize();
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(
            "neo4j-snapshot-" + LocalDate.now().toString().replace("-", "") + ".json");
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(snapshot);
        Files.writeString(outFile, json);
        System.out.printf("[Snapshot] wrote %s (%d bytes, %d entries)%n",
            outFile, json.length(), topConceptIds.size() * 3);
    }

    private List<Integer> callByDepth(int conceptId, int depth) {
        return switch (depth) {
            case 2 -> conceptRepository.findNodesIdByConceptIdDepth2(conceptId).collectList().block();
            case 3 -> conceptRepository.findNodesIdByConceptIdDepth3(conceptId).collectList().block();
            case 5 -> conceptRepository.findNodesIdByConceptIdDepth5(conceptId).collectList().block();
            default -> throw new IllegalArgumentException("unsupported depth=" + depth);
        };
    }

    private static String sha256(String input) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Task 2.2: 회귀 감지 테스트 — warmup 20 + 측정 30 회의 p50 (median) 이 ceiling 미만이어야 함.
    // median 을 쓰는 이유: isolated 실행 시 cold JVM 의 첫 몇 회가 크게 튀어 avg 가 오염됨.
    // warmup 20 은 Reactor + Neo4j driver 파이프라인 JIT 컴파일이 수렴할 때까지 필요.
    @Test
    void shouldNotRegressDepth3GraphTraversal() {
        for (int i = 0; i < 20; i++) {
            conceptRepository.findNodesIdByConceptIdDepth3(representativeId).collectList().block();
        }
        long[] nanos = new long[30];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            conceptRepository.findNodesIdByConceptIdDepth3(representativeId).collectList().block();
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        long medianMs = nanos[nanos.length / 2] / 1_000_000;
        long ceilingMs = (long) (BASELINE_DEPTH3_AVG_MS * ALLOWED_REGRESSION);
        System.out.printf("[Regression] depth3 median=%dms baseline=%dms ceiling=%dms%n",
            medianMs, BASELINE_DEPTH3_AVG_MS, ceilingMs);
        assertThat(medianMs).isLessThan(ceilingMs);
    }
}
