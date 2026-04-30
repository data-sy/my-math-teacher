package com.mmt.api.service;

import com.mmt.api.dto.network.EdgeResponse;
import com.mmt.api.dto.network.NetworkConverter;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeSpaceService {

    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    // M2 Spec 02 Task 3.1 옵션 B: ConceptService 의 그래프 메서드를 경유하여
    // 피처 플래그 분기·캐싱이 자동 상속되도록 한다. ConceptRepository 직접 호출 제거.
    private final ConceptService conceptService;

    public KnowledgeSpaceService(KnowledgeSpaceRepository knowledgeSpaceRepository,
                                 JdbcTemplateConceptRepository jdbcTemplateConceptRepository,
                                 ConceptService conceptService) {
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
        this.conceptService = conceptService;
    }

    public List<EdgeResponse> findEdgesByConceptId(int conceptId){
        // 해당 컨셉 아이디가 속한 학교급 찾기
        String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);

        // 학교급에 따라 다른 메서드 사용 (ConceptService 경유 → 분기·캐싱 자동 상속)
        Flux<Integer> conceptIdFlux = schoolLevel.equals("초등")
            ? conceptService.findNodesIdByConceptIdDepth3(conceptId)
            : conceptService.findNodesIdByConceptIdDepth5(conceptId);

        // CTE 경로는 동기 결과를 Flux 로 래핑한 형태이므로 .block() 이 즉시 반환.
        // Neo4j 경로는 reactive — spec-03 Task 5.3 Neo4j 폐기 시 .block() 자체 삭제 예정.
        List<Integer> conceptIdList = conceptIdFlux.distinct().collectList().block();
        if (conceptIdList == null || conceptIdList.isEmpty()){ // 선수단위개념이 없는 최초의 단위개념
            return new ArrayList<>();
        } else {
            return NetworkConverter.convertToEdgeResponseList(knowledgeSpaceRepository.findEdgesByConceptId(conceptIdList));
        }
    }

}
