package com.mmt.api.service;

import com.mmt.api.dto.diagnosis.DiagnosisResultResponse;
import com.mmt.api.dto.diagnosis.ExternalLink;
import com.mmt.api.repository.concept.ConceptCardMeta;
import com.mmt.api.service.DiagnosisAnalysisService.DiagnosisComputation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec-01 §4.4 결과 계약·§4.5 등급 컷(S4)·PRD §4.1 top-N 규칙 검증.
 */
class DiagnosisResultAssemblerTest {

    @Test
    @DisplayName("등급 컷: p<40 HIGH · 40≤p<65 MID · p≥65 LOW, 결측 null")
    void gradeCuts() {
        assertThat(DiagnosisResultAssembler.grade(39.999)).isEqualTo("HIGH");
        assertThat(DiagnosisResultAssembler.grade(40.0)).isEqualTo("MID");
        assertThat(DiagnosisResultAssembler.grade(64.999)).isEqualTo("MID");
        assertThat(DiagnosisResultAssembler.grade(65.0)).isEqualTo("LOW");
        assertThat(DiagnosisResultAssembler.grade(null)).isNull();
    }

    @Test
    @DisplayName("top-N: HIGH 전부 + 캡 5 초과분은 more 로")
    void highAllWithCapFive() {
        // HIGH 6개 (10~35), LOW 1개 (90)
        DiagnosisResultResponse res = assemble(Map.of(
                1, 10.0, 2, 15.0, 3, 20.0, 4, 25.0, 5, 30.0, 6, 35.0, 7, 90.0));
        assertThat(res.getCards()).hasSize(5);
        assertThat(res.getCards()).extracting(DiagnosisResultResponse.ConceptCard::getConceptId)
                .containsExactly(1, 2, 3, 4, 5); // percent 오름차순
        assertThat(res.getMore()).extracting(DiagnosisResultResponse.ConceptCard::getConceptId)
                .containsExactly(6, 7);
    }

    @Test
    @DisplayName("top-N 바닥 3: HIGH 부족 시 MID 시급도순 보충, LOW 는 승격 안 함")
    void floorThreeFillsFromMidOnly() {
        // HIGH 1개, MID 1개, LOW 2개 → top = HIGH+MID 2개 (LOW 미승격이라 바닥 3 미달 허용)
        DiagnosisResultResponse res = assemble(Map.of(
                1, 10.0, 2, 50.0, 3, 70.0, 4, 80.0));
        assertThat(res.getCards()).extracting(DiagnosisResultResponse.ConceptCard::getConceptId)
                .containsExactly(1, 2);
        assertThat(res.getCards()).extracting(DiagnosisResultResponse.ConceptCard::getUrgency)
                .containsExactly("HIGH", "MID");
        assertThat(res.getMore()).extracting(DiagnosisResultResponse.ConceptCard::getUrgency)
                .containsExactly("LOW", "LOW");
    }

