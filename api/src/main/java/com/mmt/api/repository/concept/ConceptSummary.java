package com.mmt.api.repository.concept;

/**
 * M7 진단 문답용 개념 요약 (spec-01 §4.3-5: description = concept_description 을
 * D5 "대표 예시" 1차 소스로 사용).
 */
public record ConceptSummary(int conceptId, String conceptName, String description) {}
