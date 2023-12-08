package com.mmt.api.service;

import com.mmt.api.dto.network.EdgeResponse;
import com.mmt.api.dto.network.NetworkConverter;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeSpaceService {

    private final KnowledgeSpaceRepository knowledgeSpaceRepository;

    public KnowledgeSpaceService(KnowledgeSpaceRepository knowledgeSpaceRepository) {
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
    }

    public List<EdgeResponse> findEdgesByConceptId(List<Integer> conceptIdList){
        return NetworkConverter.convertToEdgeResponseList(knowledgeSpaceRepository.findEdgesByConceptId(conceptIdList));
    }

}
