package com.mmt.api.dto.network;

import com.mmt.api.domain.KnowledgeSpace;

import java.util.ArrayList;
import java.util.List;

public class NetworkConverter {

    public static List<EdgeResponse> convertToEdgeResponseList(List<KnowledgeSpace> knowledgeSpaces) {
        List<EdgeResponse> edgeResponses = new ArrayList<>();
        for (KnowledgeSpace knowledgeSpace : knowledgeSpaces) {
            EdgeResponse edgeResponse = new EdgeResponse();
            edgeResponse.setData(knowledgeSpace);
            edgeResponses.add(edgeResponse);
        }
        return edgeResponses;
    }

}
