package com.mmt.api.service;

import com.mmt.api.domain.diagnosis.LearningQueue;
import com.mmt.api.domain.diagnosis.LearningQueueItem;
import com.mmt.api.dto.diagnosis.DiagnosisResultResponse;
import com.mmt.api.dto.diagnosis.LearningQueueResponse;
import com.mmt.api.repository.concept.ConceptCardMeta;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.diagnosis.LearningQueueItemRepository;
import com.mmt.api.repository.diagnosis.LearningQueueRepository;
import com.mmt.api.repository.users.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mmt.api.exception.DiagnosisException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M7 spec-01 §4.6 통합 학습 큐 (F-4).
 *
 * 정렬 = 위상순 지배 (선수 먼저 — 계단 불변식). 시급도는 Kahn 진입차수-0 후보가 복수일 때의
 * 진입 우선순위로만 쓴다 (순수 시급도순 정렬 금지 — PRD §4.3 "3단 건너뛰고 5단부터" 방지).
 * 동률·결측은 conceptId 오름차순 — 전 단계 결정론.
 */
@Service
public class LearningQueueService {

    private static final Logger log = LoggerFactory.getLogger(LearningQueueService.class);

    private final DiagnosisAnalysisService diagnosisAnalysisService;
    private final JdbcTemplateConceptRepository conceptRepository;
    private final LearningQueueRepository queueRepository;
    private final LearningQueueItemRepository queueItemRepository;
    private final UsersRepository usersRepository;
    private final com.mmt.api.repository.diagnosis.SelfReportAnswerRepository selfReportAnswerRepository;

    public LearningQueueService(DiagnosisAnalysisService diagnosisAnalysisService,
                                JdbcTemplateConceptRepository conceptRepository,
                                LearningQueueRepository queueRepository,
                                LearningQueueItemRepository queueItemRepository,
                                UsersRepository usersRepository,
                                com.mmt.api.repository.diagnosis.SelfReportAnswerRepository selfReportAnswerRepository) {
        this.diagnosisAnalysisService = diagnosisAnalysisService;
        this.conceptRepository = conceptRepository;
        this.queueRepository = queueRepository;
        this.queueItemRepository = queueItemRepository;
        this.usersRepository = usersRepository;
        this.selfReportAnswerRepository = selfReportAnswerRepository;
    }

