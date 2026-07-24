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
import com.mmt.api.exception.DiagnosisException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 결정론적 KST 코어 (ADR-0012): 확률층 없이 DAG 구조 계산만으로 결함 3건을 닫는다.
 * 규칙 B(순서) = blockedDescendants 내림차순 → DAG 깊이 내림차순 → conceptId 오름차순.
 * 규칙 A(하한/상한)·규칙 C(skip-with-probe)는 후속 커밋에서 얹는다.
 *
 * 결정론 계약: 동일 (entry, answered[]) → 항상 동일한 next. 순회의 모든 순서는
 * 구조 순위(blockedDescendants → 깊이 → conceptId)로 확정한다 — HashSet/HashMap
 * 순회 순서에 의존하면 preview≠귀속(계약 위반, spec-01 §8).
 *
 * 그래프 조회는 CTE 플래그(mmt.migration.use-mysql-cte-for-graph)와 무관하게
 * MySQL 직행이다 (ADR-0010 Decision-3). 선수 폐쇄·blockedDescendants·깊이는 전체
 * 간선 1회 로드 후 앱 계층에서 계산한다 — 폐쇄는 visited-set BFS(실그래프 깊이 22 로
 * cte_max_recursion_depth=10 충돌, 2026-07-13 design-review), blockedDescendants 는
 * §4.5 역방향 CTE 와 동치인 depth 3 역방향 BFS(요청당 후보 N 개 CTE 왕복 회피).
 */
@Service
public class DiagnosisService {

    /** blockedDescendants 역방향 도달 깊이 상한 (spec-01 §4.5 CTE 와 동치). */
    static final int BLOCKED_DEPTH = 3;

    /** 규칙 A 정책 상수 (ADR-0012 Decision-4, D1). 실데이터 축적 후 튜닝 대상. */
    static final int DEFAULT_MIN_QUESTIONS = 8;   // K — 최소 질문 하한 (결함① 방지)
    static final int DEFAULT_MAX_QUESTIONS = 20;  // N — 3분 하드캡

    private final JdbcTemplateConceptRepository conceptRepository;
    private final int minQuestions;
    private final int maxQuestions;

    public DiagnosisService(JdbcTemplateConceptRepository conceptRepository) {
        this(conceptRepository, DEFAULT_MIN_QUESTIONS, DEFAULT_MAX_QUESTIONS);
    }

    /** 테스트용 — 작은 합성 그래프에서 하한/상한 동작을 검증할 수 있게 K·N 을 주입. */
    DiagnosisService(JdbcTemplateConceptRepository conceptRepository, int minQuestions, int maxQuestions) {
        this.conceptRepository = conceptRepository;
        this.minQuestions = minQuestions;
        this.maxQuestions = maxQuestions;
    }

    /** 시작 프론티어 (spec-01 §4.2, F-3). */
    @Transactional(readOnly = true)
    public DiagnosisFrontierResponse frontier(DiagnosisEntry entry) {
        List<ConceptSummary> frontier = resolveFrontier(entry);
        return new DiagnosisFrontierResponse(frontier.stream()
                .map(c -> new DiagnosisFrontierResponse.FrontierConcept(c.conceptId(), c.conceptName()))
                .collect(Collectors.toList()));
    }

