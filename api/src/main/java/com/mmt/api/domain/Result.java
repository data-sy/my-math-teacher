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

    // 디버깅 용 : System.out.println(result);
    @Override
    public String toString() {
        return String.format("result{ testItemNumber = %d, conceptId = %d, toConceptDepth = %d }", testItemNumber, conceptId, toConceptDepth);
    }
}
