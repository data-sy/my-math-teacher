package com.mmt.api.controller;


import com.mmt.api.dto.concept.ChapterIdConceptResponse;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.dto.network.EdgeResponse;
import com.mmt.api.service.ConceptService;
import com.mmt.api.service.KnowledgeSpaceService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * chapter_id에 따른 단위개념 목록 보기 (Neo4J 사용)
     */
    @GetMapping("")
    public Flux<ConceptResponse> getConceptByChapterId(@RequestParam("chapterId") int chapterId){
        return conceptService.findNodesByChapterId(chapterId);
    }
//    /**
//     * chapter_id에 따른 단위개념 목록 보기 (RDB 사용) => response데이터가 수정되면서 RDB로는 chapters 테이블 조인해서 사용해야 하기 때문에 deprecated
//     */
//    @GetMapping("")
//    public List<ChapterIdConceptResponse> getConceptByChapterId(@RequestParam("chapterId") int chapterId){
//        return conceptService.findAllByChapterId(chapterId);
//    }

    /**
     * deprecated 일 듯?!
     * 단위개념 상세 보기 (관계 필드 없을 때)
     */
    @GetMapping("/{conceptId}")
    public Mono<ConceptResponse> getConcept(@PathVariable int conceptId){
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
     * 깊이 0~6의 선수단위개념 목록 보기 (노드)
     */
    @GetMapping("/nodes/{conceptId}")
    public Flux<ConceptResponse> getNodesByConceptId(@PathVariable int conceptId){
        return conceptService.findNodesByConceptId(conceptId);
    }

    /**
     * 깊이 0~6의 선수단위개념 관계 보기 (엣지)
     */
    @GetMapping("/edges/{conceptId}")
    public List<EdgeResponse> getEdgesByConceptId(@PathVariable int conceptId){
        return knowledgeSpaceService.findEdgesByConceptId(conceptId);
    }

    // (테스트용) 깊이 1~6의 선수단위개념 id만 추출 & 그대로 리스펀스
    @GetMapping("/ids/{conceptId}")
    public Flux<Integer> getNodesIdByConceptId(@PathVariable int conceptId){
        return conceptService.findNodesIdByConceptId(conceptId);
    }

//    /**
//     * 리팩토링 : nodes와 edges를 한꺼번에 보내기 (WebFlux 공부 필요)
//     */
//    public NetworkResponse getNetworkByConceptId(@PathVariable int conceptId){
//        return conceptService.findNetworkByConceptId(conceptId);

//    /**
//     * deprecated
//     * 깊이 1~6의 선수단위개념 관계 보기 (엣지)
//     * 필요한 conceptId 목록을 request로 받아서 사용했음
//     */
//    @GetMapping("/edges")
//    public List<EdgeResponse> getEdgesByConceptId(@RequestBody EdgeRequest request){
//        return knowledgeSpaceService.findEdgesByConceptId(request.getConceptIdList());
//    }

}