    /** 적응형 순회 — 다음 질문 1개 (spec-01 §4.3, ADR-0012 규칙 B). */
    @Transactional(readOnly = true)
    public DiagnosisNextResponse next(DiagnosisEntry entry, List<AnsweredConcept> answered) {
        List<AnsweredConcept> answers = answered == null ? List.of() : answered;
        Map<Integer, Boolean> answeredMap = toValidatedAnsweredMap(answers);
        int asked = answers.size();

        // 규칙 A 상한: 3분 하드캡 도달 시 강제 종료.
        if (asked >= maxQuestions) {
            return DiagnosisNextResponse.done();
        }

        // 간선 1회 로드 (3.4k 행) → 선수 인접(from=후수→to=선수) + 역방향 인접(to=선수→from=후수).
        List<KnowledgeEdge> edges = conceptRepository.findAllEdges();
        Map<Integer, List<Integer>> prerequisitesOf = buildPrerequisiteAdjacency(edges);
        Map<Integer, List<Integer>> blockersOf = buildBlockerAdjacency(edges);

        // 순회 규칙 3: "알아요" → 선수 폐쇄 전체 inferred-known (visited-set BFS).
        Set<Integer> inferredKnown = new HashSet<>();
        for (AnsweredConcept a : answers) {
            if (a.isKnown()) {
                bfsCollect(a.getConceptId(), prerequisitesOf, inferredKnown);
            }
        }

        // 순회 규칙 1·2: 시작 프론티어 + "몰라요" 직계 선수 → 후보 집합.
        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        for (ConceptSummary c : resolveFrontier(entry)) {
            candidates.add(c.conceptId());
        }
        for (AnsweredConcept a : answers) {
            if (!a.isKnown()) {
                candidates.addAll(prerequisitesOf.getOrDefault(a.getConceptId(), List.of()));
            }
        }

        // 규칙 4: Undetermined(= answered·inferred-known 제외) 후보 중 규칙 B 순서 1위.
        Map<Integer, Integer> blockedMemo = new HashMap<>();
        Map<Integer, Integer> depthMemo = new HashMap<>();
        List<Integer> ordered = candidates.stream()
                .filter(id -> !answeredMap.containsKey(id) && !inferredKnown.contains(id))
                .sorted(candidateOrder(blockersOf, prerequisitesOf, blockedMemo, depthMemo))
                .collect(Collectors.toList());
        if (!ordered.isEmpty()) {
            return buildNext(ordered.get(0), asked, ordered.size());
        }

        // 규칙 A 하한: 후보 소진이어도 asked < K 면 잠정-앎(inferred-known)에서 검증 질문을
        // 더 뽑아 진단이 너무 얇게 끝나지 않게 한다 (결함① 방지). 잠정-앎도 소진되면
        // (그래프가 K 보다 작은 극단) best-effort 로 종료한다.
        if (asked >= minQuestions) {
            return DiagnosisNextResponse.done();
        }
        List<Integer> floorFill = inferredKnown.stream()
                .filter(id -> !answeredMap.containsKey(id))
                .sorted(candidateOrder(blockersOf, prerequisitesOf, blockedMemo, depthMemo))
                .collect(Collectors.toList());
        if (floorFill.isEmpty()) {
            return DiagnosisNextResponse.done();
        }
        return buildNext(floorFill.get(0), asked, floorFill.size());
    }

    /** next 응답 조립 — concept 메타 직조회 (규칙 5: description 소스 불변). */
    private DiagnosisNextResponse buildNext(int conceptId, int asked, int estimatedRemaining) {
        ConceptSummary c = conceptRepository.findConceptSummaryById(conceptId)
                .orElseThrow(() -> new IllegalStateException("knowledge_space 가 참조하는 개념이 없음: " + conceptId));
        return new DiagnosisNextResponse(
                new DiagnosisNextResponse.NextConcept(c.conceptId(), c.conceptName(), c.description()),
                new DiagnosisNextResponse.Progress(asked, estimatedRemaining),
                false);
    }

    /**
     * 규칙 B 순서 (ADR-0012): blockedDescendants 내림차순 → DAG 깊이 내림차순 →
     * conceptId 오름차순. 전부 구조 순위라 결정론(간선 셔플·해시맵 순회 무관).
     */
    private Comparator<Integer> candidateOrder(Map<Integer, List<Integer>> blockersOf,
                                               Map<Integer, List<Integer>> prerequisitesOf,
                                               Map<Integer, Integer> blockedMemo,
                                               Map<Integer, Integer> depthMemo) {
        Comparator<Integer> byBlocked =
                Comparator.comparingInt((Integer c) -> countBlockedDescendants(c, blockersOf, blockedMemo));
        Comparator<Integer> byDepth =
                Comparator.comparingInt((Integer c) -> longestPrerequisiteDepth(c, prerequisitesOf, depthMemo));
        return byBlocked.reversed()
                .thenComparing(byDepth.reversed())
                .thenComparingInt(c -> c);
    }

