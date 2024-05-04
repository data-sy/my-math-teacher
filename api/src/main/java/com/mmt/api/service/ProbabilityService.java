package com.mmt.api.service;

import com.mmt.api.domain.Probability;
import com.mmt.api.dto.result.ResultConverter;
import com.mmt.api.dto.result.ResultResponse;
import com.mmt.api.repository.Probability.ProbabilityRepository;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.util.LogicUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProbabilityService {

    private final ProbabilityRepository probabilityRepository;
    private final ConceptRepository conceptRepository;
    private final AnswerService answerService;
    private final ConceptService conceptService;

    public ProbabilityService(ProbabilityRepository probabilityRepository, ConceptRepository conceptRepository, AnswerService answerService, ConceptService conceptService) {
        this.probabilityRepository = probabilityRepository;
        this.conceptRepository = conceptRepository;
        this.answerService = answerService;
        this.conceptService = conceptService;
    }

    public void create(Long userTestId, double[] probabilityList){
        // save에 필요한 데이터들 : answer_id, concept_id, to_concept_depth, probability_percent
        // probability 클래스는 : 위 4개에 skill_id 추가
        // 해당 학습지에서 answer_code = 0인 answer_id, concept_id, skill_id 추출
        List<Probability> depth0 = answerService.findIds(userTestId);
        List<Probability> depthN = new ArrayList<>();
        for (Probability probability : depth0){
            // 선수지식들 찾기
            int conceptId = probability.getConceptId();
            // 여기서는 path 살린 쿼리문 사용해야 함 - path 역추적해서 깊이 찾을거니까
            Flux<Integer> conceptIdFlux = conceptService.findNodesIdByConceptIdDepth3(conceptId);
            List<Integer> conceptIdList = conceptIdFlux.collectList().block();
            // key는 선수단위개념id, value는 depth (bfs로 path 최단길이 찾기)
            Map<Integer, Integer> depthDic = LogicUtil.bfs(conceptId, conceptIdList);
            for (Map.Entry<Integer, Integer> entry : depthDic.entrySet()) {
                Probability prerequisite = new Probability();
                prerequisite.setAnswerId(probability.getAnswerId());
                prerequisite.setConceptId(entry.getKey());
                prerequisite.setToConceptDepth(entry.getValue());
                depthN.add(prerequisite);
            }
        }
        // for 문 돌려서 각각의 pro객체에 해당 퍼센트 집어 넣기 probabilityList[skill_id-1] 사용
        // 원래 위에서 neo4j 돌릴 때 같이 찾아서 넣을까 했는데 path를 살려서 중복도 많고 로직도 적용해야 하니까 우선은 이 방법으로 skill_id 넣기
        for(Probability probability : depthN){
            int skillId = conceptService.findSkillIdByConceptId(probability.getConceptId());
            probability.setProbabilityPercent(probabilityList[skillId-1]);
        }
        probabilityRepository.save(depthN);
    }

    public List<ResultResponse> findResults(Long userTestId){
        return ResultConverter.convertListToResultResponseList(probabilityRepository.findResults(userTestId));
    }

}
