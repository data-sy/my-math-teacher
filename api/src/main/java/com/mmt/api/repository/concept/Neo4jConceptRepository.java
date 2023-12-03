package com.mmt.api.repository.concept;

import com.mmt.api.domain.Concept;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.repository.ReactiveNeo4jRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@Primary
public interface Neo4jConceptRepository extends ReactiveNeo4jRepository<Concept, Integer> {
    Mono<Concept> findOneByConceptId(int conceptId);

//    @Override
//    @Query("MATCH path = (start_node)-[*6]->(n {concept_id: ?}) RETURN nodes(path) as concepts , relationships(path) as knowledgeSpaces;")
//    List<Pair<String, Object>> findConceptsAndKnowledgeSpacesByConceptId(int conceptId);

//    @Override
//    @Query("MATCH (n)->[r]->(m:{concept_id: ?}) RETURN r")
//    List<KnowledgeSpace> findRByConceptId(int conceptId);
}
