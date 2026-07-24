package com.mmt.api.service;

import com.mmt.api.dto.diagnosis.AnsweredConcept;
import com.mmt.api.dto.diagnosis.DiagnosisEntry;
import com.mmt.api.dto.diagnosis.DiagnosisNextResponse;
import com.mmt.api.repository.concept.ConceptSummary;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.concept.KnowledgeEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.mmt.api.exception.DiagnosisException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * spec-01 §4.3 적응형 순회 + §8 결정론 검증 (그래프는 mock 리포지토리의 합성 시드).
 *
 * 합성 그래프 (from=후수 → to=선수):
 *   30 → 20 → 10 → 5      (깊은 사슬: 30 의 선수 폐쇄 = {20,10,5})
 *   31 → 21
 *   31 → 22
 *   프론티어(단원 1) = [30, 31]
 */
class DiagnosisServiceTraversalTest {

    private JdbcTemplateConceptRepository repo;
    private DiagnosisService service;

    private static final List<KnowledgeEdge> EDGES = List.of(
            new KnowledgeEdge(30, 20),
            new KnowledgeEdge(20, 10),
            new KnowledgeEdge(10, 5),
            new KnowledgeEdge(31, 21),
            new KnowledgeEdge(31, 22));

    private static final List<ConceptSummary> FRONTIER = List.of(
            new ConceptSummary(30, "개념30", "설명30"),
            new ConceptSummary(31, "개념31", "설명31"));

    @BeforeEach
    void setUp() {
        repo = mock(JdbcTemplateConceptRepository.class);
        when(repo.findAllEdges()).thenReturn(EDGES);
        when(repo.findFrontierByChapterId(1)).thenReturn(FRONTIER);
        when(repo.findConceptSummaryById(anyInt())).thenAnswer(inv -> {
            int id = inv.getArgument(0);
            return Optional.of(new ConceptSummary(id, "개념" + id, "설명" + id));
        });
        service = new DiagnosisService(repo);
    }

    private static DiagnosisEntry chapter1() {
        DiagnosisEntry entry = new DiagnosisEntry();
        entry.setChapterId(1);
        return entry;
    }

    private static DiagnosisEntry chain1() {
        DiagnosisEntry entry = new DiagnosisEntry();
        entry.setChapterId(2);
        return entry;
    }

    /** len-사슬(1→2→…→len, 1 이 후수·len 이 최심 선수) 프론티어=[1] 인 격리 서비스. */
    private DiagnosisService chainService(int len, int min, int max, int threshold) {
        JdbcTemplateConceptRepository r = mock(JdbcTemplateConceptRepository.class);
        List<KnowledgeEdge> edges = new ArrayList<>();
        for (int i = 1; i < len; i++) {
            edges.add(new KnowledgeEdge(i, i + 1));
        }
        when(r.findAllEdges()).thenReturn(edges);
        when(r.findFrontierByChapterId(2))
                .thenReturn(List.of(new ConceptSummary(1, "개념1", "설명1")));
        when(r.findConceptSummaryById(anyInt())).thenAnswer(inv -> {
            int id = inv.getArgument(0);
            return Optional.of(new ConceptSummary(id, "개념" + id, "설명" + id));
        });
        return new DiagnosisService(r, min, max, threshold);
    }

    @Test
    @DisplayName("첫 질문 = 프론티어 규칙 B 1위 (blocked 동점 → 깊이 큰 30)")
    void firstQuestionIsFrontierHead() {
        DiagnosisNextResponse res = service.next(chapter1(), List.of());
        assertThat(res.isDone()).isFalse();
        // 프론티어 [30,31] 둘 다 blockedDescendants=0 → tie-break 깊이: 30(사슬 30→20→10→5=3) > 31(=1).
        assertThat(res.getNext().getConceptId()).isEqualTo(30);
    }

