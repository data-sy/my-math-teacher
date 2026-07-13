package com.mmt.api.dto.diagnosis;

import lombok.Data;

import java.util.List;

@Data
public class DiagnosisNextRequest {
    private DiagnosisEntry entry;
    private List<AnsweredConcept> answered;
}
