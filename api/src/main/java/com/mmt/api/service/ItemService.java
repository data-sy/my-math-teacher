package com.mmt.api.service;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.Item;
import com.mmt.api.domain.Probability;
import com.mmt.api.dto.item.PersonalItemConverter;
import com.mmt.api.dto.item.PersonalItemsResponse;
import com.mmt.api.repository.answer.AnswerRepository;
import com.mmt.api.repository.item.ItemRepository;
import com.mmt.api.repository.probability.ProbabilityRepository;
import com.mmt.api.repository.userTest.UserTestRepository;
import com.mmt.api.repository.users.UsersRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ItemService {
    private final AnswerService answerService;
    private final AnswerRepository answerRepository;
    private final ProbabilityRepository probabilityRepository;
    private final ItemRepository itemRepository;
    private final UsersRepository usersRepository;
    private final UserTestRepository userTestRepository;

    public ItemService(AnswerService answerService, AnswerRepository answerRepository, ProbabilityRepository probabilityRepository, ItemRepository itemRepository,
                       UsersRepository usersRepository, UserTestRepository userTestRepository) {
        this.answerService = answerService;
        this.answerRepository = answerRepository;
        this.probabilityRepository = probabilityRepository;
        this.itemRepository = itemRepository;
        this.usersRepository = usersRepository;
        this.userTestRepository = userTestRepository;
    }

    // Scope B 문항 수 경계 (PersonalView InputNumber 6~30 과 일치)
    private static final int COUNT_MIN = 6;
    private static final int COUNT_MAX = 30;
    private static final int COUNT_DEFAULT = 10;

    /**
     * 레거시(Scope A) 진입점 — 조건 파라미터 없는 호출. 하위호환을 위해 유지한다.
     */
    public List<PersonalItemsResponse> findPersonalItems(Long userTestId, String userEmail) {
        return findPersonalItems(userTestId, userEmail, null, null, null);
    }

    /**
     * 맞춤학습지 조건부 출제 (Scope B).
     * spec: docs/specs/product/spec-01-personalview-conditional-items-scope-b.md
     *
     * @param category 맞춤 유형. {@code "wrong"}=오답 개념 자체(depth 0), {@code "prerequisite"}=선수지식(depth 1~2).
     *                 {@code null} 이면 레거시 Scope A 동작(depth≤2 혼합, 재출제 없음, count 무제한).
     * @param reExam   재출제. {@code "nothing"}=신규 문항만, {@code "wrong"}=원래 오답 문항+신규, {@code "all"}=원래 응시 문항 전체+신규.
     * @param count    신규 문항 상한(6~30 clamp). 재출제 원본 문항은 count 와 무관하게 추가된다.
     */
    public List<PersonalItemsResponse> findPersonalItems(Long userTestId, String userEmail,
                                                         String category, String reExam, Integer count) {
        // (#2) IDOR 방어: 요청한 userTestId 가 인증된 사용자 본인 소유인지 확인한다.
        // 위반 시 AccessDeniedException → jwtAccessDeniedHandler 가 403 으로 응답.
        Long userId = usersRepository.findUserIdByUserEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("인증 정보를 확인할 수 없습니다."));
        if (!userTestRepository.existsByUserTestIdAndUserId(userTestId, userId)) {
            throw new AccessDeniedException("본인의 학습 기록만 조회할 수 있습니다.");
        }

        // 조건 미지정 = 레거시 Scope A 경로 (롤백 안전판)
        if (category == null) {
            return findLegacyPersonalItems(userTestId);
        }

        int cappedCount = clampCount(count);
        // item_id 기준 dedup + 삽입 순서 보존(재출제 원본 먼저 → 신규 채우기). 원본 우선.
        Map<Long, PersonalItemsResponse> byItemId = new LinkedHashMap<>();

        // 1) 재출제 원본 문항 — 선택 시 전부 포함. count 에 산입(원본이 count 이상이면 초과 허용).
        if ("wrong".equals(reExam) || "all".equals(reExam)) {
            boolean onlyWrong = "wrong".equals(reExam);
            for (Item original : itemRepository.findOriginalItemsByUserTestId(userTestId, onlyWrong)) {
                byItemId.putIfAbsent(original.getItemId(), PersonalItemConverter.convertToPersonalItemsResponse(original));
            }
        }

        // 2) 남은 자리(count - 원본수)를 D1 우선순위 tier 순서로 채운다. 원본이 이미 count 이상이면 생략.
        //    primary(맞춤유형 우선 depth) 를 먼저 채우고, 모자라면 secondary 로 넘어간다(spill).
        if (byItemId.size() < cappedCount) {
            List<List<Integer>> tiers = findConceptTiers(userTestId, category);
            fillFromRange(byItemId, tiers.get(0), cappedCount);
            if (byItemId.size() < cappedCount) {
                fillFromRange(byItemId, tiers.get(1), cappedCount);
            }
        }

        // 3) 최종 순서대로 출제 번호 부여
        List<PersonalItemsResponse> result = new ArrayList<>(byItemId.values());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setTestItemNumber(i + 1);
        }
        return result;
    }

    /**
     * 맞춤 유형별 우선순위 tier (spec D1). 반환 = [primary 개념목록, secondary 개념목록].
     * 범위 = 학생 오답 answer → probabilities 의 depth≤2 개념(union). depth 3 제외.
     * - wrong(오답 위주): primary=depth 0, secondary=depth 1~2
     * - prerequisite(선수지식 위주): primary=depth 1~2, secondary=depth 0
     * secondary 는 primary 와 중복 개념 제외(개념은 우선 tier 에만 1회).
     */
    private List<List<Integer>> findConceptTiers(Long userTestId, String category) {
        List<Integer> primary = new ArrayList<>();
        List<Integer> secondary = new ArrayList<>();

        List<Answer> answerList = answerRepository.findAnswersByUserTestId(userTestId);
        if (answerList.isEmpty()) return List.of(primary, secondary);
        List<Long> answerIdList = answerList.stream().map(Answer::getAnswerId).collect(Collectors.toList());
        List<Probability> probabilityList = probabilityRepository.findProbability(answerIdList);

        for (Probability p : probabilityList) {
            int d = p.getToConceptDepth();
            if (d < 0 || d > 2) continue; // depth 3 제외
            boolean isPrimary = "wrong".equals(category) ? (d == 0) : (d >= 1);
            int cid = p.getConceptId();
            if (isPrimary) {
                if (!primary.contains(cid)) primary.add(cid);
            } else {
                if (!secondary.contains(cid)) secondary.add(cid);
            }
        }
        secondary.removeAll(primary); // 우선 tier 에 있으면 secondary 에서 제외
        return List.of(primary, secondary);
    }

    /**
     * 범위 채우기(spec D3): 개념당 1문항씩(다양성, 개념 등장 순서) → 추가 패스로 같은 개념 다른 문항을
     * round-robin 으로 채워 targetCount 도달까지. item_id dedup(원본 우선). 범위 소진 시 중단(미달 허용).
     */
    private void fillFromRange(Map<Long, PersonalItemsResponse> byItemId, List<Integer> categoryConcepts, int targetCount) {
        if (categoryConcepts.isEmpty()) return;
        // 개념 등장 순서를 보존한 개념별 문항 큐 (문항 자체 순서는 쿼리 ORDER BY RAND())
        Map<Integer, Deque<Item>> pool = new LinkedHashMap<>();
        for (Integer cid : categoryConcepts) pool.put(cid, new ArrayDeque<>());
        for (Item it : itemRepository.findItemsByConceptIds(categoryConcepts)) {
            Deque<Item> q = pool.get(it.getConceptId());
            if (q != null) q.add(it);
        }
        boolean progressed = true;
        while (byItemId.size() < targetCount && progressed) {
            progressed = false;
            for (Integer cid : categoryConcepts) {
                if (byItemId.size() >= targetCount) break;
                Deque<Item> q = pool.get(cid);
                while (q != null && !q.isEmpty()) {
                    Item it = q.poll();
                    if (!byItemId.containsKey(it.getItemId())) {
                        byItemId.put(it.getItemId(), PersonalItemConverter.convertToPersonalItemsResponse(it));
                        progressed = true;
                        break;
                    }
                }
            }
        }
    }

    private int clampCount(Integer count) {
        if (count == null) return COUNT_DEFAULT;
        return Math.max(COUNT_MIN, Math.min(COUNT_MAX, count));
    }

    /**
     * 레거시 Scope A 출제: 오답 → 선수지식 depth≤2 → 개념당 1문항. distinct·count 미적용(원형 보존).
     */
    private List<PersonalItemsResponse> findLegacyPersonalItems(Long userTestId) {
        List<PersonalItemsResponse> personalItemsResponseList = new ArrayList<>();

        List<Answer> answerList = answerRepository.findAnswersByUserTestId(userTestId);
        if (answerList.isEmpty()) return personalItemsResponseList;

        List<Long> answerIdList = answerList.stream().map(Answer::getAnswerId).collect(Collectors.toList());
        List<Probability> probabilityList = probabilityRepository.findProbability(answerIdList);
        List<Integer> conceptIdList = probabilityList.stream()
                .filter(pro -> pro.getToConceptDepth() <= 2)
                .map(Probability::getConceptId)
                .collect(Collectors.toList());

        for (int i = 0; i < conceptIdList.size(); i++) {
            int conceptId = conceptIdList.get(i);
            PersonalItemsResponse item = PersonalItemConverter.convertToPersonalItemsResponse(itemRepository.findByConceptId(conceptId));
            item.setTestItemNumber(i + 1);
            personalItemsResponseList.add(item);
        }
        return personalItemsResponseList;
    }
}