    @Test
    @DisplayName("규칙 B: '몰라요' drill-down 후 blockedDescendants 큰 개념 우선 (결함③)")
    void unknownDrillsDownByInformation() {
        DiagnosisNextResponse res = service.next(chapter1(),
                List.of(new AnsweredConcept(30, false)));
        // 30 몰라요 → 후보 {31, 20}. blocked(20)=1(30을 막음) > blocked(31)=0 → next=20.
        assertThat(res.getNext().getConceptId()).isEqualTo(20);
        // 30·31 몰라요 → 후보 {20,21,22}. blocked 전부 1 → tie-break 깊이: 20(20→10→5=2) > 21,22(=0).
        DiagnosisNextResponse res2 = service.next(chapter1(), List.of(
                new AnsweredConcept(30, false),
                new AnsweredConcept(31, false)));
        assertThat(res2.getNext().getConceptId()).isEqualTo(20);
    }

    // ── 규칙 C: skip-with-probe (ADR-0012, 결함②) ──────────────────────────────

    @Test
    @DisplayName("규칙 C: '알아요' 폐쇄에 검증 프로브가 남아 정보량 순으로 질문됨 (결함②)")
    void knownClosureLeavesVerificationProbe() {
        // 30 알아요 → 폐쇄 {20,10,5}(크기 3 < m=4) → 프로브 1개 = blocked 최대 5.
        // 31 몰라요 → drill-down {21,22}. primary = {21,22,5}. 프로브 5(blocked3)가 21,22(blocked1)보다 우선.
        DiagnosisNextResponse res = service.next(chapter1(), List.of(
                new AnsweredConcept(30, true),
                new AnsweredConcept(31, false)));
        assertThat(res.getNext().getConceptId()).isEqualTo(5);
    }

    @Test
    @DisplayName("규칙 C(D2 √n): 폐쇄 크기 ≥ m 이면 √n 개 프로브")
    void probeDensitySqrtForLargeClosure() {
        // 7-사슬(1→2→…→7), 1 알아요 → 폐쇄 {2..7} 크기 6 → floor(√6)=2 프로브.
        // 규칙 B(blocked desc, depth desc) 상위 2 = {4,5}. 첫 프로브 = 4(blocked3, depth3).
        DiagnosisService chain = chainService(7, 8, 20, 4);
        DiagnosisNextResponse res = chain.next(chain1(), List.of(new AnsweredConcept(1, true)));
        assertThat(res.getNext().getConceptId()).isEqualTo(4);
    }

    @Test
    @DisplayName("규칙 C(D3): 프로브 '몰라요' → 직계 선수 서브트리만 Undetermined 복원")
    void probeFailureRestoresSubtree() {
        // 7-사슬, 1 알아요 → 폐쇄 {2..7}, 프로브 {4,5}.
        // 프로브 5 '몰라요' + 프로브 4 '알아요' → D3 로 5 의 선수 서브트리 {6,7} 재개방.
        // 6 은 프로브가 아니며(잠정-앎이었음) drill-down 으로만 나온다 → 복원 증거.
        DiagnosisService chain = chainService(7, 8, 20, 4);
        DiagnosisNextResponse res = chain.next(chain1(), List.of(
                new AnsweredConcept(1, true),
                new AnsweredConcept(5, false),
                new AnsweredConcept(4, true)));
        assertThat(res.isDone()).isFalse();
        assertThat(res.getNext().getConceptId()).isEqualTo(6);
    }

