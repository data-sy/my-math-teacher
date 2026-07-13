package com.mmt.api.repository.concept;

/**
 * knowledge_space 간선 (M2 방향 정의 승계): from = 후수(학습 시간상 뒤), to = 선수(먼저 알아야 함).
 * 개념 C 의 직계 선수 = from_concept_id = C 인 행들의 to_concept_id.
 */
public record KnowledgeEdge(int fromConceptId, int toConceptId) {}
