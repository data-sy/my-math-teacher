package com.mmt.api.controller;

import com.mmt.api.domain.KnowledgeSpace;
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
     * 단위개념 상세 보기
     */
    @GetMapping("/{conceptId}")
    public Mono<ConceptResponse> getConcept(@PathVariable int conceptId){
        return conceptService.findOne(conceptId);
    }

//    /**
//     * 단위개념의 선수지식 목록
//     */
//    @GetMapping("/to-concepts/{conceptId}")
//    public List<Pair<String, Object>> getToConcepts(@PathVariable int conceptId){
//        return conceptService.findToConcept(conceptId);
//    }
//
//    /**
//     * 단위개념의 1차 선수지식 (테스트용)
//     */
//    @GetMapping("/rel/{conceptId}")
//    public List<KnowledgeSpace> getRel(@PathVariable int conceptId){
//        return conceptService.findRel(conceptId);
//    }

}
