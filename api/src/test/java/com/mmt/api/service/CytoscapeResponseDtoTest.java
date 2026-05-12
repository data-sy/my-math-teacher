package com.mmt.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.dto.network.EdgeResponse;
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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 spec-03 Task 4.3: Cytoscape.js 시각화 응답 DTO 안정성 검증.
 *
 * <p>spec body §Task 4.3 의 접근 (C): Neo4j vs CTE 원시 결과의 노드 집합 동등성은
 * Task 4.1 ({@code GraphRegressionSnapshotTest}) 이 이미 검증한다. 본 클래스는
 * 그 위에서 CTE 분기의 응답 DTO 직렬화가 (1) Cytoscape 가 요구하는 필수 필드를
 * 빠짐없이 제공하고 (2) Task 4.1 스냅샷의 노드 집합과 일치하는 응답을 산출하는지
 * 만 검증한다 — Neo4j 시드 LOAD CSV 부담을 피하고 Task 4.1 과의 중복을 줄임.
 *
 * <p>검증 대상 메서드:
 * <ul>
 *   <li>{@link ConceptService#findNodesByConceptId(int)} — 노드 응답</li>
 *   <li>{@link ConceptService#findToConcepts(int)} — depth 1 응답</li>
 *   <li>{@link KnowledgeSpaceService#findEdgesByConceptId(int)} — 엣지 응답</li>
 * </ul>
 *
 * <p>conceptId 는 M1 baseline 및 Task 4.1 스냅샷 representative_id 와 동일한 6646.
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
class CytoscapeResponseDtoTest {

    private static final int CONCEPT_ID = 6646;

    @Autowired
    ConceptService conceptService;

    @Autowired
    KnowledgeSpaceService knowledgeSpaceService;

    @Autowired
    RedisUtil redisUtil;

    @BeforeEach
    void cleanGraphCache() {
        // ConceptServiceFeatureFlagTest 와 동일한 우회: RedisUtil.set 의 클래스
        // 단위 serializer 교체로 다른 테스트의 잔존 캐시와 deserialize 충돌 가능.
        redisUtil.deleteByPrefix("graph:");
    }

    @Test
    void findNodesByConceptId_returnsConceptResponseWithRequiredFields() throws IOException {
        List<ConceptResponse> nodes = conceptService.findNodesByConceptId(CONCEPT_ID)
            .collectList().block();

        assertThat(nodes).isNotEmpty();
        nodes.forEach(c -> {
            assertThat(c.getConceptId())
                .as("Cytoscape 노드 식별 필드 conceptId 비-0")
                .isPositive();
            assertThat(c.getConceptName())
                .as("노드 표시 이름 비-null/공백")
                .isNotBlank();
        });

        Set<Integer> actualIds = nodes.stream()
            .map(ConceptResponse::getConceptId)
            .collect(Collectors.toCollection(TreeSet::new));

        // ConceptService.java:111-112 — 학교급에 따라 depth 3(초등) 또는 5(그 외).
        // 6646 의 schoolLevel 은 prod 데이터에서 결정되므로 두 depth 키를 모두
        // 시도해 응답이 일치하는 스냅샷 키를 찾는다.
        Set<Integer> snapshot3 = loadSnapshotIds("conceptId=6646,depth=3");
        Set<Integer> snapshot5 = loadSnapshotIds("conceptId=6646,depth=5");
        assertThat(actualIds)
            .as("응답 노드 집합이 Task 4.1 스냅샷 depth 3 또는 5 와 일치")
            .satisfiesAnyOf(
                ids -> assertThat(ids).isEqualTo(snapshot3),
                ids -> assertThat(ids).isEqualTo(snapshot5));
    }

    @Test
    void findToConcepts_returnsDepth1PrerequisitesWithRequiredFields() {
        List<ConceptResponse> direct = conceptService.findToConcepts(CONCEPT_ID)
            .collectList().block();

        // depth=1 결과는 self + 직접 선수개념. 시작 노드 1개는 항상 포함.
        assertThat(direct).isNotEmpty();
        direct.forEach(c -> assertThat(c.getConceptId()).isPositive());
    }

    @Test
    void findEdgesByConceptId_returnsEdgeResponseWithSourceTarget() {
        List<EdgeResponse> edges = knowledgeSpaceService.findEdgesByConceptId(CONCEPT_ID);

        assertThat(edges).isNotEmpty();
        edges.forEach(e -> {
            assertThat(e.getData()).as("EdgeResponse.data 비-null").isNotNull();
            assertThat(e.getData().getId()).isNotBlank();
            assertThat(e.getData().getSource()).isNotBlank();
            assertThat(e.getData().getTarget()).isNotBlank();
        });
    }

    @Test
    void responseJsonSerializationContainsAllConceptResponseFields() throws Exception {
        ConceptResponse first = conceptService.findNodesByConceptId(CONCEPT_ID)
            .blockFirst();
        assertThat(first).isNotNull();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(first);
        JsonNode node = mapper.readTree(json);

        // Cytoscape 가 직접 사용하는 핵심 필드 + 프론트 도메인 표시 필드.
        // 누락되면 시각화 또는 사이드패널이 깨짐 — 회귀 가드.
        List.of("conceptId", "conceptName", "conceptDescription",
                "conceptChapterId", "conceptChapterName",
                "conceptAchievementId", "conceptAchievementName")
            .forEach(field -> assertThat(node.has(field))
                .as("ConceptResponse JSON 에 %s 필드 존재", field)
                .isTrue());
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
