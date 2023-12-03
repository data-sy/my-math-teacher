package com.mmt.api.controller;


import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.service.ConceptService;
import org.neo4j.driver.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/concepts")
public class ConceptController {

    private final ConceptService conceptService;

    public ConceptController(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    /**
     * 단위개념 상세 보기 (관계 필드 없을 때)
     */
    @GetMapping("/{conceptId}")
    public Mono<ConceptResponse> getConcept(@PathVariable int conceptId){
        return conceptService.findOne(conceptId);
    }

    /**
     * 단위개념 Mono말고 & DTO 말고 그냥 받아보기 (.block() 사용) & 관계 넣기
     */
    @GetMapping("/test/{conceptId}")
    public Concept getConceptTest(@PathVariable int conceptId){
        return conceptService.findOneTest(conceptId);
    }

    /**
     * 단위개념 상세 보기
     */
//    @GetMapping("/ttt/{conceptId}")
//    public Concept getConceptTTT(@PathVariable int conceptId){
//        return conceptService.findOneTTT(conceptId);
//    }

}
