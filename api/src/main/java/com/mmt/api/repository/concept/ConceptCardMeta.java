package com.mmt.api.repository.concept;

/**
 * M7 진단 결과 카드 표시 메타 (spec-01 §4.4): level = "학교-학년-학기", chapter = "대-중-소".
 */
public record ConceptCardMeta(int conceptId, String conceptName, String level, String chapter) {}
