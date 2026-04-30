package com.mmt.api.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptNameResponse;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.concept.MysqlConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import com.mmt.api.util.RedisUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class ConceptService {

    // ADR 0004: RedisUtil 직접 호출 캐싱.
    // RedisUtil.set 의 duration 단위는 MILLISECONDS (RedisUtil:19).
    private static final long TTL_24H = Duration.ofHours(24).toMillis();

    // 캐시 키 prefix (ADR 0004 + spec-02 Task 2.1 규약)
    private static final String KEY_IDS_PREFIX = "graph:prerequisites:ids:";
    private static final String KEY_OBJS_PREFIX = "graph:prerequisites:objs:";
    private static final String KEY_TO_CONCEPTS_PREFIX = "graph:to-concepts:";

    private final ConceptRepository conceptRepository;
    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    // M1 Spec 03 Task 3.2: 피처 플래그 true 일 때만 스텁 bean 이 등록됨.
    // Optional 주입으로 false 상태에서 bean 미존재여도 컨텍스트 기동 가능.
    private final Optional<MysqlConceptRepository> mysqlConceptRepository;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    @Value("${mmt.migration.use-mysql-cte-for-graph:false}")
    private boolean useMysqlCte;

    public ConceptService(
        ConceptRepository conceptRepository,
        KnowledgeSpaceRepository knowledgeSpaceRepository,
        JdbcTemplateConceptRepository jdbcTemplateConceptRepository,
        Optional<MysqlConceptRepository> mysqlConceptRepository,
        RedisUtil redisUtil,
        ObjectMapper objectMapper
    ) {
        this.conceptRepository = conceptRepository;
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
        this.mysqlConceptRepository = mysqlConceptRepository;
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ConceptResponse findOne(int conceptId){
        return ConceptConverter.convertToConceptResponse(jdbcTemplateConceptRepository.findOneByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findToConcepts(int conceptId){
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            // ADR 0003: backward 단일 방향 → depth=1 의 선수가 곧 to-concepts.
            // Cypher (n)-[r]->(m{conceptId}) 는 m 미포함이므로 시작 노드 필터링 필수.
            String key = KEY_TO_CONCEPTS_PREFIX + conceptId;
            List<Concept> concepts = cachedConceptsOrCompute(key, () ->
                mysqlConceptRepository.get().findPrerequisiteConcepts(conceptId, 1).stream()
                    .filter(c -> c.getConceptId() != conceptId)
                    .collect(Collectors.toList()));
            return ConceptConverter.convertToFluxConceptResponse(Flux.fromIterable(concepts));
        }
        return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findToConceptsByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findNodesByConceptId(int conceptId){
        // 해당 컨셉 아이디가 속한 학교급 찾기
        String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);
        // 학교급에 따라 다른 메서드 사용
        int depth = schoolLevel.equals("초등") ? 3 : 5;

        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            String key = KEY_OBJS_PREFIX + conceptId + ":" + depth;
            List<Concept> concepts = cachedConceptsOrCompute(key, () ->
                mysqlConceptRepository.get().findPrerequisiteConcepts(conceptId, depth));
            return ConceptConverter.convertToFluxConceptResponse(Flux.fromIterable(concepts));
        }

        Flux<Concept> source = depth == 3
            ? conceptRepository.findNodesByConceptIdDepth3(conceptId)
            : conceptRepository.findNodesByConceptIdDepth5(conceptId);
        return ConceptConverter.convertToFluxConceptResponse(source);
    }

    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth2(int conceptId){
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            return Flux.fromIterable(cachedIdsOrCompute(conceptId, 2));
        }
        return conceptRepository.findNodesIdByConceptIdDepth2(conceptId);
    }
    // M1 Spec 03 Task 3.2 / M2 Spec 02 Task 3.1: M2 MySQL CTE 마이그레이션을 위한 조건 분기.
    // 반환 타입 Flux<Integer> 는 그대로 유지 (ProbabilityService:65 등 기존 호출자와 호환).
    // true 경로는 리스트를 Flux.fromIterable 로 래핑해 동일 시그니처 보존.
    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            return Flux.fromIterable(cachedIdsOrCompute(conceptId, 3));
        }
        return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
    }
    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth5(int conceptId){
        if (useMysqlCte && mysqlConceptRepository.isPresent()) {
            return Flux.fromIterable(cachedIdsOrCompute(conceptId, 5));
        }
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

    // ─── 캐싱 헬퍼 (ADR 0004) ───

    /**
     * List<Integer> 캐시 헬퍼. RedisUtil 의 Jackson2JsonRedisSerializer(ArrayList.class)
     * 라운드트립이 Integer 에는 안전 (스칼라 타입은 default typing 없이도 복원됨).
     */
    private List<Integer> cachedIdsOrCompute(int conceptId, int depth) {
        String key = KEY_IDS_PREFIX + conceptId + ":" + depth;
        Object raw = redisUtil.get(key);
        if (raw instanceof List<?> list) {
            // ArrayList<Integer> 또는 ArrayList<Number> (Jackson 정수 디시리얼라이즈) 를 정규화
            return list.stream()
                .map(o -> ((Number) o).intValue())
                .collect(Collectors.toList());
        }
        List<Integer> result = mysqlConceptRepository.get()
            .findPrerequisiteConceptIds(conceptId, depth);
        redisUtil.set(key, result, TTL_24H);
        return result;
    }

    /**
     * List<Concept> 캐시 헬퍼. RedisUtil 의 기본 직렬화는 default typing 미적용으로 POJO 라운드트립 불가.
     * 따라서 ObjectMapper 로 JSON 문자열 변환 후 String 으로 저장하고, 읽을 때 TypeReference 로 복원한다.
     * 직렬화 실패 시 캐시 미스로 처리하여 저장만 스킵 (호출자 영향 없음).
     */
    private List<Concept> cachedConceptsOrCompute(String key, Supplier<List<Concept>> compute) {
        Object raw = redisUtil.get(key);
        if (raw instanceof String json) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<Concept>>() {});
            } catch (Exception ignored) {
                // 캐시 형식 변경 등으로 파싱 실패 시 미스로 처리
            }
        }
        List<Concept> result = compute.get();
        try {
            redisUtil.set(key, objectMapper.writeValueAsString(result), TTL_24H);
        } catch (Exception ignored) {
            // 직렬화 실패 시 캐시 저장만 건너뛰고 결과는 정상 반환
        }
        return result;
    }

    /**
     * spec-02 Task 2.2: 그래프 캐시 prefix 3종 일괄 삭제. AdminController 가 호출.
     * @return 삭제된 키 총 개수
     */
    public long invalidateGraphCaches() {
        long ids = redisUtil.deleteByPattern(KEY_IDS_PREFIX + "*");
        long objs = redisUtil.deleteByPattern(KEY_OBJS_PREFIX + "*");
        long toConcepts = redisUtil.deleteByPattern(KEY_TO_CONCEPTS_PREFIX + "*");
        return ids + objs + toConcepts;
    }

}
