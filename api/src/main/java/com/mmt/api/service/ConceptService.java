package com.mmt.api.service;


import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;

    public ConceptService(ConceptRepository conceptRepository, KnowledgeSpaceRepository knowledgeSpaceRepository, JdbcTemplateConceptRepository jdbcTemplateConceptRepository) {
        this.conceptRepository = conceptRepository;
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
    }
    @Transactional(readOnly = true)
    public Mono<ConceptResponse> findOne(int conceptId){
        return ConceptConverter.convertToMonoConceptResponse(conceptRepository.findOneByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findToConcepts(int conceptId){
        return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findToConceptsByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findNodesByConceptId(int conceptId){
        return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findNodesByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptId(int conceptId){
        return conceptRepository.findNodesIdByConceptId(conceptId);
    }

    public int findSkillIdByConceptId (int conceptId){
        return jdbcTemplateConceptRepository.findSkillIdByConceptId(conceptId);
    }

//    // 이건 RDB 사용 (나중에 Neo4j 사용한 거 만들어서 둘의 성능 비교해보기)
//    public List<ChapterIdConceptResponse> findAllByChapterId(int chapterId){
//        return ConceptConverter.convertListToConceptResponseList(jdbcTemplateConceptRepository.findAllByChapterId(chapterId));
//    }
    // Neo4j 사용
    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findNodesByChapterId(int chapterId){
        return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findNodesByChapterId(chapterId));
    }

//    /**
//     * 리팩토링 : nodes와 edges를 한꺼번에 보내기 (WebFlux 공부 필요)
//     */
//    @Transactional(readOnly = true)
//    public NetworkResponse findNetworkByConceptId(int conceptId){
//        // Spring WebFlux의 Flux객체를 사용하는 중이므로 일반적인 게터, 세터 방법 사용할 수 없음
//        // Flux<concept>에서 concept을 필드로 가지는 NodeResponse 클래스로 Flux<NodeResponse>
//        Flux<NodeResponse> nodeResponseFlux = conceptRepository.findNodesByConceptId(conceptId).flatMap(concept -> {
//            NodeResponse nodeResponse = new NodeResponse();
//            nodeResponse.setData(concept);
//            return Mono.just(nodeResponse);
//        });
//        // 여기까지는 성공!
//
//        NetworkResponse networkResponse = new NetworkResponse();
//        // Flux<NodeResponse> 자체를 리스펀스 보내는 건 성공했지만 Flux<NodeResponse>를 통채로 NetworkResponse의 필드로 담는 건 실패
//        return networkResponse;
//    }

}
