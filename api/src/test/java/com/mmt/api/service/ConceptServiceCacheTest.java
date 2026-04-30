package com.mmt.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmt.api.domain.Concept;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.concept.MysqlConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import com.mmt.api.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec-02 Task 2.1 캐시 동작 단위 테스트.
 *
 * 검증 범위:
 *  - cache miss → 리포지토리 호출 + Redis set
 *  - cache hit  → 리포지토리 미호출
 *  - invalidate → deleteByPattern 3 개 prefix 호출
 *
 * SpringBootTest 미사용. Mockito 로 RedisUtil + MysqlConceptRepository 격리하여
 * 캐싱 헬퍼 (cachedIdsOrCompute, cachedConceptsOrCompute) 의 동작만 검증.
 */
@ExtendWith(MockitoExtension.class)
class ConceptServiceCacheTest {

    @Mock private ConceptRepository conceptRepository;
    @Mock private KnowledgeSpaceRepository knowledgeSpaceRepository;
    @Mock private JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    @Mock private MysqlConceptRepository mysqlConceptRepository;
    @Mock private RedisUtil redisUtil;

    private ConceptService service;

    @BeforeEach
    void setUp() {
        service = new ConceptService(
            conceptRepository,
            knowledgeSpaceRepository,
            jdbcTemplateConceptRepository,
            Optional.of(mysqlConceptRepository),
            redisUtil,
            new ObjectMapper()
        );
        // 피처 플래그 true 강제 (테스트는 CTE 경로만 다룸)
        ReflectionTestUtils.setField(service, "useMysqlCte", true);
    }

    // ─── cachedIdsOrCompute (List<Integer>) ───

    @Test
    void idsCacheMissCallsRepositoryAndStores() {
        when(redisUtil.get("graph:prerequisites:ids:10:3")).thenReturn(null);
        when(mysqlConceptRepository.findPrerequisiteConceptIds(10, 3))
            .thenReturn(List.of(10, 1, 2, 3, 4));

        List<Integer> result = service.findNodesIdByConceptIdDepth3(10)
            .collectList().block();

        assertThat(result).containsExactly(10, 1, 2, 3, 4);
        verify(mysqlConceptRepository, times(1)).findPrerequisiteConceptIds(10, 3);
        verify(redisUtil, times(1)).set(
            eq("graph:prerequisites:ids:10:3"), any(List.class), anyLong());
    }

    @Test
    void idsCacheHitSkipsRepository() {
        when(redisUtil.get("graph:prerequisites:ids:10:3"))
            .thenReturn(List.of(10, 1, 2, 3, 4));

        List<Integer> result = service.findNodesIdByConceptIdDepth3(10)
            .collectList().block();

        assertThat(result).containsExactly(10, 1, 2, 3, 4);
        verify(mysqlConceptRepository, never()).findPrerequisiteConceptIds(anyInt(), anyInt());
        verify(redisUtil, never()).set(any(), any(), anyLong());
    }

    @Test
    void idsCacheNormalizesNumberType() {
        // Jackson 의 정수 디시리얼라이즈가 Long 으로 떨어질 수 있음 — Number 캐스팅 후 intValue() 정규화
        when(redisUtil.get("graph:prerequisites:ids:10:5"))
            .thenReturn(List.of(10L, 1L, 2L));

        List<Integer> result = service.findNodesIdByConceptIdDepth5(10)
            .collectList().block();

        assertThat(result).containsExactly(10, 1, 2);
        verify(mysqlConceptRepository, never()).findPrerequisiteConceptIds(anyInt(), anyInt());
    }

    // ─── cachedConceptsOrCompute (List<Concept>) ───

    @Test
    void conceptsCacheMissCallsRepositoryAndStores() {
        when(jdbcTemplateConceptRepository.findSchoolLevelByConceptId(10)).thenReturn("초등");
        when(redisUtil.get("graph:prerequisites:objs:10:3")).thenReturn(null);

        Concept c = new Concept();
        c.setConceptId(10);
        c.setName("start");
        when(mysqlConceptRepository.findPrerequisiteConcepts(10, 3))
            .thenReturn(List.of(c));

        var result = service.findNodesByConceptId(10).collectList().block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConceptId()).isEqualTo(10);
        verify(mysqlConceptRepository, times(1)).findPrerequisiteConcepts(10, 3);
        verify(redisUtil, times(1)).set(
            eq("graph:prerequisites:objs:10:3"),
            any(String.class),  // ObjectMapper 로 JSON 직렬화한 String 저장
            anyLong());
    }

    @Test
    void conceptsCacheHitDeserializesFromJsonString() throws Exception {
        when(jdbcTemplateConceptRepository.findSchoolLevelByConceptId(10)).thenReturn("고등");

        Concept c = new Concept();
        c.setConceptId(10);
        c.setName("cached node");
        c.setSchoolLevel("고등");
        String json = new ObjectMapper().writeValueAsString(List.of(c));
        when(redisUtil.get("graph:prerequisites:objs:10:5")).thenReturn(json);

        var result = service.findNodesByConceptId(10).collectList().block();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConceptId()).isEqualTo(10);
        assertThat(result.get(0).getConceptName()).isEqualTo("cached node");
        assertThat(result.get(0).getConceptSchoolLevel()).isEqualTo("고등");
        verify(mysqlConceptRepository, never()).findPrerequisiteConcepts(anyInt(), anyInt());
    }

    // ─── findToConcepts: depth=1 + self 필터링 ───

    @Test
    void findToConceptsFiltersOutStartNode() {
        when(redisUtil.get("graph:to-concepts:10")).thenReturn(null);

        Concept self = new Concept();
        self.setConceptId(10);
        Concept prereq1 = new Concept();
        prereq1.setConceptId(1);
        Concept prereq2 = new Concept();
        prereq2.setConceptId(2);

        // CTE depth=1 은 자기 자신 + 직접 선수 반환 → 자기 자신 제외 후 to-concepts 와 동치
        when(mysqlConceptRepository.findPrerequisiteConcepts(10, 1))
            .thenReturn(List.of(self, prereq1, prereq2));

        var result = service.findToConcepts(10).collectList().block();

        assertThat(result).hasSize(2);
        assertThat(result).extracting("conceptId").containsExactlyInAnyOrder(1, 2);
    }

    // ─── invalidateGraphCaches ───

    @Test
    void invalidateGraphCachesDeletesAllThreePrefixes() {
        when(redisUtil.deleteByPattern("graph:prerequisites:ids:*")).thenReturn(3L);
        when(redisUtil.deleteByPattern("graph:prerequisites:objs:*")).thenReturn(2L);
        when(redisUtil.deleteByPattern("graph:to-concepts:*")).thenReturn(1L);

        long deleted = service.invalidateGraphCaches();

        assertThat(deleted).isEqualTo(6L);
        verify(redisUtil).deleteByPattern("graph:prerequisites:ids:*");
        verify(redisUtil).deleteByPattern("graph:prerequisites:objs:*");
        verify(redisUtil).deleteByPattern("graph:to-concepts:*");
    }
}
