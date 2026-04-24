package com.mmt.api.performance;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.Probability;
import com.mmt.api.domain.Result;
import com.mmt.api.repository.probability.JdbcTemplateProbabilityRepository;
import com.mmt.api.repository.probability.ProbabilityRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M1 Spec 02 Task 2.1: MySQL 리포지토리 성능 기준선 측정.
 *
 * 설계 결정:
 *  - @DataJpaTest + @AutoConfigureTestDatabase(replace=NONE) 로 MySQL Testcontainer 를 사용.
 *    @DataJpaTest 슬라이스는 JdbcTemplate 도 autoconfigure 하므로
 *    JdbcTemplateProbabilityRepository 를 @Import 로 직접 추가해 bean 등록.
 *  - concepts/chapters/items/tests/tests_items/users_tests/answers/probabilities 는
 *    JPA 엔티티가 없어 ddl-auto=create-drop 로 자동 생성되지 않음 → @BeforeAll 에서
 *    raw DDL 로 테이블 생성 후 시드 주입. (프로덕션 스키마 create.sql 의 서브셋)
 *  - 시드 규모: 단일 user_test_id 에 대해 200 행 probabilities 를 배치해
 *    findResults 의 5-way JOIN 이 의미 있는 결과셋을 반환하도록 함 (depth < 3 필터 통과).
 *  - 배치 insert 벤치마크는 별도 answer_id 를 사용하여 findResults 데이터와 격리.
 *  - @Commit 으로 @DataJpaTest 의 기본 롤백을 막아 @BeforeAll 시드가 테스트 메서드에서 읽히게 함.
 *
 * 주의:
 *  - 배치 insert 100 회 × 100 행 = 10,000 행이 DB 에 남지만 create-drop 로 JVM 종료 시 정리됨.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfig.class, JdbcTemplateProbabilityRepository.class})
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Commit
class RepositoryBenchmarkTest {

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 100;
    private static final int FIND_RESULTS_SEED_ROWS = 200;
    private static final int BATCH_INSERT_SIZE = 100;
    private static final long FIND_RESULTS_USER_TEST_ID = 1L;
    private static final long FIND_RESULTS_ANSWER_ID = 1L;
    private static final long BATCH_INSERT_ANSWER_ID = 2L;

    // Task 2.2 회귀 감지 기준선 (ms) — 2026-04-24 측정 실측 avg 기반.
    // 실측 avg 1.495ms 이지만 integer 반올림 · JIT 재컴파일 변동을 고려해 기준선 3ms.
    // 허용 배수 1.5 → ceiling 4ms. ~2.5x 실측 이상의 퇴행만 감지한다.
    private static final long BASELINE_FIND_RESULTS_AVG_MS = 3L;
    private static final double ALLOWED_REGRESSION = 1.5;

