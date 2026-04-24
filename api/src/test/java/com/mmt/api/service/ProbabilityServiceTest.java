package com.mmt.api.service;

import com.mmt.api.domain.Probability;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.probability.ProbabilityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbabilityServiceTest {

    @Mock
    private ProbabilityRepository probabilityRepository;

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private AnswerService answerService;

    @Mock
    private ConceptService conceptService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProbabilityService probabilityService;

    @Test
    void createWithEmptyAnswersSavesEmptyListWithoutBlockingCall() {
        long userTestId = 1L;
        when(answerService.findIds(userTestId)).thenReturn(Collections.emptyList());

        probabilityService.create(userTestId, new double[]{0.1, 0.2, 0.3});

        // depth0 이 비어 있으면 for 루프 자체가 돌지 않아 conceptService 호출 없음.
        verify(conceptService, never()).findNodesIdByConceptIdDepth3(anyInt());
        // 빈 리스트로라도 save 는 호출됨 (service 계약).
        ArgumentCaptor<List<Probability>> captor = ArgumentCaptor.forClass(List.class);
        verify(probabilityRepository).save(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void createDoesNotHangOnBlockAndBuildsExpectedProbabilities() {
        long userTestId = 7L;

        Probability answer = new Probability();
        answer.setAnswerId(100L);
        answer.setConceptId(5);
        when(answerService.findIds(userTestId)).thenReturn(List.of(answer));

        // Flux.collectList().block() 경로 검증: blocking 이 타임아웃 안에 완료되어야 함.
        // 입력 리스트 [99, 2, 3, 5] (size=4, 루프 i=1..2)
        // i=1: current=2, next=3 → 엣지 2-3
        // i=2: current=3, next=5 (start==5) → 엣지 3-5
        // BFS from 5: {5:0, 3:1, 2:2}
        when(conceptService.findNodesIdByConceptIdDepth3(5))
            .thenReturn(Flux.just(99, 2, 3, 5));

        when(conceptService.findSkillIdByConceptId(5)).thenReturn(1);
        when(conceptService.findSkillIdByConceptId(3)).thenReturn(2);
        when(conceptService.findSkillIdByConceptId(2)).thenReturn(3);

        double[] probs = new double[]{0.5, 0.6, 0.7};

        assertTimeout(Duration.ofSeconds(3), () ->
            probabilityService.create(userTestId, probs));

        ArgumentCaptor<List<Probability>> captor = ArgumentCaptor.forClass(List.class);
        verify(probabilityRepository).save(captor.capture());
        List<Probability> saved = captor.getValue();

        assertThat(saved).hasSize(3);
        // conceptId -> depth 매핑과 probability 값 (skillId-1 로 index)
        assertThat(saved)
            .extracting(Probability::getConceptId, Probability::getToConceptDepth, Probability::getProbabilityPercent)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(5, 0, 0.5),  // skill=1, probs[0]=0.5
                org.assertj.core.groups.Tuple.tuple(3, 1, 0.6),  // skill=2, probs[1]=0.6
                org.assertj.core.groups.Tuple.tuple(2, 2, 0.7)   // skill=3, probs[2]=0.7
            );
        assertThat(saved).allSatisfy(p -> assertThat(p.getAnswerId()).isEqualTo(100L));
    }
}
