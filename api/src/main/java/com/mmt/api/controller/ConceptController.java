package com.mmt.api.controller;


import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.service.ConceptService;
import org.neo4j.driver.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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
     * 선수 단위개념 목록 보기
     */
    @GetMapping("/to/{conceptId}")
    public Flux<ConceptResponse> getToConcepts(@PathVariable int conceptId){
        return conceptService.findToConcepts(conceptId);
    }

//    /**
//     * 선수 단위개념 경로 보기(1) : Flux<Integer>로 컨셉아이디만 리스펀스
//     */
//    @GetMapping("/path")
//    public Flux<Map<String, Object>> getPathConceptId(@RequestParam int conceptId){
//        return conceptService.findPathConceptId(conceptId);
//    }

}
