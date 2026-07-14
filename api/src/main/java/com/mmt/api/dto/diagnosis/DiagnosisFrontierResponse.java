package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 시작 프론티어 응답 (spec-01 §4.2). concepts 순서 = concept_id 오름차순 (결정론).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisFrontierResponse {
    private List<FrontierConcept> concepts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrontierConcept {
        private int conceptId;
        private String conceptName;
    }
}
