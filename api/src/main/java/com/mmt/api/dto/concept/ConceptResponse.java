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
    private String conceptChapterMain;
    private String conceptChapterSub;
    private String conceptChapterSubsub;
    private int conceptAchievementId;
    private String conceptAchievementName;

}
