package com.mmt.api.service;


import com.mmt.api.dto.concept.ChapterIdConceptResponse;
import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptNameResponse;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.concept.MysqlConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    // M1 Spec 03 Task 3.2: 피처 플래그 true 일 때만 스텁 bean 이 등록됨.
    // Optional 주입으로 false 상태에서 bean 미존재여도 컨텍스트 기동 가능.
    private final Optional<MysqlConceptRepository> mysqlConceptRepository;

    @Value("${mmt.migration.use-mysql-cte-for-graph:false}")
    private boolean useMysqlCte;

    public ConceptService(
        ConceptRepository conceptRepository,
        KnowledgeSpaceRepository knowledgeSpaceRepository,
        JdbcTemplateConceptRepository jdbcTemplateConceptRepository,
        Optional<MysqlConceptRepository> mysqlConceptRepository
    ) {
        this.conceptRepository = conceptRepository;
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
        this.mysqlConceptRepository = mysqlConceptRepository;
    }

    @Transactional(readOnly = true)
    public ConceptResponse findOne(int conceptId){
        return ConceptConverter.convertToConceptResponse(jdbcTemplateConceptRepository.findOneByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findToConcepts(int conceptId){
        return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findToConceptsByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findNodesByConceptId(int conceptId){
        // 해당 컨셉 아이디가 속한 학교급 찾기
        String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);
        // 학교급에 따라 다른 메서드 사용
        if (schoolLevel.equals("초등")) return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findNodesByConceptIdDepth3(conceptId));
        else return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findNodesByConceptIdDepth5(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth2(int conceptId){
        return conceptRepository.findNodesIdByConceptIdDepth2(conceptId);
    }
    // M1 Spec 03 Task 3.2: M2 MySQL CTE 마이그레이션을 위한 조건 분기.
    // 반환 타입 Flux<Integer> 는 그대로 유지 (ProbabilityService:65 등 기존 호출자와 호환).
    // true 경로는 리스트를 Flux.fromIterable 로 래핑해 동일 시그니처 보존.
    // 나머지 depth2/5 메서드는 M2 에서 동일 패턴으로 확장 예정.
    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            return Flux.fromIterable(
                mysqlConceptRepository.get().findPrerequisiteConceptIds(conceptId, 3));
        }
        return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
    }
    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth5(int conceptId){
        return conceptRepository.findNodesIdByConceptIdDepth5(conceptId);
    }

    public int findSkillIdByConceptId (int conceptId){
        return jdbcTemplateConceptRepository.findSkillIdByConceptId(conceptId);
    }

    public List<ConceptNameResponse> findConceptNameByChapterId(int chapterId){
        return ConceptConverter.convertListToConceptNameResponseList(jdbcTemplateConceptRepository.findAllByChapterId(chapterId));
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