    /** 큐 생성 (§4.6 병합 알고리즘 — 1회 계산·영속). 재진단 시 새 큐, 구 큐 보관. */
    @Transactional
    public LearningQueueResponse create(String userEmail, Long userTestId) {
        if (userTestId == null) {
            throw new DiagnosisException(HttpStatus.BAD_REQUEST, "userTestId 는 필수입니다.");
        }
        Long userId = resolveUserId(userEmail);
        // 소유권 포함 결과 재조회 — top-N 카드가 큐의 목표(goal) 집합
        DiagnosisResultResponse result = diagnosisAnalysisService.getResult(userTestId, userEmail);

        // 1. 노드 집합 = top-N 카드 ∪ 각각 선수 폐쇄(depth≤3, §4.4 확장과 동일 소스)
        //    - known=true 답변·inferred-known 제외 (이미 아는 계단은 안 밟음)
        Map<Integer, List<Integer>> adjacency = DiagnosisService.buildPrerequisiteAdjacency(
                conceptRepository.findAllEdges());
        Set<Integer> knownTrue = selfReportAnswerRepository
                .findByUserTestIdOrderBySelfReportAnswerIdAsc(userTestId).stream()
                .filter(com.mmt.api.domain.diagnosis.SelfReportAnswer::getKnown)
                .map(com.mmt.api.domain.diagnosis.SelfReportAnswer::getConceptId)
                .collect(Collectors.toSet());
        Set<Integer> inferredKnown = new HashSet<>(knownTrue);
        for (int known : knownTrue) {
            DiagnosisService.bfsCollect(known, adjacency, inferredKnown);
        }

        Set<Integer> nodes = new LinkedHashSet<>();
        Map<Integer, Double> branchUrgency = new HashMap<>();
        for (DiagnosisResultResponse.ConceptCard card : result.getCards()) {
            double urgency = card.getProbabilityPercent() == null
                    ? Double.MAX_VALUE : card.getProbabilityPercent();
            conceptRepository.findPrerequisitesWithDepth(card.getConceptId(),
                    DiagnosisAnalysisService.EXPANSION_DEPTH).forEach(cd -> {
                if (!inferredKnown.contains(cd.conceptId())) {
                    nodes.add(cd.conceptId());
                    branchUrgency.merge(cd.conceptId(), urgency, Double::min);
                }
            });
        }

        // 2~4. 노드 집합 내부 간선으로 DAG → 위상순 지배 정렬 (사이클은 degrade)
        List<Integer> order = topologicalOrder(nodes, adjacency, branchUrgency);

        LearningQueue queue = queueRepository.save(new LearningQueue(userId, userTestId));
        List<LearningQueueItem> items = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            items.add(queueItemRepository.save(
                    new LearningQueueItem(queue.getQueueId(), i + 1, order.get(i))));
        }
        return toResponse(queue, items);
    }

    /** 이어가기: 활성 큐(최신) + 파생 현재 위치. 큐 없으면 404. */
    @Transactional(readOnly = true)
    public LearningQueueResponse getMyQueue(String userEmail) {
        Long userId = resolveUserId(userEmail);
        LearningQueue queue = queueRepository.findTopByUserIdOrderByQueueIdDesc(userId)
                .orElseThrow(() -> new DiagnosisException(HttpStatus.NOT_FOUND, "학습 큐가 없습니다."));
        return toResponse(queue, queueItemRepository.findByQueueIdOrderByPositionAsc(queue.getQueueId()));
    }

    /** 완료 self-mark (멱등 — 이미 done 이면 그대로). 앱은 외부 학습을 검증하지 않음. */
    @Transactional
    public LearningQueueResponse markDone(String userEmail, Long queueId, Long queueItemId) {
        Long userId = resolveUserId(userEmail);
        LearningQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new DiagnosisException(HttpStatus.NOT_FOUND, "학습 큐가 없습니다."));
        if (!queue.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인의 학습 큐만 갱신할 수 있습니다.");
        }
        LearningQueueItem item = queueItemRepository.findByQueueItemIdAndQueueId(queueItemId, queueId)
                .orElseThrow(() -> new DiagnosisException(HttpStatus.NOT_FOUND, "큐 항목이 없습니다."));
        if (!Boolean.TRUE.equals(item.getDone())) {
            item.setDone(Boolean.TRUE);
            item.setDoneAt(LocalDateTime.now());
            queueItemRepository.save(item);
        }
        return toResponse(queue, queueItemRepository.findByQueueIdOrderByPositionAsc(queueId));
    }

    /**
     * Kahn 위상정렬 — 진입차수-0 후보 복수 시 (카드 가지 최고 시급도 = 최저 percent,
     * 동률은 conceptId 오름차순). 사이클 발견 시 잔여 노드 중 우선순위 최상을 강제 진입
     * (= 해당 역방향 간선 drop) + 경고 로그 — 큐 생성이 죽지 않게 degrade (§4.6-4).
     */
    static List<Integer> topologicalOrder(Set<Integer> nodes,
                                          Map<Integer, List<Integer>> prerequisitesOf,
                                          Map<Integer, Double> branchUrgency) {
        // 노드별 잔여 선수(집합 내) 카운트 + 역인덱스(선수 → 후수들)
        Map<Integer, Integer> remainingPrereqs = new HashMap<>();
        Map<Integer, List<Integer>> dependentsOf = new HashMap<>();
        for (int node : nodes) {
            int count = 0;
            for (int prerequisite : prerequisitesOf.getOrDefault(node, List.of())) {
                if (nodes.contains(prerequisite)) {
                    count++;
                    dependentsOf.computeIfAbsent(prerequisite, k -> new ArrayList<>()).add(node);
                }
            }
            remainingPrereqs.put(node, count);
        }

        java.util.Comparator<Integer> priority = java.util.Comparator
                .<Integer>comparingDouble(id -> branchUrgency.getOrDefault(id, Double.MAX_VALUE))
                .thenComparingInt(id -> id);
        PriorityQueue<Integer> ready = new PriorityQueue<>(priority);
        remainingPrereqs.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });

        List<Integer> order = new ArrayList<>();
        Set<Integer> placed = new HashSet<>();
        while (order.size() < nodes.size()) {
            Integer next = ready.poll();
            if (next == null) {
                // 사이클: 잔여 노드 중 우선순위 최상을 강제 진입 (간선 drop 상당)
                next = nodes.stream().filter(n -> !placed.contains(n)).min(priority).orElseThrow();
                log.warn("learning-queue: knowledge_space 사이클 감지 — 개념 {} 강제 진입 (간선 drop degrade)", next);
            }
            if (!placed.add(next)) {
                continue;
            }
            order.add(next);
            for (int dependent : dependentsOf.getOrDefault(next, List.of())) {
                int remain = remainingPrereqs.merge(dependent, -1, Integer::sum);
                if (remain == 0 && !placed.contains(dependent)) {
                    ready.add(dependent);
                }
            }
        }
        return order;
    }

    private Long resolveUserId(String userEmail) {
        return usersRepository.findUserIdByUserEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("인증 정보를 확인할 수 없습니다."));
    }

    private LearningQueueResponse toResponse(LearningQueue queue, List<LearningQueueItem> items) {
        Map<Integer, ConceptCardMeta> metas = conceptRepository.findConceptCardMetas(
                        items.stream().map(LearningQueueItem::getConceptId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(ConceptCardMeta::conceptId, m -> m));
        Long currentItemId = items.stream()
                .filter(i -> !Boolean.TRUE.equals(i.getDone()))
                .map(LearningQueueItem::getQueueItemId)
                .findFirst().orElse(null);
        List<LearningQueueResponse.QueueItem> dto = items.stream()
                .map(i -> new LearningQueueResponse.QueueItem(
                        i.getQueueItemId(),
                        i.getPosition(),
                        i.getConceptId(),
                        metas.containsKey(i.getConceptId()) ? metas.get(i.getConceptId()).conceptName() : null,
                        Boolean.TRUE.equals(i.getDone()),
                        i.getQueueItemId().equals(currentItemId)))
                .collect(Collectors.toList());
        return new LearningQueueResponse(queue.getQueueId(), queue.getUserTestId(), dto);
    }
}
