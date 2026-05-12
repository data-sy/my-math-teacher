package com.mmt.api.performance;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import org.junit.jupiter.api.Test;
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
 * M2 사후 측정: 복합 인덱스 효과 분리.
 *
 * <p>{@link CteGraphQueryPerformanceTest} 와 동일한 측정 방식이되 스키마에서
 * {@code idx_knowledge_space_composite} 를 제거한 상태(가장 raw 한 인덱스 부재
 * 상태). 본 클래스의 측정값과 {@code CteGraphQueryPerformanceTest} 의 측정값을
 * 비교하면 인덱스 효과만 분리해서 볼 수 있다.
 *
 * <p>3-way 비교에 사용:
 * <ul>
 *   <li>Neo4j (M1 baseline) — Neo4j native indexing</li>
 *   <li>CTE no-index — knowledge_space PRIMARY 만</li>
 *   <li>CTE with-index — + 복합 인덱스 (실제 운영 상태)</li>
 * </ul>
 *
 * <p>본 클래스는 단정 없이 측정 로그만 남긴다 — 인덱스 부재 상태는 운영에서
 * 사용하지 않으므로 회귀 가드 불필요. 측정값은 docs/reports/m2-cte-migration.md
 * 의 비교 표로 옮긴다.
 */
@JdbcTest
@Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = {
    "classpath:regression_snapshot_schema_no_index.sql",
    "file:../api/sql/insert_chapters.sql",
    "file:../api/sql/insert_concepts_escape.sql",
    "file:../api/sql/insert_knowledge_space.sql"
})
class CteGraphQueryNoIndexPerformanceTest {

    private static final int CONCEPT_ID = 6646;
    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 100;

    @Autowired
    JdbcTemplateConceptRepository repository;

    @Test
    void benchmarkDepth2NoIndex() {
        double p95 = runBenchmark("cte-no-index-depth2", CONCEPT_ID, 2);
        assertThat(p95).isPositive();
    }

    @Test
    void benchmarkDepth3NoIndex() {
        double p95 = runBenchmark("cte-no-index-depth3", CONCEPT_ID, 3);
        assertThat(p95).isPositive();
    }

    @Test
    void benchmarkDepth5NoIndex() {
        double p95 = runBenchmark("cte-no-index-depth5", CONCEPT_ID, 5);
        assertThat(p95).isPositive();
    }

    private double runBenchmark(String label, int conceptId, int depth) {
        IntConsumer op = id -> repository.findPrerequisitesWithDepth(id, depth);
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
