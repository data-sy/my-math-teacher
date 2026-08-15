package com.mmt.api.service;

import com.mmt.api.dto.diagnosis.DiagnosisResultResponse;
import com.mmt.api.dto.diagnosis.ExternalLink;
import com.mmt.api.repository.concept.ConceptCardMeta;
import com.mmt.api.service.DiagnosisAnalysisService.DiagnosisComputation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 결과 계약 조립 (spec-01 §4.4·§4.5) — 순수 함수: 동일 computation → 동일 응답 (결정론).
 *
 * 등급 컷(S4 잠정): HIGH p<40 · MID 40≤p<65 · LOW p≥65 — 구 ResultView 40/65 승계
 * (0~100 스케일 전제). 출발값일 뿐 — R2 실측 분포로 보정 예정 (백로그).
 *
 * top-N (PRD §4.1 확정): default = HIGH 전부, 바닥 3(부족 시 MID 시급도순 보충),
 * 캡 5(초과분 more). 정렬 = percent 오름차순(낮을수록 시급), 결측은 뒤로,
 * 동률·결측 내 순서 = (toConceptDepth, conceptId) 오름차순 — 전 키 결정론.
 */
final class DiagnosisResultAssembler {

    static final double HIGH_CUT = 40.0;
    static final double MID_CUT = 65.0;
    static final int TOP_FLOOR = 3;
    static final int TOP_CAP = 5;
    /** M8 spec-03 U4 — 개념당 링크 노출 상한. 모바일 카드 폭·선택 과부하 방지. */
    static final int LINK_CAP = 3;

    private DiagnosisResultAssembler() {}

    static DiagnosisResultResponse assemble(DiagnosisComputation computation,
                                            Map<Integer, ConceptCardMeta> metaByConcept,
                                            Map<Integer, Integer> blockedByConcept,
                                            Map<Integer, List<ExternalLink>> linksByConcept) {
        List<DiagnosisResultResponse.ConceptCard> sorted = computation.minDepthByConcept().entrySet().stream()
                .map(e -> {
                    int conceptId = e.getKey();
                    ConceptCardMeta meta = metaByConcept.get(conceptId);
                    Double percent = computation.percentByConcept().get(conceptId);
                    return new DiagnosisResultResponse.ConceptCard(
                            conceptId,
                            meta == null ? null : meta.conceptName(),
                            meta == null ? null : meta.level(),
                            meta == null ? null : meta.chapter(),
                            grade(percent),
                            new DiagnosisResultResponse.UrgencyBasis(
                                    blockedByConcept.getOrDefault(conceptId, 0)),
                            percent,
                            e.getValue(),
                            // 결측 허용이 계약 — 링크 0개면 빈 배열 (§2.2). 상한 3 은 여기서 자른다.
                            linksByConcept.getOrDefault(conceptId, List.of()).stream()
                                    .limit(LINK_CAP)
                                    .collect(Collectors.toList()));
                })
                .sorted(Comparator
                        .comparing((DiagnosisResultResponse.ConceptCard c) -> c.getProbabilityPercent() == null)
                        .thenComparing(c -> c.getProbabilityPercent() == null ? 0.0 : c.getProbabilityPercent())
                        .thenComparingInt(DiagnosisResultResponse.ConceptCard::getToConceptDepth)
                        .thenComparingInt(DiagnosisResultResponse.ConceptCard::getConceptId))
                .collect(Collectors.toList());

        List<DiagnosisResultResponse.ConceptCard> top = new ArrayList<>();
        for (DiagnosisResultResponse.ConceptCard card : sorted) {
            boolean high = "HIGH".equals(card.getUrgency());
            // 바닥 3 보충은 MID 까지만 (LOW 는 승격하지 않음 — PRD §4.1)
            boolean floorFill = top.size() < TOP_FLOOR && "MID".equals(card.getUrgency());
            // fail-soft: 등급 결측이면 정렬순 상위로 캡까지 채움 (§4.7 degrade)
            boolean degradedFill = !computation.servingOk();
            if ((high || floorFill || degradedFill) && top.size() < TOP_CAP) {
                top.add(card);
            }
        }
        List<DiagnosisResultResponse.ConceptCard> more = sorted.stream()
                .filter(c -> !top.contains(c))
                .collect(Collectors.toList());

        DiagnosisResultResponse.Headline headline = new DiagnosisResultResponse.Headline(
                computation.totalAsked(),
                computation.unknownConceptIds().size(),
                top.isEmpty() ? null : top.get(0).getConceptName());
        return new DiagnosisResultResponse(headline, top, more, computation.servingOk());
    }

    static String grade(Double percent) {
        if (percent == null) {
            return null;
        }
        if (percent < HIGH_CUT) {
            return "HIGH";
        }
        if (percent < MID_CUT) {
            return "MID";
        }
        return "LOW";
    }
}
