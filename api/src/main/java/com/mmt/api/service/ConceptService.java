package com.mmt.api.service;


import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptRepository;
import org.neo4j.driver.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ConceptService {

    private final ConceptRepository conceptRepository;

    public ConceptService(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }
    @Transactional(readOnly = true)
    public Mono<ConceptResponse> findOne(int conceptId){
        return ConceptConverter.convertToMonoConceptResponse(conceptRepository.findOneByConceptId(conceptId));
    }
//    @Transactional
//    public List<Pair<String, Object>> findToConcept(int conceptId){
//        List<Pair<String, Object>> result = conceptRepository.findConceptsAndKnowledgeSpacesByConceptId(conceptId);
//        System.out.println(result);
//        return result;
//    }
//
//    @Transactional(readOnly = true)
//    public List<KnowledgeSpace> findRel(int conceptId){
//        return conceptRepository.findRByConceptId(conceptId);
//    }

}
