package com.mmt.api.repository.probability;

/**
 * M7 spec-01 자가진단 확률 행 — probabilities 골격 재사용 + user_test_id 스코프
 * (answer_id = NULL). percent 는 fail-soft 결측 허용(스키마 nullable): TF Serving
 * 실패·skill_id 미매핑 시 시급도 등급만 결측되고 "몰라요" 목록 기반 결과는 성립 (spec-01 §4.7).
 */
public record DiagnosisProbabilityRow(int conceptId, int toConceptDepth, Double probabilityPercent) {}
