package com.mmt.api.service;

import com.mmt.api.dto.network.EdgeResponse;
import com.mmt.api.dto.network.NetworkConverter;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeSpaceService {

    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final ConceptRepository conceptRepository;

    public KnowledgeSpaceService(KnowledgeSpaceRepository knowledgeSpaceRepository, ConceptRepository conceptRepository) {
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.conceptRepository = conceptRepository;
    }

    public List<EdgeResponse> findEdgesByConceptId(int conceptId){
        Flux<Integer> conceptIdFlux = conceptRepository.findNodesIdByConceptId(conceptId);
        List<Integer> conceptIdList = conceptIdFlux.distinct().collectList().block();
        if (conceptIdList.isEmpty()){ // 선수단위개념이 없는 최초의 단위개념
            return new ArrayList<>();
        } else {
            return NetworkConverter.convertToEdgeResponseList(knowledgeSpaceRepository.findEdgesByConceptId(conceptIdList));
        }
    }

//    // deprecated
//    public List<EdgeResponse> findEdgesByConceptId(List<Integer> conceptIdList){
//        return NetworkConverter.convertToEdgeResponseList(knowledgeSpaceRepository.findEdgesByConceptId(conceptIdList));
//    }

}
