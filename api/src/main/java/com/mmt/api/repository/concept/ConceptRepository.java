package com.mmt.api.repository.concept;

import com.mmt.api.domain.Concept;
import org.springframework.data.neo4j.repository.ReactiveNeo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ConceptRepository extends ReactiveNeo4jRepository<Concept, Integer> {

    Mono<Concept> findOneByConceptId(int conceptId);

    @Query("MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n)")
    Flux<Concept> findToConceptsByConceptId(@Param("conceptId") int conceptId);

    @Query("MATCH path = (n)-[*1..6]->(m {concept_id: $conceptId}) RETURN nodes(path)")
    Flux<Concept> findNodesByConceptId(@Param("conceptId") int conceptId);

    @Query("MATCH path = (start_node)-[*1..6]->(n {concept_id: $conceptId}) WITH nodes(path) AS connected_nodes\n" +
            "UNWIND connected_nodes AS node RETURN [id IN node.concept_id] AS concept_ids")
    Flux<Integer> findNodeIdsByConceptId(@Param("conceptId") int conceptId);

}
