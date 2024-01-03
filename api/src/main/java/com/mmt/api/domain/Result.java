package com.mmt.api.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Result {

    private Long probabilityId;
    private int testItemNumber;
    private int conceptId;
    private int toConceptDepth;
    private double probabilityPercent;
    private String conceptName;
    private String schoolLevel;
    private String gradeLevel;
    private String semester;
    private String chapterMain;
    private String chapterSub;
    private String chapterName;

}
