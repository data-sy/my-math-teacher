package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * answered-map 원소 (spec-01 §4.3). 배열 순서 = 답변 입력 순서 — DKT 시퀀스 순서의
 * 정본이므로 재정렬 금지 (결정론 계약, spec-01 §4.4-2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnsweredConcept {
    private int conceptId;
    private boolean known;   // 안다=true / 모른다=false
}
