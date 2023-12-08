package com.mmt.api.domain;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

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
    @Property("chapter_name")
    private String chapterName;
    @Property("chapter_main")
    private String chapterMain;
    @Property("chapter_sub")
    private String chapterSub;
    @Property("achievement_id")
    private int achievementId;
    @Property("achievement_name")
    private String achievementName;
    private String section;

}
