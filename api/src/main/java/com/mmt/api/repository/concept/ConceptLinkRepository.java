package com.mmt.api.repository.concept;

import com.mmt.api.domain.concept.ConceptLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * M8 spec-03 — 신규 리포지토리는 JPA (api/CLAUDE.md 영속성 규칙).
 *
 * 카드 links 부착은 반드시 이 IN 일괄 조회 1쿼리로 한다 — 개념별 반복 조회 금지 (§3.1 N+1).
 */
public interface ConceptLinkRepository extends JpaRepository<ConceptLink, Long> {

    /**
     * 여러 개념의 노출 대상 링크를 한 번에 조회 (alive=TRUE, 개념·순서 결정론).
     *
     * 개념당 상한 3개(U4)는 SQL 이 아니라 조립 단계에서 자른다 — 윈도우 함수 없이
     * 단일 쿼리를 유지하려는 의도적 선택이고, 시드 규모(개념당 3개 내외)에서 과다 반입이 없다.
     */
    List<ConceptLink> findByConceptIdInAndAliveTrueOrderByConceptIdAscDisplayOrderAscConceptLinkIdAsc(
            Collection<Integer> conceptIds);
}
