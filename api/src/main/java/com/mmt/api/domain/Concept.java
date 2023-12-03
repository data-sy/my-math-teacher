package com.mmt.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.data.neo4j.core.schema.Relationship.Direction.INCOMING;

@Data
@Node("concept")
public class Concept {

    @Id
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
    @Relationship(type="KNOWLEDGE_SPACE", direction = INCOMING)
    private Set<Concept> toConcepts = new HashSet<>();

    public Concept(int conceptId, String name, String desc, String schoolLevel, String gradeLevel, String semester, int chapterId, String chapterMain, String chapterSub, String chapterSubsub, int achievementId, String achievementName) {
        this.conceptId = conceptId;
        this.name = name;
        this.desc = desc;
        this.schoolLevel = schoolLevel;
        this.gradeLevel = gradeLevel;
        this.semester = semester;
        this.chapterId = chapterId;
        this.chapterMain = chapterMain;
        this.chapterSub = chapterSub;
        this.chapterSubsub = chapterSubsub;
        this.achievementId = achievementId;
        this.achievementName = achievementName;
    }

}
