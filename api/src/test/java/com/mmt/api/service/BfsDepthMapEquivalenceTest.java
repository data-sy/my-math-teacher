package com.mmt.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 spec-03 Task 4.4: 거리 맵 의미 보존 검증.
 *
 * <p>spec body §Task 4.4 의 결정: {@link com.mmt.api.util.LogicUtil#bfs} 는
 * ID 리스트가 path order 라는 전제로 인접쌍 엣지를 가정해 그래프를 복원한다 —
 * CTE 결과(GROUP BY dedup)와 입력 형식이 달라 동일 Map 이 나오지 않는다(spec-01
 * A3 silent regression). 따라서 양쪽 Map 직접 비교 대신 CTE 분기 헬퍼의 거리
 * 맵이 의미적 안정성을 갖는지 (시작 노드=0, 0~maxDepth 범위, Task 4.1 스냅샷의
 * 노드 집합과 일치) 검증.
 *
 * <p>Neo4j 분기의 BFS 동작 보존은 spec-02 Task 3.4 단위 테스트가 검증.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "mmt.migration.use-mysql-cte-for-graph=true")
@Sql(scripts = {
    "classpath:regression_snapshot_schema.sql",
    "file:../api/sql/insert_chapters.sql",
    "file:../api/sql/insert_concepts_escape.sql",
    "file:../api/sql/insert_knowledge_space.sql"
})
class BfsDepthMapEquivalenceTest {

    private static final int CONCEPT_ID = 6646;

    @Autowired
    ConceptService conceptService;

    @Autowired
    RedisUtil redisUtil;

    @BeforeEach
    void cleanGraphCache() {
        redisUtil.deleteByPrefix("graph:");
    }

    @Test
    void cteDepthMap_startNodeIsZero() {
        Map<Integer, Integer> map = conceptService
            .findPrerequisitesAsDepthMap(CONCEPT_ID, 3);

        assertThat(map).isNotEmpty();
        assertThat(map.get(CONCEPT_ID))
            .as("시작 노드 %d 의 거리 = 0", CONCEPT_ID)
            .isZero();
    }

    @Test
    void cteDepthMap_allDistancesWithinMaxDepth() {
        int maxDepth = 3;
        Map<Integer, Integer> map = conceptService
            .findPrerequisitesAsDepthMap(CONCEPT_ID, maxDepth);

        map.forEach((id, d) -> assertThat(d)
            .as("conceptId=%d 의 거리가 0 이상 %d 이하", id, maxDepth)
            .isBetween(0, maxDepth));
    }

    @Test
    void cteDepthMap_keySetMatchesSnapshotNodeSet() throws IOException {
        Map<Integer, Integer> map = conceptService
            .findPrerequisitesAsDepthMap(CONCEPT_ID, 3);

        // 6646 의 schoolLevel 에 따라 헬퍼 내부 depth 가 결정되지 않음 — 본 호출
        // 은 maxDepth=3 을 직접 전달. 스냅샷 키 (conceptId=6646,depth=3) 와 비교.
        Set<Integer> snapshot3 = loadSnapshotIds("conceptId=6646,depth=3");
        assertThat(map.keySet())
            .as("CTE depth=3 의 key 집합이 스냅샷의 depth=3 unique node 집합과 일치")
            .isEqualTo(snapshot3);
    }

    @Test
    void cteDepthMap_depth5_keySetMatchesSnapshot() throws IOException {
        Map<Integer, Integer> map = conceptService
            .findPrerequisitesAsDepthMap(CONCEPT_ID, 5);

        Set<Integer> snapshot5 = loadSnapshotIds("conceptId=6646,depth=5");
        assertThat(map.keySet()).isEqualTo(snapshot5);
        // depth=5 호출이라도 시작 노드 거리는 여전히 0.
        assertThat(map.get(CONCEPT_ID)).isZero();
        map.values().forEach(d -> assertThat(d).isBetween(0, 5));
    }

    private Set<Integer> loadSnapshotIds(String key) throws IOException {
        JsonNode root = new ObjectMapper().readTree(
            Files.readAllBytes(Paths.get("../shared/benchmark/neo4j-snapshot-20260512.json")));
        JsonNode ids = root.get(key).get("concept_ids");
        Set<Integer> unique = new TreeSet<>();
        ids.forEach(n -> unique.add(n.asInt()));
        return unique;
    }
}
