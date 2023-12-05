package com.mmt.api.repository.concept;

import com.mmt.api.domain.Concept;
import org.springframework.data.neo4j.repository.ReactiveNeo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface ConceptRepository extends ReactiveNeo4jRepository<Concept, Integer> {

    Mono<Concept> findOneByConceptId(int conceptId);

    @Query("MATCH (n)-[r]->(m{concept_id: $conceptId}) RETURN (n);")
    Flux<Concept> findToConceptsByConceptId(@Param("conceptId") int conceptId);

    @Query("MATCH path = (n)-[*1..6]->(m {concept_id: $conceptId}) RETURN nodes(path)")
    Flux<Concept> findPathConceptId(@Param("conceptId") int conceptId);

}
