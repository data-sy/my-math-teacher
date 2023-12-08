package com.mmt.api.repository.knowledgeSpace;

import com.mmt.api.domain.KnowledgeSpace;

import java.util.List;

public interface KnowledgeSpaceRepository {

    List<KnowledgeSpace> findEdgesByConceptId(List<Integer> conceptIdList);

}
