package com.mmt.api.performance;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 spec-03 Task 4.2: MySQL 재귀 CTE 그래프 쿼리 성능 체크포인트.
 *
 * <p>{@link JdbcTemplateConceptRepository#findPrerequisitesWithDepth} 를 직접
 * 측정한다 — ConceptService 의 Redis 캐시를 거치지 않으므로 매 호출이 cache miss
 * 와 동일한 비용. spec-03 L80 표의 "CTE 깊이 N (캐시 미스)" 측정 항목에 대응.
 *
 * <p>conceptId 는 M1 baseline 과 동일한 6646 (스냅샷 representative_id) 으로
 * 고정해 깊이별 비교의 의미를 유지한다 (spec-03 L83).
 *
 * <p>측정 방식은 M1 {@link GraphQueryPerformanceTest} 패턴을 그대로 따른다:
 * WARMUP_RUNS 후 MEASURED_RUNS 회 nanoTime 측정 → 정렬해 p95 산출.
 *
 * <p>허용 기준 (spec-03 L78 표):
 * <ul>
 *   <li>depth 3 p95 &lt; 30ms</li>
 *   <li>depth 5 p95 &lt; 100ms</li>
 * </ul>
 * depth 2 는 기준 비명시 — 측정 로그만 남기고 단정 생략 (회귀 추적용).
 */
@JdbcTest
@Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {
    "classpath:regression_snapshot_schema.sql",
    "file:../api/sql/insert_chapters.sql",
    "file:../api/sql/insert_concepts_escape.sql",
    "file:../api/sql/insert_knowledge_space.sql"
})
class CteGraphQueryPerformanceTest {

    private static final int CONCEPT_ID = 6646;
    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 100;

    private static final double DEPTH3_P95_LIMIT_MS = 30.0;
    private static final double DEPTH5_P95_LIMIT_MS = 100.0;

    @Autowired
    JdbcTemplateConceptRepository repository;

    @Test
    void benchmarkDepth2() {
        double p95 = runBenchmark("cte-depth2", CONCEPT_ID, 2);
        // 기준 비명시 — 로그만 남기고 회귀 추적.
        assertThat(p95).isPositive();
    }

    @Test
    void benchmarkDepth3() {
        double p95 = runBenchmark("cte-depth3", CONCEPT_ID, 3);
        assertThat(p95)
            .as("CTE depth 3 p95 < %.1fms (spec-03 Task 4.2)", DEPTH3_P95_LIMIT_MS)
            .isLessThan(DEPTH3_P95_LIMIT_MS);
    }

    @Test
    void benchmarkDepth5() {
        double p95 = runBenchmark("cte-depth5", CONCEPT_ID, 5);
        assertThat(p95)
            .as("CTE depth 5 p95 < %.1fms (spec-03 Task 4.2)", DEPTH5_P95_LIMIT_MS)
            .isLessThan(DEPTH5_P95_LIMIT_MS);
    }

    /**
     * 객체 반환 CTE 성능도 함께 측정 (ADR 0005: 시각화 경로 응답 시간 분리 권장).
     * 허용 기준은 spec 미명시 — 측정 로그만 남긴다.
     */
    @ParameterizedTest(name = "cte-objects-depth{0}")
    @ValueSource(ints = {2, 3, 5})
    void benchmarkObjectReturningCte(int depth) {
        IntConsumer op = id -> repository.findPrerequisiteConcepts(id, depth);
        double p95 = runBenchmarkWith("cte-objects-depth" + depth, CONCEPT_ID, op);
        assertThat(p95).isPositive();
    }

    private double runBenchmark(String label, int conceptId, int depth) {
        IntConsumer op = id -> repository.findPrerequisitesWithDepth(id, depth);
        return runBenchmarkWith(label, conceptId, op);
    }

    private double runBenchmarkWith(String label, int conceptId, IntConsumer op) {
        for (int i = 0; i < WARMUP_RUNS; i++) op.accept(conceptId);
        long[] nanos = new long[MEASURED_RUNS];
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            op.accept(conceptId);
            nanos[i] = System.nanoTime() - start;
        }
        BenchmarkStats.report(label + " (conceptId=" + conceptId + ")", nanos);
        return p95Ms(nanos);
    }

    private static double p95Ms(long[] nanos) {
        long[] copy = nanos.clone();
        Arrays.sort(copy);
        return copy[(int) Math.ceil(copy.length * 0.95) - 1] / 1_000_000.0;
    }
}
