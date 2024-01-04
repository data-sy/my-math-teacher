package com.mmt.api.dto.result;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ResultResponse {

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
    private List<ResultResponse> prerequisiteList;

    // 디버깅 용 : System.out.println(resultResponse);
    @Override
    public String toString() {
        return String.format("resultResponse{ testItemNumber = %d, conceptId = %d, toConceptDepth = %d }", testItemNumber, conceptId, toConceptDepth);
    }
}
