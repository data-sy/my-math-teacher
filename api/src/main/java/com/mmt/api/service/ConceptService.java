package com.mmt.api.service;


import com.mmt.api.domain.Concept;
import com.mmt.api.dto.concept.ChapterIdConceptResponse;
import com.mmt.api.dto.concept.ConceptConverter;
import com.mmt.api.dto.concept.ConceptNameResponse;
import com.mmt.api.dto.concept.ConceptResponse;
import com.mmt.api.dto.concept.ConceptSearchResponse;
import com.mmt.api.repository.concept.ConceptDepth;
import com.mmt.api.repository.concept.ConceptRepository;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.knowledgeSpace.KnowledgeSpaceRepository;
import com.mmt.api.util.LogicUtil;
import com.mmt.api.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class ConceptService {

    private static final Logger log = LoggerFactory.getLogger(ConceptService.class);

    private final ConceptRepository conceptRepository;
    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final JdbcTemplateConceptRepository jdbcTemplateConceptRepository;
    private final RedisUtil redisUtil;

    // RedisUtil.set(key, o, duration) 의 duration 은 MILLISECONDS 단위.
    // toSeconds() 사용 시 86.4 초만 캐싱되어 의도(24h)와 불일치하므로 .toMillis() 필수.
    private static final long TTL_24H = Duration.ofHours(24).toMillis();

    // 그래프 캐시 키 네임스페이스(버전 포함). value serializer 를
    // GenericJackson2JsonRedisSerializer 로 바꾸면서 캐시 값의 on-wire 포맷이
    // 이전(StringRedisSerializer/타입미지정 Jackson)과 호환되지 않는다. blue/green
    // 무중단 배포의 오버랩 구간에는 구·신 인스턴스가 같은 Redis 를 공유하므로,
    // 키 prefix 에 버전을 넣어 keyspace 를 분리한다 → 구 인스턴스는 절대 신 포맷
    // 값을 읽지 않고(구 코드엔 fallback 이 없음), 신 인스턴스는 v2 만 읽는다.
    // 구 "graph:*" 엔트리는 24h TTL 로 자연 만료(flush 불필요 → 로그아웃 blacklist
    // 등 비-graph 키 보존). deleteByPrefix("graph:") 는 prefix 매치라 v2 도 포함.
    // 포맷을 다시 바꿀 때만 이 버전을 올린다.
    private static final String GRAPH_NS = "graph:v2:";

    @Value("${mmt.migration.use-mysql-cte-for-graph:false}")
    private boolean useMysqlCte;

    public ConceptService(
        ConceptRepository conceptRepository,
        KnowledgeSpaceRepository knowledgeSpaceRepository,
        JdbcTemplateConceptRepository jdbcTemplateConceptRepository,
        RedisUtil redisUtil
    ) {
        this.conceptRepository = conceptRepository;
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.jdbcTemplateConceptRepository = jdbcTemplateConceptRepository;
        this.redisUtil = redisUtil;
    }

    // ADR 0003: RedisUtil 직접 호출 캐시 패턴. CTE 분기 경로에서만 사용.
    // Neo4j 경로는 자체 그래프 인덱스가 있고 결과 재사용 시 의미 차이로 잘못
    // 캐시 매칭될 위험이 있어 캐시 미적용.
    // spec-03 Task 4.2c (ADR 0003/0004): 캐시 히트율 측정을 위한 hit/miss 로그.
    // M2 모니터링 인프라가 M3 로 분리됐으므로 production dashboard 가 없다 —
    // 1개월 관찰 기간 동안 INFO 로그에서 "[cache] hit|miss key=graph:*" 패턴을
    // grep 으로 집계해 히트율(>90% 목표) 검증한다.
    private <T> List<T> cachedOrCompute(String key, Supplier<List<T>> compute) {
        @SuppressWarnings("unchecked")
        List<T> cached = (List<T>) redisUtil.get(key);
        if (cached != null) {
            log.info("[cache] hit key={}", key);
            return cached;
        }
        log.info("[cache] miss key={}", key);
        List<T> result = compute.get();
        redisUtil.set(key, result, TTL_24H);
        return result;
    }

    @Transactional(readOnly = true)
    public ConceptResponse findOne(int conceptId){
        return ConceptConverter.convertToConceptResponse(jdbcTemplateConceptRepository.findOneByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findToConcepts(int conceptId){
        if (useMysqlCte) {
            String key = GRAPH_NS + "to-concepts:" + conceptId;
            List<ConceptResponse> result = cachedOrCompute(key, () ->
                ConceptConverter.convertListToConceptResponseList(
                    jdbcTemplateConceptRepository.findPrerequisiteConcepts(conceptId, 1)));
            return Flux.fromIterable(result);
        }
        return ConceptConverter.convertToFluxConceptResponse(conceptRepository.findToConceptsByConceptId(conceptId));
    }

    @Transactional(readOnly = true)
    public Flux<ConceptResponse> findNodesByConceptId(int conceptId){
        // 해당 컨셉 아이디가 속한 학교급 찾기 → 깊이 결정 (초등=3, 그 외=5)
        String schoolLevel = jdbcTemplateConceptRepository.findSchoolLevelByConceptId(conceptId);
        int depth = schoolLevel.equals("초등") ? 3 : 5;
        if (useMysqlCte) {
            String key = GRAPH_NS + "prerequisites:objs:" + conceptId + ":" + depth;
            List<ConceptResponse> result = cachedOrCompute(key, () ->
                ConceptConverter.convertListToConceptResponseList(
                    jdbcTemplateConceptRepository.findPrerequisiteConcepts(conceptId, depth)));
            return Flux.fromIterable(result);
        }
        Flux<Concept> neo4j = (depth == 3)
            ? conceptRepository.findNodesByConceptIdDepth3(conceptId)
            : conceptRepository.findNodesByConceptIdDepth5(conceptId);
        return ConceptConverter.convertToFluxConceptResponse(neo4j);
    }

    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth2(int conceptId){
        if (useMysqlCte) {
            return Flux.fromIterable(cachedPrerequisiteIds(conceptId, 2));
        }
        return conceptRepository.findNodesIdByConceptIdDepth2(conceptId);
    }
    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth3(int conceptId){
        if (useMysqlCte) {
            return Flux.fromIterable(cachedPrerequisiteIds(conceptId, 3));
        }
        return conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
    }
    @Transactional(readOnly = true)
    public Flux<Integer> findNodesIdByConceptIdDepth5(int conceptId){
        if (useMysqlCte) {
            return Flux.fromIterable(cachedPrerequisiteIds(conceptId, 5));
        }
        return conceptRepository.findNodesIdByConceptIdDepth5(conceptId);
    }

    // ID 반환 그래프 메서드 3종이 공유하는 캐시 wrap. depth 만 다름.
    private List<Integer> cachedPrerequisiteIds(int conceptId, int depth) {
        String key = GRAPH_NS + "prerequisites:ids:" + conceptId + ":" + depth;
        // 캐시 저장 타입은 표준 mutable JDK 컬렉션(ArrayList)으로 고정한다.
        // Stream.toList() 가 반환하는 불변 리스트(ImmutableCollections$ListN)는
        // GenericJackson2JsonRedisSerializer 의 @class 타입 정보로 역직렬화되지
        // 않아 인스턴스 간 read 시 InvalidTypeIdException 을 낸다(RedisConfig 주석 참조).
        return cachedOrCompute(key, () ->
            jdbcTemplateConceptRepository.findPrerequisitesWithDepth(conceptId, depth)
                .stream().map(ConceptDepth::conceptId)
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    /**
     * conceptId 에서 maxDepth 단계까지의 선수 개념을 id → 최단 거리 형태의
     * Map 으로 반환. spec-01 A3: ProbabilityService 가 LogicUtil.bfs 의 path
     * 시퀀스 의존을 우회하기 위해 CTE 결과의 MIN(depth) 를 직접 사용한다.
     *
     * 플래그 OFF (Neo4j) 경로는 기존 LogicUtil.bfs 동작을 보존하며, Neo4j
     * 폐기(spec-03 Task 5.3) 시 LogicUtil 과 함께 일괄 삭제.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Integer> findPrerequisitesAsDepthMap(int conceptId, int maxDepth) {
        if (useMysqlCte) {
            String key = GRAPH_NS + "prerequisites:depthmap:" + conceptId + ":" + maxDepth;
            // 캐시는 List<ConceptDepth>(ArrayList) 로 저장하고 Integer 키 Map 은 read
            // 후 재구성한다. Map<Integer,Integer> 를 그대로 JSON 캐시하면 객체 키가
            // String 으로 뭉개져(JSON object 키 규칙상) Integer 키 조회가 깨진다 —
            // ProbabilityService 가 entry.getKey() 를 int 로 소비하므로 치명적이고,
            // 이는 serializer 뮤테이션 버그와 별개로 depthmap 경로에 잠복해 있었다.
            List<ConceptDepth> pairs = cachedOrCompute(key, () -> new ArrayList<>(
                jdbcTemplateConceptRepository.findPrerequisitesWithDepth(conceptId, maxDepth)));
            Map<Integer, Integer> depthMap = new HashMap<>();
            for (ConceptDepth cd : pairs) {
                depthMap.put(cd.conceptId(), cd.depth());
            }
            return depthMap;
        }
        Flux<Integer> idsFlux = switch (maxDepth) {
            case 2 -> conceptRepository.findNodesIdByConceptIdDepth2(conceptId);
            case 3 -> conceptRepository.findNodesIdByConceptIdDepth3(conceptId);
            case 5 -> conceptRepository.findNodesIdByConceptIdDepth5(conceptId);
            default -> throw new IllegalArgumentException(
                "Neo4j 경로는 depth 2/3/5 만 지원. got: " + maxDepth);
        };
        List<Integer> ids = idsFlux.collectList().block();
        return LogicUtil.bfs(conceptId, ids);
    }

    public int findSkillIdByConceptId (int conceptId){
        return jdbcTemplateConceptRepository.findSkillIdByConceptId(conceptId);
    }

    public List<ConceptNameResponse> findConceptNameByChapterId(int chapterId){
        return ConceptConverter.convertListToConceptNameResponseList(jdbcTemplateConceptRepository.findAllByChapterId(chapterId));
    }

    /**
     * 개념명 검색(자동완성). 공백/빈 질의는 빈 목록, limit 은 1~50 으로 클램프.
     * schoolLevel 이 주어지면 학교급으로 좁힌다(null/blank 면 전체).
     */
    public List<ConceptSearchResponse> searchConcepts(String query, String schoolLevel, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return ConceptConverter.convertListToConceptSearchResponseList(
                jdbcTemplateConceptRepository.searchByName(query.trim(), schoolLevel, safeLimit));
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
