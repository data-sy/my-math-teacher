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

    @Test
    @DisplayName("'알아요' → 선수 폐쇄 전체(깊이 무제한) inferred-known 으로 skip")
    void knownSkipsEntirePrerequisiteClosure() {
        // 30 알아요 → {20,10,5} 전부 제외, 31 몰라요 → {21,22} push.
        DiagnosisNextResponse res = service.next(chapter1(), List.of(
                new AnsweredConcept(30, true),
                new AnsweredConcept(31, false)));
        assertThat(res.getNext().getConceptId()).isEqualTo(21);
        // 21, 22 까지 답하면 소진 — 20/10/5 는 절대 질문되지 않음.
        DiagnosisNextResponse resDone = service.next(chapter1(), List.of(
                new AnsweredConcept(30, true),
                new AnsweredConcept(31, false),
                new AnsweredConcept(21, true),
                new AnsweredConcept(22, true)));
        assertThat(resDone.isDone()).isTrue();
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
}
