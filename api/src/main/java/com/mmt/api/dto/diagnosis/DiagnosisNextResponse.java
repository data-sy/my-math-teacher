package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 적응형 순회 응답 (spec-01 §4.3): 다음 1개 개념 또는 done.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisNextResponse {
    private NextConcept next;      // done=true 면 null
    private Progress progress;     // done=true 면 null
    private boolean done;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextConcept {
        private int conceptId;
        private String conceptName;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private int asked;
        private int estimatedRemaining;
    }

    public static DiagnosisNextResponse done() {
        return new DiagnosisNextResponse(null, null, true);
    }
}