    @Test
    @DisplayName("결정론: '알아요' 프로브 포함 시나리오도 간선 셔플 무관 동일 next")
    void deterministicWithProbes() {
        List<AnsweredConcept> answered = List.of(
                new AnsweredConcept(30, true),
                new AnsweredConcept(31, false));
        int expected = service.next(chapter1(), answered).getNext().getConceptId();
        for (int seed = 0; seed < 10; seed++) {
            List<KnowledgeEdge> shuffled = new ArrayList<>(EDGES);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            when(repo.findAllEdges()).thenReturn(shuffled);
            assertThat(service.next(chapter1(), answered).getNext().getConceptId())
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("결정론: 동일 answered-map → 항상 동일 next (간선 셔플 무관)")
    void deterministicRegardlessOfEdgeOrder() {
        List<AnsweredConcept> answered = List.of(
                new AnsweredConcept(30, false),
                new AnsweredConcept(31, false));
        int expected = service.next(chapter1(), answered).getNext().getConceptId();
        // 간선 순서를 뒤섞어도 (DB 반환 순서 비보장 가정) 결과 동일해야 한다.
        for (int seed = 0; seed < 10; seed++) {
            List<KnowledgeEdge> shuffled = new ArrayList<>(EDGES);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            when(repo.findAllEdges()).thenReturn(shuffled);
            assertThat(service.next(chapter1(), answered).getNext().getConceptId())
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("중복 conceptId → 400 (preview·귀속 대칭 계약)")
    void duplicateConceptIdRejected() {
        assertThatThrownBy(() -> service.next(chapter1(), List.of(
                new AnsweredConcept(30, false),
                new AnsweredConcept(30, true))))
                .isInstanceOf(DiagnosisException.class)
                .satisfies(e -> assertThat(((DiagnosisException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("사이클 데이터에서도 BFS 가 종료한다 (visited-set)")
    void bfsTerminatesOnCyclicData() {
        Set<Integer> acc = new java.util.HashSet<>();
        DiagnosisService.bfsCollect(1,
                DiagnosisService.buildPrerequisiteAdjacency(List.of(
                        new KnowledgeEdge(1, 2), new KnowledgeEdge(2, 1))),
                acc);
        assertThat(acc).containsExactly(2);
    }

    @Test
    @DisplayName("entry 미지정/모호 → 400")
    void invalidEntryRejected() {
        assertThatThrownBy(() -> service.next(new DiagnosisEntry(), List.of()))
                .isInstanceOf(DiagnosisException.class);
    }

    // ── 규칙 A: 최소 하한 K / 상한 N (ADR-0012, 결함①) ─────────────────────────

    @Test
    @DisplayName("규칙 A 하한: 전부 '알아요'라 후보가 비어도 K 미만이면 잠정-앎에서 검증 질문 (결함①)")
    void floorPreventsThinTermination() {
        // 프론티어 [30,31] 둘 다 알아요 → 후보 소진. 구 규칙이면 asked=2 에 done("약점 없음").
        // 규칙 A: 기본 K=8 미달이라 잠정-앎(closure) 에서 blocked 최대(5)부터 검증 질문.
        DiagnosisNextResponse res = service.next(chapter1(), List.of(
                new AnsweredConcept(30, true),
                new AnsweredConcept(31, true)));
        assertThat(res.isDone()).isFalse();
        assertThat(res.getNext().getConceptId()).isEqualTo(5);
    }

    @Test
    @DisplayName("규칙 A best-effort: 도달 가능 개념이 K보다 적으면 소진 시 done")
    void bestEffortDoneWhenGraphSmallerThanK() {
        // 합성 그래프 전체 7개(30,31,20,10,5,21,22)를 전부 답하면 K=8 미달이어도 종료.
        DiagnosisNextResponse res = service.next(chapter1(), List.of(
                new AnsweredConcept(30, true), new AnsweredConcept(31, true),
                new AnsweredConcept(20, true), new AnsweredConcept(10, true),
                new AnsweredConcept(5, true), new AnsweredConcept(21, true),
                new AnsweredConcept(22, true)));
        assertThat(res.isDone()).isTrue();
    }

    @Test
    @DisplayName("규칙 A 상한: asked = N 도달 시 후보가 남아도 강제 done")
    void hardCapForcesDone() {
        DiagnosisService capped = new DiagnosisService(repo, 1, 3); // K=1, N=3
        DiagnosisNextResponse res = capped.next(chapter1(), List.of(
                new AnsweredConcept(30, false),
                new AnsweredConcept(31, false),
                new AnsweredConcept(20, false))); // 몰라요 3개 → 후보 더 있음에도 N=3 캡
        assertThat(res.isDone()).isTrue();
    }
}
