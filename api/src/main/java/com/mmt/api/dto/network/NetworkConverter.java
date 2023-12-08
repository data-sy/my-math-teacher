package com.mmt.api.dto.network;

import com.mmt.api.domain.Concept;
import com.mmt.api.domain.KnowledgeSpace;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class NetworkConverter {

//
//    public static Flux<NodeResponse> convertToNodeResponseFlux(Flux<Concept> fluxConcept) {
//        return fluxConcept.map(concept -> {
//            NodeResponse nodeResponse = new NodeResponse();
//            nodeResponse.setData(Mono.just(concept));
//            return nodeResponse;
//        });
//    }

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
