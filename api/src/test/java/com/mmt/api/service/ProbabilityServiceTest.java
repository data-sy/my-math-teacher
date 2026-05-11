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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
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
        // spec-02 Task 3.4: 호출 대상은 findNodesIdByConceptIdDepth3 → findPrerequisitesAsDepthMap
        verify(conceptService, never()).findPrerequisitesAsDepthMap(anyInt(), anyInt());
        // 빈 리스트로라도 save 는 호출됨 (service 계약).
        ArgumentCaptor<List<Probability>> captor = ArgumentCaptor.forClass(List.class);
        verify(probabilityRepository).save(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void createBuildsExpectedProbabilitiesFromDepthMap() {
        // spec-02 Task 3.4 후: ProbabilityService 는 conceptService.findPrerequisitesAsDepthMap
        // 의 Map 결과를 그대로 사용 (LogicUtil.bfs 외부 호출 제거 → .block() 도 자연 해소).
        long userTestId = 7L;

        Probability answer = new Probability();
        answer.setAnswerId(100L);
        answer.setConceptId(5);
        when(answerService.findIds(userTestId)).thenReturn(List.of(answer));

        // 시나리오: conceptId=5 의 선수 depth map = {5:0, 3:1, 2:2}.
        // 마이그레이션 전엔 Flux 결과 + LogicUtil.bfs 로 도출했으나 이제 직접 mock.
        when(conceptService.findPrerequisitesAsDepthMap(5, 3))
            .thenReturn(Map.of(5, 0, 3, 1, 2, 2));

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