    @Autowired
    private ProbabilityRepository probabilityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void createSchemaAndSeed() {
        // 기존 테이블 정리 (reuse=true 컨테이너 재사용 대비).
        jdbcTemplate.execute("DROP TABLE IF EXISTS probabilities");
        jdbcTemplate.execute("DROP TABLE IF EXISTS answers");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users_tests");
        jdbcTemplate.execute("DROP TABLE IF EXISTS tests_items");
        jdbcTemplate.execute("DROP TABLE IF EXISTS items");
        jdbcTemplate.execute("DROP TABLE IF EXISTS tests");
        jdbcTemplate.execute("DROP TABLE IF EXISTS concepts");
        jdbcTemplate.execute("DROP TABLE IF EXISTS chapters");

        // create.sql 의 서브셋 (findResults 쿼리가 참조하는 테이블만).
        jdbcTemplate.execute(
            "CREATE TABLE chapters ("
                + "  chapter_id INT PRIMARY KEY, chapter_name VARCHAR(50),"
                + "  school_level VARCHAR(5), grade_level VARCHAR(5), semester VARCHAR(5),"
                + "  chapter_main VARCHAR(50), chapter_sub VARCHAR(50)"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE concepts ("
                + "  concept_id INT PRIMARY KEY, concept_name VARCHAR(70),"
                + "  concept_description TEXT, concept_chapter_id INT,"
                + "  concept_achievement_id INT, concept_achievement_name VARCHAR(120),"
                + "  skill_id INT,"
                + "  FOREIGN KEY (concept_chapter_id) REFERENCES chapters(chapter_id)"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE tests ("
                + "  test_id BIGINT AUTO_INCREMENT PRIMARY KEY, test_name VARCHAR(50),"
                + "  test_comments VARCHAR(500), test_school_level CHAR(2),"
                + "  test_grade_level CHAR(2), test_semester VARCHAR(3)"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE items ("
                + "  item_id BIGINT AUTO_INCREMENT PRIMARY KEY, item_answer VARCHAR(100),"
                + "  item_image_path VARCHAR(255), concept_id INT,"
                + "  FOREIGN KEY (concept_id) REFERENCES concepts(concept_id)"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE tests_items ("
                + "  test_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "  test_id BIGINT, item_id BIGINT, test_item_number INT,"
                + "  FOREIGN KEY (test_id) REFERENCES tests(test_id),"
                + "  FOREIGN KEY (item_id) REFERENCES items(item_id)"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE users_tests ("
                + "  user_test_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "  user_id BIGINT, test_id BIGINT,"
                + "  user_test_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + "    ON UPDATE CURRENT_TIMESTAMP,"
                + "  diagnosis_id BIGINT"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE answers ("
                + "  answer_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "  user_test_id BIGINT, item_id BIGINT, answer_code INT,"
                + "  FOREIGN KEY (user_test_id) REFERENCES users_tests(user_test_id),"
                + "  FOREIGN KEY (item_id) REFERENCES items(item_id)"
                + ")");
        jdbcTemplate.execute(
            "CREATE TABLE probabilities ("
                + "  probability_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "  answer_id BIGINT, concept_id INT, to_concept_depth INT,"
                + "  probability_percent DOUBLE,"
                + "  probability_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + "    ON UPDATE CURRENT_TIMESTAMP,"
                + "  FOREIGN KEY (answer_id) REFERENCES answers(answer_id),"
                + "  FOREIGN KEY (concept_id) REFERENCES concepts(concept_id)"
                + ")");

        // 시드
        jdbcTemplate.update(
            "INSERT INTO chapters VALUES (?, ?, ?, ?, ?, ?, ?)",
            1, "테스트 단원", "초등", "초1", "1학기", "수와 연산", "");
        jdbcTemplate.update(
            "INSERT INTO concepts VALUES (?, ?, ?, ?, ?, ?, ?)",
            1, "테스트 개념", "설명", 1, 1, "성취", 1);
        jdbcTemplate.update(
            "INSERT INTO tests (test_id, test_name, test_school_level, test_grade_level, test_semester)"
                + " VALUES (?, ?, ?, ?, ?)",
            1L, "테스트 학습지", "초등", "초1", "1학기");
        jdbcTemplate.update(
            "INSERT INTO items (item_id, item_answer, item_image_path, concept_id)"
                + " VALUES (?, ?, ?, ?)",
            1L, "42", null, 1);
        jdbcTemplate.update(
            "INSERT INTO tests_items (test_item_id, test_id, item_id, test_item_number)"
                + " VALUES (?, ?, ?, ?)",
            1L, 1L, 1L, 1);
        jdbcTemplate.update(
            "INSERT INTO users_tests (user_test_id, user_id, test_id) VALUES (?, ?, ?)",
            FIND_RESULTS_USER_TEST_ID, 1L, 1L);
        jdbcTemplate.update(
            "INSERT INTO users_tests (user_test_id, user_id, test_id) VALUES (?, ?, ?)",
            2L, 1L, 1L);
        jdbcTemplate.update(
            "INSERT INTO answers (answer_id, user_test_id, item_id, answer_code)"
                + " VALUES (?, ?, ?, ?)",
            FIND_RESULTS_ANSWER_ID, FIND_RESULTS_USER_TEST_ID, 1L, 1);
        jdbcTemplate.update(
            "INSERT INTO answers (answer_id, user_test_id, item_id, answer_code)"
                + " VALUES (?, ?, ?, ?)",
            BATCH_INSERT_ANSWER_ID, 2L, 1L, 1);

        // findResults 용 시드 probabilities — depth 0/1/2 만 사용해 필터 (depth < 3) 전부 통과.
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < FIND_RESULTS_SEED_ROWS; i++) {
            rows.add(new Object[]{FIND_RESULTS_ANSWER_ID, 1, i % 3, 0.5});
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO probabilities (answer_id, concept_id, to_concept_depth,"
                + " probability_percent) VALUES (?, ?, ?, ?)",
            rows);

        int seeded = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM probabilities WHERE answer_id = ?",
            Integer.class, FIND_RESULTS_ANSWER_ID);
        System.out.printf("[Benchmark seed] probabilities for answer_id=%d : %d rows%n",
            FIND_RESULTS_ANSWER_ID, seeded);
    }

    // (6) 진단 결과 조회 (JPA 다중 조인) — JdbcTemplateProbabilityRepository.findResults.
    @Test
    void benchmarkFindResults() {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            probabilityRepository.findResults(FIND_RESULTS_USER_TEST_ID);
        }
        long[] nanos = new long[MEASURED_RUNS];
        int lastSize = -1;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            List<Result> results = probabilityRepository.findResults(FIND_RESULTS_USER_TEST_ID);
            nanos[i] = System.nanoTime() - start;
            lastSize = results.size();
        }
        System.out.printf("[Benchmark] findResults returned rows=%d (seed=%d, depth<3 filter)%n",
            lastSize, FIND_RESULTS_SEED_ROWS);
        BenchmarkStats.report(
            "findResults (userTestId=" + FIND_RESULTS_USER_TEST_ID + ")", nanos);
    }

    // (7) 100 개 확률 배치 삽입 — BatchPreparedStatementSetter.
    @Test
    void benchmarkBatchInsertProbabilities() {
        List<Probability> batch = new ArrayList<>(BATCH_INSERT_SIZE);
        for (int i = 0; i < BATCH_INSERT_SIZE; i++) {
            Probability p = new Probability();
            p.setAnswerId(BATCH_INSERT_ANSWER_ID);
            p.setConceptId(1);
            p.setToConceptDepth(i % 5);
            p.setProbabilityPercent(0.5);
            batch.add(p);
        }

        for (int i = 0; i < WARMUP_RUNS; i++) probabilityRepository.save(batch);

        long[] nanos = new long[MEASURED_RUNS];
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            probabilityRepository.save(batch);
            nanos[i] = System.nanoTime() - start;
        }
        BenchmarkStats.report("batchInsert100Probabilities", nanos);
    }

    // Task 2.2: 회귀 감지 테스트 — warmup 20 + 측정 30 회의 p50 (median) 이 ceiling 미만이어야 함.
    // 참고: GraphQueryPerformanceTest#shouldNotRegressDepth3GraphTraversal 과 동일한 방법론.
    @Test
    void shouldNotRegressFindResultsPerformance() {
        for (int i = 0; i < 20; i++) {
            probabilityRepository.findResults(FIND_RESULTS_USER_TEST_ID);
        }
        long[] nanos = new long[30];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            probabilityRepository.findResults(FIND_RESULTS_USER_TEST_ID);
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        long medianMs = nanos[nanos.length / 2] / 1_000_000;
        long ceilingMs = (long) (BASELINE_FIND_RESULTS_AVG_MS * ALLOWED_REGRESSION);
        System.out.printf("[Regression] findResults median=%dms baseline=%dms ceiling=%dms%n",
            medianMs, BASELINE_FIND_RESULTS_AVG_MS, ceilingMs);
        assertThat(medianMs).isLessThan(ceilingMs);
    }
}
