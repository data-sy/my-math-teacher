package com.mmt.api.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Probability {

    private Long answerId;
    private int conceptId;
    private int skillId;
    private int toConceptDepth;
    private double probabilityPercent;

    // 디버깅 용 : System.out.println(probability);
    @Override
    public String toString() {
        return String.format("probability{ answerId = %d, conceptId = %s, skillId = %s, toConceptDepth = %d, probabilityPercent = %f }", answerId, conceptId, skillId, toConceptDepth, probabilityPercent);
    }

}