    /**
     * blockedDescendants: "이 개념을 모르면 위로 몇 개가 막히는가" = depth 3 역방향
     * 도달 개념 수(자기 제외). spec-01 §4.5 countBlockedDescendants CTE 와 동치를
     * 이미 로드한 간선에서 인메모리 계산 (요청당 CTE 왕복 회피). 사이클 면역(visited-set).
     */
    private int countBlockedDescendants(int start, Map<Integer, List<Integer>> blockersOf,
                                        Map<Integer, Integer> memo) {
        Integer cached = memo.get(start);
        if (cached != null) {
            return cached;
        }
        Set<Integer> visited = new HashSet<>();
        visited.add(start);
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{start, 0});
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            if (cur[1] >= BLOCKED_DEPTH) {
                continue;
            }
            for (int blocked : blockersOf.getOrDefault(cur[0], List.of())) {
                if (visited.add(blocked)) {
                    queue.add(new int[]{blocked, cur[1] + 1});
                }
            }
        }
        int count = visited.size() - 1;
        memo.put(start, count);
        return count;
    }

    /** DAG 깊이(tie-break) = 가장 긴 선수 사슬 길이. 사이클 back-edge 는 0 기여(무한 방지). */
    private int longestPrerequisiteDepth(int concept, Map<Integer, List<Integer>> prerequisitesOf,
                                         Map<Integer, Integer> memo) {
        return depthDfs(concept, prerequisitesOf, memo, new HashSet<>());
    }

    private int depthDfs(int concept, Map<Integer, List<Integer>> adj,
                         Map<Integer, Integer> memo, Set<Integer> onStack) {
        Integer cached = memo.get(concept);
        if (cached != null) {
            return cached;
        }
        if (!onStack.add(concept)) {
            return 0; // 사이클 back-edge — 사슬 연장 중단
        }
        int best = 0;
        for (int prerequisite : adj.getOrDefault(concept, List.of())) {
            best = Math.max(best, 1 + depthDfs(prerequisite, adj, memo, onStack));
        }
        onStack.remove(concept);
        memo.put(concept, best);
        return best;
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
                throw new DiagnosisException(HttpStatus.BAD_REQUEST,
                        "answered 에 같은 conceptId 가 두 번 올 수 없습니다: " + a.getConceptId());
            }
        }
        return map;
    }

    private List<ConceptSummary> resolveFrontier(DiagnosisEntry entry) {
        if (entry == null) {
            throw new DiagnosisException(HttpStatus.BAD_REQUEST, "entry 는 필수입니다.");
        }
        boolean full = "full".equals(entry.getScope());
        if (full && entry.getSchoolLevel() != null && !entry.getSchoolLevel().isBlank()) {
            return conceptRepository.findFrontierBySchoolLevel(entry.getSchoolLevel());
        }
        if (!full && entry.getChapterId() != null) {
            return conceptRepository.findFrontierByChapterId(entry.getChapterId());
        }
        throw new DiagnosisException(HttpStatus.BAD_REQUEST,
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

    /** 역방향 to(선수)→from(후수) 인접 리스트 — blockedDescendants 계산용. id 오름차순. */
    static Map<Integer, List<Integer>> buildBlockerAdjacency(List<KnowledgeEdge> edges) {
        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (KnowledgeEdge e : edges) {
            adj.computeIfAbsent(e.toConceptId(), k -> new ArrayList<>()).add(e.fromConceptId());
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
