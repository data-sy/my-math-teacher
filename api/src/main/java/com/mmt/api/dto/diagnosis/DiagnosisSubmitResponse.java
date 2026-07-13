package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisSubmitResponse {
    private Long userTestId;
    private DiagnosisResultResponse result;
}
