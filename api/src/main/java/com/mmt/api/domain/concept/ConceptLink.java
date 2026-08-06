package com.mmt.api.domain.concept;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * M8 spec-03 §2.1 — 개념별 외부 학습자료 링크 (무료 존, 비로그인 노출: F-1).
 *
 * 노출 규칙: alive=TRUE 만, display_order 순, 개념당 최대 3개 (U4).
 * 죽은 링크는 삭제가 아니라 alive=FALSE 로 비노출 — 점검 이력을 보존한다 (§4 R4).
 * 링크 결측은 계약이다 — 0개 개념은 링크 섹션 자체를 생략한다 (§2.2, "준비 중" 문구 없음).
 *
 * concepts 로의 FK 는 평컬럼으로만 모델링 — 실 FK 제약은 api/sql/create.sql 에만 둔다
 * (테스트 ddl-auto 가 비엔티티 테이블 FK 를 만들 수 없음, ADR-0010 Decision-3 관행 승계).
 */
@Entity
@Table(name = "concept_links")
@Getter
@Setter
@NoArgsConstructor
public class ConceptLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_link_id")
    private Long conceptLinkId;

    @Column(name = "concept_id", nullable = false)
    private Integer conceptId;

    /** 노출 문구 (예: "무료 강의 (EBS)") — 프론트가 그대로 렌더한다. */
    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** 'EBS' 등 — 노출·점검 그룹핑 키. provider 단위 일괄 비노출에 쓴다 (§4). */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /** R4 점검 결과. false = 비노출 (행은 남긴다). */
    @Column(name = "alive", nullable = false)
    private Boolean alive;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ConceptLink(Integer conceptId, String title, String url, String provider, Integer displayOrder) {
        this.conceptId = conceptId;
        this.title = title;
        this.url = url;
        this.provider = provider;
        this.displayOrder = displayOrder;
        this.alive = true;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (alive == null) {
            alive = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
