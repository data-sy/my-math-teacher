package com.mmt.api.dto.result;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ResultResponse {

    private Long probabilityId;
    private int testItemNumber;
    private int conceptId; // 각자 자기 개념 번호
    private int toConceptDepth;
    private double probabilityPercent;
    private String conceptName;
    private String level; // 학교-학년-학기
    private String chapter; // 대-중-소

    // 디버깅 용 : System.out.println(resultResponse);
    @Override
    public String toString() {
        return String.format("resultResponse{ testItemNumber = %d, conceptId = %d, toConceptDepth = %d }", testItemNumber, conceptId, toConceptDepth);
    }
}
