package com.mmt.api.repository.concept;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmt.api.config.TestcontainersConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 spec-03 Task 4.1: Neo4j 결과 스냅샷과 MySQL 재귀 CTE 결과의 unique 노드 집합
 * 동등성 회귀 테스트.
 *
 * <p>스냅샷({@code shared/benchmark/neo4j-snapshot-20260512.json}) 의 각 키
 * {@code "conceptId=N,depth=D"} 에 대해 {@code findPrerequisitesWithDepth(N, D)}
 * 결과의 unique conceptId 집합이 스냅샷의 unique conceptId 집합과 일치하는지
 * 검증한다. 추가로 sorted unique 리스트의 sha256 해시도 양쪽이 동일한지 비교한다.
 *
 * <p>스냅샷의 {@code concept_ids} 는 Cypher {@code UNWIND nodes(path)} 결과라
 * 경로별 중복이 포함된 multiset 이며, MySQL CTE 는 {@code GROUP BY concept_id}
 * 로 dedup 된 결과를 반환한다 — 따라서 multiset 직접 비교는 불가하며 Set 비교
 * 만 의미 있다 (spec-03 §Task 4.1 비교 전략 참조).
 *
 * <p>시드: {@code api/sql/insert_*.sql} 의 prod 데이터를 그대로 재활용한다.
 * 스키마는 {@code regression_snapshot_schema.sql} 로 prod 의 chapters /
 * concepts / knowledge_space 컬럼 정의를 복제.
 */
@JdbcTest
@Import({TestcontainersConfig.class, JdbcTemplateConceptRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
// insert_concepts.sql 은 단일따옴표가 escape 되지 않아 testcontainers MySQL 8.0
// (STRICT_TRANS_TABLES) 에서 파싱 실패 → insert_concepts_escape.sql 사용.
// chapters / knowledge_space 는 정수 또는 따옴표 미포함 단순 문자열이라 escape 필요 없음.
@Sql(scripts = {
    "classpath:regression_snapshot_schema.sql",
    "file:../api/sql/insert_chapters.sql",
    "file:../api/sql/insert_concepts_escape.sql",
    "file:../api/sql/insert_knowledge_space.sql"
})
class GraphRegressionSnapshotTest {

    private static final Path SNAPSHOT_PATH =
        Paths.get("../shared/benchmark/neo4j-snapshot-20260512.json");

    // 스냅샷의 그래프 엔트리는 "conceptId=N,depth=D" 패턴. 메타데이터 키
    // (generated_at / git_commit / representative_id) 는 제외.
    private static final Set<String> METADATA_KEYS =
        Set.of("generated_at", "git_commit", "representative_id");

    @Autowired
    JdbcTemplateConceptRepository repository;

    @ParameterizedTest(name = "{0}")
    @MethodSource("snapshotEntries")
    void cteUniqueNodeSetMatchesNeo4jSnapshot(String key, int conceptId, int depth,
                                              Set<Integer> expectedUnique) {
        Set<Integer> actualUnique = repository.findPrerequisitesWithDepth(conceptId, depth)
            .stream()
            .map(ConceptDepth::conceptId)
            .collect(Collectors.toCollection(TreeSet::new));

        assertThat(actualUnique)
            .as("unique conceptId 집합 동등성 (%s)", key)
            .isEqualTo(expectedUnique);

        // sorted unique 리스트 문자열에 대한 sha256 재계산 — Set 비교가 통과하면
        // 이 단계도 정의상 통과하지만 정규화 절차의 무결성 회귀를 가드한다.
        String expectedHash = sha256(new TreeSet<>(expectedUnique).toString());
        String actualHash   = sha256(new TreeSet<>(actualUnique).toString());
        assertThat(actualHash)
            .as("sorted unique sha256 동등성 (%s)", key)
            .isEqualTo(expectedHash);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> snapshotEntries() throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readAllBytes(SNAPSHOT_PATH));
        List<org.junit.jupiter.params.provider.Arguments> args = new ArrayList<>();

        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (METADATA_KEYS.contains(key)) {
                continue;
            }
            // "conceptId=N,depth=D" 파싱
            String[] parts = key.split(",");
            int conceptId = Integer.parseInt(parts[0].substring("conceptId=".length()));
            int depth     = Integer.parseInt(parts[1].substring("depth=".length()));

            JsonNode ids = e.getValue().get("concept_ids");
            Set<Integer> unique = new TreeSet<>();
            for (JsonNode id : ids) {
                unique.add(id.asInt());
            }
            args.add(org.junit.jupiter.params.provider.Arguments.of(key, conceptId, depth, unique));
        }
        return args.stream();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
