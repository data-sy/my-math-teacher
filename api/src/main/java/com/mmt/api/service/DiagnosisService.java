package com.mmt.api.service;

import com.mmt.api.dto.diagnosis.AnsweredConcept;
import com.mmt.api.dto.diagnosis.DiagnosisEntry;
import com.mmt.api.dto.diagnosis.DiagnosisFrontierResponse;
import com.mmt.api.dto.diagnosis.DiagnosisNextResponse;
import com.mmt.api.repository.concept.ConceptSummary;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.concept.KnowledgeEdge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M7 spec-01 자가진단 — 그래프 적응형 문답 (§4.3 서버 주도 stateless 순회, D1-A).
 *
 * 결정론 계약: 동일 (entry, answered[]) → 항상 동일한 next. 순회의 모든 순서는
 * concept_id 오름차순 + answered[] 입력 순서로만 정해진다 (spec-01 §8 검증 대상).
 *
 * 그래프 조회는 CTE 플래그(mmt.migration.use-mysql-cte-for-graph)와 무관하게
 * MySQL 직행이다 (ADR-0010 Decision-3). 선수 폐쇄 전체는 depth 10 CTE 가 아니라
 * 전체 간선 로드 + visited-set BFS 로 계산한다 — 실그래프 폐쇄 깊이 22 로
 * cte_max_recursion_depth=10 과 충돌(ERROR 3636 실측)하기 때문 (2026-07-13 design-review).
 */
@Service
public class DiagnosisService {

    private final JdbcTemplateConceptRepository conceptRepository;

    public DiagnosisService(JdbcTemplateConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    /** 시작 프론티어 (spec-01 §4.2, F-3). */
    @Transactional(readOnly = true)
    public DiagnosisFrontierResponse frontier(DiagnosisEntry entry) {
        List<ConceptSummary> frontier = resolveFrontier(entry);
        return new DiagnosisFrontierResponse(frontier.stream()
                .map(c -> new DiagnosisFrontierResponse.FrontierConcept(c.conceptId(), c.conceptName()))
                .collect(Collectors.toList()));
    }

    /** 적응형 순회 — 다음 질문 1개 (spec-01 §4.3). */
    @Transactional(readOnly = true)
    public DiagnosisNextResponse next(DiagnosisEntry entry, List<AnsweredConcept> answered) {
        List<AnsweredConcept> answers = answered == null ? List.of() : answered;
        Map<Integer, Boolean> answeredMap = toValidatedAnsweredMap(answers);

        // 간선 1회 로드 (3.4k 행) → 선수 인접 리스트. from=후수, to=선수.
        Map<Integer, List<Integer>> prerequisitesOf = buildPrerequisiteAdjacency(conceptRepository.findAllEdges());

        // 순회 규칙 3: "알아요" → 선수 폐쇄 전체 inferred-known (visited-set BFS).
        Set<Integer> inferredKnown = new HashSet<>();
        for (AnsweredConcept a : answers) {
            if (a.isKnown()) {
                bfsCollect(a.getConceptId(), prerequisitesOf, inferredKnown);
            }
        }

        // 순회 규칙 1·2: 시작 프론티어 + "몰라요" 직계 선수 push (입력 순서대로, 개념 내부는 id 오름차순).
        LinkedHashSet<Integer> sequence = new LinkedHashSet<>();
        for (ConceptSummary c : resolveFrontier(entry)) {
            sequence.add(c.conceptId());
        }
        for (AnsweredConcept a : answers) {
            if (!a.isKnown()) {
                prerequisitesOf.getOrDefault(a.getConceptId(), List.of()).stream()
                        .sorted()
                        .forEach(sequence::add);
            }
        }

        // 순회 규칙 4: (answered ∪ inferred-known) 제외 후 첫 개념.
        List<Integer> remaining = sequence.stream()
                .filter(id -> !answeredMap.containsKey(id) && !inferredKnown.contains(id))
                .collect(Collectors.toList());
        if (remaining.isEmpty()) {
            return DiagnosisNextResponse.done();
        }
        int nextId = remaining.get(0);
        ConceptSummary next = conceptRepository.findConceptSummaryById(nextId)
                .orElseThrow(() -> new IllegalStateException("knowledge_space 가 참조하는 개념이 없음: " + nextId));
        return new DiagnosisNextResponse(
                new DiagnosisNextResponse.NextConcept(next.conceptId(), next.conceptName(), next.description()),
                new DiagnosisNextResponse.Progress(answers.size(), remaining.size()),
                false);
    }

    /**
     * answered[] 검증 — 중복 conceptId 는 preview·귀속 양쪽에서 동일하게 400 (결정론
     * 계약을 에러 동작까지 확장: 귀속만 UNIQUE 제약으로 실패하는 비대칭 방지,
     * 2026-07-13 design-review Caution#2). 순서는 보존한다.
     */
    public Map<Integer, Boolean> toValidatedAnsweredMap(List<AnsweredConcept> answered) {
        Map<Integer, Boolean> map = new LinkedHashMap<>();
        for (AnsweredConcept a : answered) {
            if (map.putIfAbsent(a.getConceptId(), a.isKnown()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "answered 에 같은 conceptId 가 두 번 올 수 없습니다: " + a.getConceptId());
            }
        }
        return map;
    }

    private List<ConceptSummary> resolveFrontier(DiagnosisEntry entry) {
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entry 는 필수입니다.");
        }
        boolean full = "full".equals(entry.getScope());
        if (full && entry.getSchoolLevel() != null && !entry.getSchoolLevel().isBlank()) {
            return conceptRepository.findFrontierBySchoolLevel(entry.getSchoolLevel());
        }
        if (!full && entry.getChapterId() != null) {
            return conceptRepository.findFrontierByChapterId(entry.getChapterId());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "entry 는 chapterId 또는 scope=full+schoolLevel 중 하나여야 합니다.");
    }

    /** from(후수)→to(선수) 인접 리스트. 각 리스트는 concept_id 오름차순 (결정론). */
    static Map<Integer, List<Integer>> buildPrerequisiteAdjacency(List<KnowledgeEdge> edges) {
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (KnowledgeEdge e : edges) {
            adj.computeIfAbsent(e.fromConceptId(), k -> new ArrayList<>()).add(e.toConceptId());
        }
        adj.values().forEach(list -> list.sort(Integer::compareTo));
        return adj;
    }

    /** start 의 선수 폐쇄 전체(start 자신 제외)를 acc 에 누적. */
    static void bfsCollect(int start, Map<Integer, List<Integer>> adj, Set<Integer> acc) {
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        Set<Integer> visited = new HashSet<>();
        visited.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int prerequisite : adj.getOrDefault(cur, List.of())) {
                if (visited.add(prerequisite)) {
                    acc.add(prerequisite);
                    queue.add(prerequisite);
                }
            }
        }
    }
}
