package com.mmt.api.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Getter
@Setter
public class KnowledgeSpace {

    @Id
    private Long id;

    @TargetNode
    private final Concept concept;

    public KnowledgeSpace(Concept concept) {
        this.concept = concept;
    }

}
