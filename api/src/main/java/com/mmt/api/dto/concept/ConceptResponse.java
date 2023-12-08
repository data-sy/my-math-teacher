package com.mmt.api.dto.concept;

import lombok.Data;

import java.util.List;

@Data
public class ConceptResponse {

    private int conceptId;
    private String conceptName;
    private String conceptDescription;
    private String conceptSchoolLevel;
    private String conceptGradeLevel;
    private String conceptSemester;
    private int conceptChapterId;
    private String conceptChapterName;
    private String conceptChapterMain;
    private String conceptChapterSub;
    private int conceptAchievementId;
    private String conceptAchievementName;
    private String conceptSection;

}
