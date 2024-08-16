package com.mmt.api.service;

import com.mmt.api.domain.Probability;
import com.mmt.api.dto.AI.AIServingRequest;
import com.mmt.api.dto.AI.AIServingResponse;
import com.mmt.api.dto.AI.InputInstance;
import com.mmt.api.dto.answer.AnswerCreateRequest;
import com.mmt.api.dto.result.ResultConverter;
import com.mmt.api.dto.result.ResultResponse;
import com.mmt.api.repository.probability.ProbabilityRepository;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.util.LogicUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
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
    private final RestTemplate restTemplate;

    public ProbabilityService(ProbabilityRepository probabilityRepository, ConceptRepository conceptRepository, AnswerService answerService, ConceptService conceptService, RestTemplate restTemplate) {
        this.probabilityRepository = probabilityRepository;
        this.conceptRepository = conceptRepository;
        this.answerService = answerService;
        this.conceptService = conceptService;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void createAndPredict(AnswerCreateRequest request) {
        // 정오답 create
        answerService.create(request);
        // AI 분석
        List<Double> predictionList = getPrediction(request.getUserTestId()).getPredictions().get(0);
        double[] probabilityList = predictionList.stream().mapToDouble(Double::doubleValue).toArray();
        // AI 분석 결과 create
        create(request.getUserTestId(), probabilityList);
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
            Flux<Integer> conceptIdFlux = conceptService.findNodesIdByConceptIdDepth2(conceptId);
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

    // 입력값을 findAIInput으로
    public AIServingResponse getPrediction(Long userTestId) {
        String serverUrl = "http://13.124.61.161:8501/v1/models/my_model:predict";

        List<InputInstance> instances = answerService.findAIInput(userTestId);

        AIServingRequest aiServingRequest = new AIServingRequest();
        aiServingRequest.setSignatureName("serving_default");
        aiServingRequest.setInstances(instances);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 요청 엔티티 생성
        HttpEntity<AIServingRequest> requestEntity = new HttpEntity<>(aiServingRequest, headers);

        // 예측 요청
        try {
            ResponseEntity<AIServingResponse> response = restTemplate.postForEntity(serverUrl, requestEntity, AIServingResponse.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("예측 요청 실패. 상태 코드: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            System.out.println("예측 요청 실패. 클라이언트 에러: " + e.getRawStatusCode() + ", 응답: " + e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //    // 요청, 응답 모두 DTO로 => ok
//    public AIServingResponse getPredictionTest() {
//        String serverUrl = "http://13.124.61.161:8501/v1/models/my_model:predict";
//
//        // DTO를 사용한 입력 데이터 설정
//        InputInstance inputInstance = new InputInstance();
//        inputInstance.setInput(Arrays.asList(
//                new int[]{1171, 1}, new int[]{467, 1}, new int[]{1703, 1}, new int[]{1817, 1},
//                new int[]{1698, 1}, new int[]{623, 0}, new int[]{1182, 0}, new int[]{1614, 0},
//                new int[]{396, 0}, new int[]{1681, 0}, new int[]{1564, 1}, new int[]{461, 1},
//                new int[]{782, 1}, new int[]{593, 1}, new int[]{1582, 1}, new int[]{774, 0},
//                new int[]{1660, 0}, new int[]{1583, 0}, new int[]{790, 0}, new int[]{1531, 0}
//        ));
//        List<InputInstance> instances = new ArrayList<>();
//        instances.add(inputInstance);
//
//        AIServingRequest aiServingRequest = new AIServingRequest();
//        aiServingRequest.setSignatureName("serving_default");
//        aiServingRequest.setInstances(instances);
//
//        // 헤더 설정
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        // 요청 엔티티 생성
//        HttpEntity<AIServingRequest> requestEntity = new HttpEntity<>(aiServingRequest, headers);
//
//        // 예측 요청
//        try {
//            ResponseEntity<AIServingResponse> response = restTemplate.postForEntity(serverUrl, requestEntity, AIServingResponse.class);
//            if (response.getStatusCode().is2xxSuccessful()) {
//                return response.getBody();
//            } else {
//                throw new RuntimeException("예측 요청 실패. 상태 코드: " + response.getStatusCode());
//            }
//        } catch (HttpClientErrorException e) {
//            System.out.println("예측 요청 실패. 클라이언트 에러: " + e.getRawStatusCode() + ", 응답: " + e.getResponseBodyAsString());
//            return null;
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

}
