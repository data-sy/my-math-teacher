package com.mmt.api.dto.concept;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChapterIdConceptResponse {

    private int conceptId;
    private String conceptName;
    private String conceptDescription;
    private String conceptAchievementName;

}
