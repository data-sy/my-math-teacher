package com.mmt.api.service;

import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptDepth;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import com.mmt.api.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M2 spec-02 Task 2.1 단위 테스트.
 * ConceptService 의 CTE 분기 경로에서 RedisUtil 캐시 wrap 동작(hit/miss/우회)
 * 과 키 prefix 4 종을 검증한다. Redis 실제 인스턴스가 없는 빠른 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ConceptServiceCacheTest {

    @Mock ConceptRepository conceptRepository;
    @Mock KnowledgeSpaceRepository knowledgeSpaceRepository;
    @Mock JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    @Mock RedisUtil redisUtil;

    @InjectMocks ConceptService service;

    @Nested
    class FlagOn_CtePath {

        @BeforeEach
        void enableFlag() {
            ReflectionTestUtils.setField(service, "useMysqlCte", true);
        }

        @Test
        void idMethod_cacheMiss_callsRepoAndStores() {
            String key = "graph:prerequisites:ids:300:3";
            when(redisUtil.get(key)).thenReturn(null);
            when(jdbcTemplateConceptRepository.findPrerequisitesWithDepth(300, 3))
                .thenReturn(List.of(
                    new ConceptDepth(300, 0),
                    new ConceptDepth(310, 1)));

            List<Integer> result = service.findNodesIdByConceptIdDepth3(300)
                .collectList().block();

            assertThat(result).containsExactly(300, 310);
            verify(jdbcTemplateConceptRepository).findPrerequisitesWithDepth(300, 3);
            verify(redisUtil).set(eq(key), eq(List.of(300, 310)), anyLong());
        }

        @Test
        void idMethod_cacheHit_skipsRepo() {
            String key = "graph:prerequisites:ids:300:3";
            when(redisUtil.get(key)).thenReturn(List.of(300, 310, 320, 330));

            List<Integer> result = service.findNodesIdByConceptIdDepth3(300)
                .collectList().block();

            assertThat(result).containsExactly(300, 310, 320, 330);
            verify(jdbcTemplateConceptRepository, never())
                .findPrerequisitesWithDepth(anyInt(), anyInt());
            verify(redisUtil, never()).set(anyString(), any(), anyLong());
        }

        @Test
        void idMethod_depthIncludedInKey() {
            // depth 2 와 5 는 서로 다른 키로 저장되어야 함 (오인 매칭 회귀 가드).
            when(redisUtil.get("graph:prerequisites:ids:300:2")).thenReturn(null);
            when(redisUtil.get("graph:prerequisites:ids:300:5")).thenReturn(null);
            when(jdbcTemplateConceptRepository.findPrerequisitesWithDepth(300, 2))
                .thenReturn(List.of(new ConceptDepth(300, 0)));
            when(jdbcTemplateConceptRepository.findPrerequisitesWithDepth(300, 5))
                .thenReturn(List.of(new ConceptDepth(300, 0)));

            service.findNodesIdByConceptIdDepth2(300).collectList().block();
            service.findNodesIdByConceptIdDepth5(300).collectList().block();

            verify(redisUtil).get("graph:prerequisites:ids:300:2");
            verify(redisUtil).get("graph:prerequisites:ids:300:5");
        }

        @Test
        void findToConcepts_usesToConceptsPrefix() {
            String key = "graph:to-concepts:5";
            when(redisUtil.get(key)).thenReturn(null);
            Concept c = new Concept();
            c.setConceptId(7);
            c.setName("선수 7");
            when(jdbcTemplateConceptRepository.findPrerequisiteConcepts(5, 1))
                .thenReturn(List.of(c));

            List<ConceptResponse> result = service.findToConcepts(5).collectList().block();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConceptId()).isEqualTo(7);
            assertThat(result.get(0).getConceptName()).isEqualTo("선수 7");
            verify(redisUtil).set(eq(key), any(), anyLong());
        }

        @Test
        void findNodesByConceptId_elementaryUsesObjsPrefixDepth3() {
            when(jdbcTemplateConceptRepository.findSchoolLevelByConceptId(11)).thenReturn("초등");
            String key = "graph:prerequisites:objs:11:3";
            when(redisUtil.get(key)).thenReturn(null);
            when(jdbcTemplateConceptRepository.findPrerequisiteConcepts(11, 3))
                .thenReturn(List.of());

            service.findNodesByConceptId(11).collectList().block();

            verify(redisUtil).get(key);
            verify(redisUtil).set(eq(key), any(), anyLong());
            verify(jdbcTemplateConceptRepository).findPrerequisiteConcepts(11, 3);
        }

        @Test
        void findNodesByConceptId_nonElementaryUsesObjsPrefixDepth5() {
            when(jdbcTemplateConceptRepository.findSchoolLevelByConceptId(11)).thenReturn("중등");
            String key = "graph:prerequisites:objs:11:5";
            when(redisUtil.get(key)).thenReturn(null);
            when(jdbcTemplateConceptRepository.findPrerequisiteConcepts(11, 5))
                .thenReturn(List.of());

            service.findNodesByConceptId(11).collectList().block();

            verify(redisUtil).get(key);
            verify(redisUtil).set(eq(key), any(), anyLong());
            verify(jdbcTemplateConceptRepository).findPrerequisiteConcepts(11, 5);
        }
    }

    @Nested
    class FlagOff_Neo4jPath {

        @BeforeEach
        void disableFlag() {
            ReflectionTestUtils.setField(service, "useMysqlCte", false);
        }

        @Test
        void idMethod_bypassesCache() {
            when(conceptRepository.findNodesIdByConceptIdDepth3(300))
                .thenReturn(Flux.just(300, 310));

            List<Integer> result = service.findNodesIdByConceptIdDepth3(300)
                .collectList().block();

            assertThat(result).containsExactly(300, 310);
            verifyNoInteractions(redisUtil);
            verifyNoInteractions(jdbcTemplateConceptRepository);
        }

        @Test
        void findToConcepts_bypassesCache() {
            when(conceptRepository.findToConceptsByConceptId(5))
                .thenReturn(Flux.empty());

            service.findToConcepts(5).collectList().block();

            verifyNoInteractions(redisUtil);
        }
    }
}
