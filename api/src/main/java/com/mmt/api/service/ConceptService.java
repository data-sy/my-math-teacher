package com.mmt.api.service;


import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.Neo4jConceptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class ConceptService {

    private final Neo4jConceptRepository conceptRepository;

    public ConceptService(Neo4jConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }
    @Transactional(readOnly = true)
    public Mono<ConceptResponse> findOne(int conceptId){
        return ConceptConverter.convertToMonoConceptResponse(conceptRepository.findOneByConceptId(conceptId));
    }

//    @Transactional(readOnly = true)
    public Concept findOneTest(int conceptId){
        return conceptRepository.findOneByConceptId(conceptId).block();
    }

}
