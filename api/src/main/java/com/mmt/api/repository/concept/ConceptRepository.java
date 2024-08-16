package com.mmt.api.repository.concept;

import com.mmt.api.domain.Concept;
import org.springframework.data.neo4j.repository.ReactiveNeo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
@Repository
public interface ConceptRepository extends ReactiveNeo4jRepository<Concept, Integer> {

    Mono<Concept> findOneByConceptId(int conceptId);

    @Query("MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)")
    Flux<Concept> findToConceptsByConceptId(@Param("conceptId") int conceptId);

    @Query("MATCH (n)-[*0..5]->(m {concept_id: $conceptId}) RETURN (n)")
    Flux<Concept> findNodesByConceptIdDepth5(@Param("conceptId") int conceptId);

    // 초등은 depth 3까지만
    @Query("MATCH (n)-[*0..3]->(m {concept_id: $conceptId}) RETURN (n)")
    Flux<Concept> findNodesByConceptIdDepth3(@Param("conceptId") int conceptId);

    @Query("MATCH path = (start_node)-[*0..5]->(n {concept_id: $conceptId}) WITH nodes(path) AS connected_nodes\n" +
            "UNWIND connected_nodes AS node RETURN [id IN node.concept_id] AS concept_ids")
    Flux<Integer> findNodesIdByConceptIdDepth5(@Param("conceptId") int conceptId);

    // [*0..$depth] 불가. 여기에는 리터럴만 가능하대. 그래서 메서드 분리해서 사용 중
    @Query("MATCH path = (start_node)-[*0..2]->(n {concept_id: $conceptId}) WITH nodes(path) AS connected_nodes\n" +
            "UNWIND connected_nodes AS node RETURN [id IN node.concept_id] AS concept_ids")
    Flux<Integer> findNodesIdByConceptIdDepth2(@Param("conceptId") int conceptId);

    // 초등 지식에 대해서
    @Query("MATCH path = (start_node)-[*0..3]->(n {concept_id: $conceptId}) WITH nodes(path) AS connected_nodes\n" +
            "UNWIND connected_nodes AS node RETURN [id IN node.concept_id] AS concept_ids")
    Flux<Integer> findNodesIdByConceptIdDepth3(@Param("conceptId") int conceptId);

//    @Query("MATCH (n{chapter_id: $chapterId}) RETURN (n)")
    @Query("MATCH (n:concept {chapter_id: $chapterId}) WITH n RETURN n")
    Flux<Concept> findNodesByChapterId(@Param("chapterId") int chapterId);

//    // 성능 테스트
//    @Query("MATCH path = (start_node)-[*1..6]->(n {concept_id: $conceptId}) RETURN nodes(path), relationships(path)")
//    Flux<PathResult> findPathsByConceptId(@Param("conceptId") int conceptId);

    // 연결 테스트를 위한 간단한 쿼리
    @Query("RETURN 1")
    Flux<Integer> testConnection();
}
