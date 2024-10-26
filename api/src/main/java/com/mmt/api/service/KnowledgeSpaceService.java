package com.mmt.api.service;

import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.network.EdgeResponse;
import com.mmt.api.dto.network.NetworkConverter;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeSpaceService {

    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final ConceptRepository conceptRepository;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    public KnowledgeSpaceService(KnowledgeSpaceRepository knowledgeSpaceRepository, ConceptRepository conceptRepository, JdbcTemplateConceptRepository jdbcTemplateConceptRepository) {
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.conceptRepository = conceptRepository;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
    }

    public List<EdgeResponse> findEdgesByConceptId(int conceptId){
        // 해당 컨셉 아이디가 속한 학교급 찾기
        String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);

        // 학교급에 따라 다른 메서드 사용
        Flux<Integer> conceptIdFlux;
        if (schoolLevel.equals("초등")) conceptIdFlux = conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
        else conceptIdFlux = conceptRepository.findNodesIdByConceptIdDepth5(conceptId);

        List<Integer> conceptIdList = conceptIdFlux.distinct().collectList().block();
        if (conceptIdList.isEmpty()){ // 선수단위개념이 없는 최초의 단위개념
            return new ArrayList<>();
        } else {
            return NetworkConverter.convertToEdgeResponseList(knowledgeSpaceRepository.findEdgesByConceptId(conceptIdList));
        }
    }

}