    @Test
    @DisplayName("fail-soft: 서빙 결측 → urgency null, 정렬 = depth·conceptId, urgencyAvailable=false")
    void degradedFillsWithoutGrades() {
        Map<Integer, Integer> depths = new LinkedHashMap<>();
        depths.put(9, 1);
        depths.put(3, 0);
        depths.put(5, 0);
        DiagnosisComputation c = new DiagnosisComputation(4, List.of(3, 5), depths, Map.of(), false);
        DiagnosisResultResponse res = DiagnosisResultAssembler.assemble(
                c, metas(depths.keySet()), Map.of(), Map.of());
        assertThat(res.isUrgencyAvailable()).isFalse();
        // 정렬: percent 결측 → depth asc, conceptId asc = [3(d0), 5(d0), 9(d1)]
        assertThat(res.getCards()).extracting(DiagnosisResultResponse.ConceptCard::getConceptId)
                .containsExactly(3, 5, 9);
        assertThat(res.getCards()).extracting(DiagnosisResultResponse.ConceptCard::getUrgency)
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("headline: totalAsked·weakCount·top 1위 개념명, 링크 없으면 빈 배열")
    void headlineAndLinksReservation() {
        DiagnosisResultResponse res = assemble(Map.of(1, 10.0, 2, 90.0));
        assertThat(res.getHeadline().getTotalAsked()).isEqualTo(4);
        assertThat(res.getHeadline().getWeakCount()).isEqualTo(1); // 합성 depth 규칙상 depth0 = 홀수 id 1개
        assertThat(res.getHeadline().getTopConceptName()).isEqualTo("개념1");
        assertThat(res.getCards().get(0).getLinks()).isEmpty();
        assertThat(res.getCards().get(0).getUrgencyBasis().getBlockedDescendants()).isEqualTo(11);
    }

    @Test
    @DisplayName("M8 links: 개념별 부착 + 상한 3 절단 + 결측 개념은 빈 배열 (§2.2 결측 허용)")
    void linksAttachedWithCapAndMissingAllowed() {
        // 개념 1 = 4개(상한 초과) · 개념 2 = 시드 없음
        DiagnosisResultResponse res = assemble(Map.of(1, 10.0, 2, 20.0),
                Map.of(1, List.of(
                        new ExternalLink("무료 강의 (EBS)", "https://e.kr/1", "EBS"),
                        new ExternalLink("개념 정리", "https://e.kr/2", "EBS"),
                        new ExternalLink("연습 문제", "https://e.kr/3", "EBS"),
                        new ExternalLink("넘치는 4번째", "https://e.kr/4", "EBS"))));

        DiagnosisResultResponse.ConceptCard withLinks = res.getCards().get(0);
        assertThat(withLinks.getConceptId()).isEqualTo(1);
        assertThat(withLinks.getLinks()).hasSize(DiagnosisResultAssembler.LINK_CAP);
        // 절단은 뒤에서 — 입력 순서(display_order) 보존
        assertThat(withLinks.getLinks()).extracting(ExternalLink::getUrl)
                .containsExactly("https://e.kr/1", "https://e.kr/2", "https://e.kr/3");
        assertThat(withLinks.getLinks().get(0).getTitle()).isEqualTo("무료 강의 (EBS)");
        assertThat(withLinks.getLinks().get(0).getProvider()).isEqualTo("EBS");

        // 링크 0개 개념은 빈 배열 — "준비 중" 같은 자리표시 금지, UI 가 섹션을 생략한다
        assertThat(res.getCards().get(1).getConceptId()).isEqualTo(2);
        assertThat(res.getCards().get(1).getLinks()).isEmpty();
    }

    @Test
    @DisplayName("약점 없음: cards·more 빈 배열 + weakCount 0 (에러 아님)")
    void noWeaknessIsNormalResult() {
        DiagnosisResultResponse res = DiagnosisResultAssembler.assemble(
                DiagnosisComputation.noWeakness(5), Map.of(), Map.of(), Map.of());
        assertThat(res.getCards()).isEmpty();
        assertThat(res.getMore()).isEmpty();
        assertThat(res.getHeadline().getWeakCount()).isZero();
        assertThat(res.getHeadline().getTopConceptName()).isNull();
        assertThat(res.isUrgencyAvailable()).isTrue();
    }

    // ---- helpers ----

    /** percent 맵으로 computation 구성 (depth: conceptId 홀수=0, 짝수=1 — 단순 합성). */
    private static DiagnosisResultResponse assemble(Map<Integer, Double> percents) {
        return assemble(percents, Map.of());
    }

    private static DiagnosisResultResponse assemble(Map<Integer, Double> percents,
                                                    Map<Integer, List<ExternalLink>> links) {
        Map<Integer, Integer> depths = new LinkedHashMap<>();
        percents.keySet().stream().sorted().forEach(id -> depths.put(id, id % 2 == 0 ? 1 : 0));
        List<Integer> unknown = depths.entrySet().stream()
                .filter(e -> e.getValue() == 0).map(Map.Entry::getKey).collect(Collectors.toList());
        DiagnosisComputation c = new DiagnosisComputation(4, unknown, depths, percents, true);
        Map<Integer, Integer> blocked = depths.keySet().stream()
                .collect(Collectors.toMap(id -> id, id -> id + 10));
        return DiagnosisResultAssembler.assemble(c, metas(depths.keySet()), blocked, links);
    }

    private static Map<Integer, ConceptCardMeta> metas(java.util.Collection<Integer> ids) {
        return ids.stream().collect(Collectors.toMap(id -> id,
                id -> new ConceptCardMeta(id, "개념" + id, "중학교-1-1", "대-중-소")));
    }
}
