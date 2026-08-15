package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 결과 계약 (spec-01 §4.4) — preview 와 GET /diagnosis/{userTestId} 가 동일 shape.
 *
 * probabilityPercent·toConceptDepth 는 내부 용어로 응답에 노출하되 프론트 표기 금지
 * (PRD §4.2 — 표기 정본은 urgency 등급·urgencyBasis). links 는 spec-03 부착 지점 예약.
 * urgencyAvailable=false = fail-soft (TF Serving 결측 — 등급 없이 "몰라요" 목록 기반, §4.7).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResultResponse {

    private Headline headline;
    private List<ConceptCard> cards;
    private List<ConceptCard> more;
    private boolean urgencyAvailable;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Headline {
        private int totalAsked;
        private int weakCount;
        private String topConceptName;   // 약점 없으면 null
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConceptCard {
        private int conceptId;
        private String conceptName;
        private String level;            // "학교-학년-학기"
        private String chapter;          // "대-중-소"
        private String urgency;          // HIGH | MID | LOW | null(등급 결측)
        private UrgencyBasis urgencyBasis;
        private Double probabilityPercent; // 내부 용어 — 프론트 표기 금지 (PRD §4.2)
        private int toConceptDepth;        // 내부 용어 — 0 = "몰라요" 응답 개념
        // M8 spec-03: alive=TRUE, display_order 순, 개념당 최대 3 (U4).
        // 링크 결측은 계약 — 0개면 빈 배열이고 UI 가 섹션을 통째로 생략한다 (§2.2).
        private List<ExternalLink> links;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UrgencyBasis {
        private int blockedDescendants;  // "이걸 모르면 위로 N개가 막혀요" (S6)
    }
}
