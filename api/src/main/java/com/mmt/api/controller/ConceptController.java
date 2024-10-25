package com.mmt.api.controller;

import com.mmt.api.dto.concept.ConceptNameResponse;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.dto.network.EdgeResponse;
import com.mmt.api.service.ConceptService;
import com.mmt.api.service.KnowledgeSpaceService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/concepts")
public class ConceptController {

    private final ConceptService conceptService;
    private final KnowledgeSpaceService knowledgeSpaceService;
    public ConceptController(ConceptService conceptService, KnowledgeSpaceService knowledgeSpaceService) {
        this.conceptService = conceptService;
        this.knowledgeSpaceService = knowledgeSpaceService;
    }

    /**
     * chapter_id에 따른 단위개념 목록 보기 (MySQL 사용)
     */
    @GetMapping("")
    public List<ConceptNameResponse> getConceptByChapterId(@RequestParam("chapterId") int chapterId){
        return conceptService.findConceptNameByChapterId(chapterId);
    }

    /**
     * 단위개념 상세 보기 (관계 필드 없을 때) (MySQL 사용)
     */
    @GetMapping("/{conceptId}")
    public ConceptResponse getConcept(@PathVariable int conceptId){
        return conceptService.findOne(conceptId);
    }

    /**
     * 깊이 1의 선수단위개념 목록 보기
     */
    @GetMapping("/prerequisite/{conceptId}")
    public Flux<ConceptResponse> getToConcepts(@PathVariable int conceptId){
        return conceptService.findToConcepts(conceptId);
    }

    /**
     * 깊이 0~n의 선수단위개념 목록 보기 (노드)
     */
    @GetMapping("/nodes/{conceptId}")
    public Flux<ConceptResponse> getNodesByConceptId(@PathVariable int conceptId){
        return conceptService.findNodesByConceptId(conceptId);
    }

    /**
     * 깊이 0~n의 선수단위개념 관계 보기 (엣지)
     */
    @GetMapping("/edges/{conceptId}")
    public List<EdgeResponse> getEdgesByConceptId(@PathVariable int conceptId){
        return knowledgeSpaceService.findEdgesByConceptId(conceptId);
    }

}
