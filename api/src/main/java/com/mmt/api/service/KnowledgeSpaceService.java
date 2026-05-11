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
    private final ConceptService conceptService;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;

    public KnowledgeSpaceService(
        KnowledgeSpaceRepository knowledgeSpaceRepository,
        ConceptService conceptService,
        JdbcTemplateConceptRepository jdbcTemplateConceptRepository
    ) {
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.conceptService = conceptService;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
    }

    public List<EdgeResponse> findEdgesByConceptId(int conceptId){
        // 해당 컨셉 아이디가 속한 학교급 찾기
        String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);

        // B1 경유: ConceptService 의 분기·캐시가 자동 상속.
        Flux<Integer> conceptIdFlux;
        if (schoolLevel.equals("초등")) conceptIdFlux = conceptService.findNodesIdByConceptIdDepth3(conceptId);
        else conceptIdFlux = conceptService.findNodesIdByConceptIdDepth5(conceptId);

        List<Integer> conceptIdList = conceptIdFlux.distinct().collectList().block();
        if (conceptIdList.isEmpty()){ // 선수단위개념이 없는 최초의 단위개념
            return new ArrayList<>();
        } else {
            return NetworkConverter.convertToEdgeResponseList(knowledgeSpaceRepository.findEdgesByConceptId(conceptIdList));
        }
    }

}
