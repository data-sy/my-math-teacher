package com.mmt.api.domain;

import lombok.Getter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.INCOMING;
import static org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING;

@Getter
@Node("concept")
public class Concept {

    @Id
    private int id;
    @Property("concept_id")
    private int conceptId;
    private String name;
    private String desc;
    @Property("school_level")
    private String schoolLevel;
    @Property("grade_level")
    private String gradeLevel;
    private String semester;
    @Property("chapter_id")
    private int chapterId;
    @Property("chapter_main")
    private String chapterMain;
    @Property("chapter_sub")
    private String chapterSub;
    @Property("chapter_subsub")
    private String chapterSubsub;
    @Property("achievement_id")
    private int achievementId;
    @Property("achievement_name")
    private String achievementName;

//    @Relationship(type="KNOWLEDGE_SPACE", direction = INCOMING)
//    private List<KnowledgeSpace> toConcept;
//    @Relationship(type="KNOWLEDGE_SPACE", direction = OUTGOING)
//    private List<KnowledgeSpace> fromConcept;

}
