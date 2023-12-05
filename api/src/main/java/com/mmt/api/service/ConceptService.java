package com.mmt.api.service;


import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findToConcepts(int conceptId){
        return ConceptConverter.convertToMonoConceptResponse(conceptRepository.findToConceptsByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<Concept> findPathConceptId(int conceptId){
        return conceptRepository.findPathConceptId(conceptId);
    }

}
