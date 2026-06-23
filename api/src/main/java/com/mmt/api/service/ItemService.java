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

import java.util.ArrayList;
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
        // item_id 기준 dedup + 삽입 순서 보존(재출제 원본 먼저 → 신규). 원본 우선.
        Map<Long, PersonalItemsResponse> byItemId = new LinkedHashMap<>();

        // 1) 재출제 원본 문항 (additive)
        if ("wrong".equals(reExam) || "all".equals(reExam)) {
            boolean onlyWrong = "wrong".equals(reExam);
            for (Item original : itemRepository.findOriginalItemsByUserTestId(userTestId, onlyWrong)) {
                byItemId.putIfAbsent(original.getItemId(), PersonalItemConverter.convertToPersonalItemsResponse(original));
            }
        }

        // 2) 맞춤 유형 기반 신규 문항 (오답 answer → probabilities depth 필터 → distinct concept → count 캡)
        List<Answer> answerList = answerRepository.findAnswersByUserTestId(userTestId);
        if (!answerList.isEmpty()) {
            List<Long> answerIdList = answerList.stream().map(Answer::getAnswerId).collect(Collectors.toList());
            List<Probability> probabilityList = probabilityRepository.findProbability(answerIdList);
            List<Integer> conceptIdList = probabilityList.stream()
                    .filter(pro -> matchesCategory(category, pro.getToConceptDepth()))
                    .map(Probability::getConceptId)
                    .distinct()
                    .limit(cappedCount)
                    .collect(Collectors.toList());
            for (int conceptId : conceptIdList) {
                PersonalItemsResponse item = PersonalItemConverter.convertToPersonalItemsResponse(itemRepository.findByConceptId(conceptId));
                byItemId.putIfAbsent(item.getItemId(), item);
            }
        }

        // 3) 최종 순서대로 출제 번호 부여
        List<PersonalItemsResponse> result = new ArrayList<>(byItemId.values());
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setTestItemNumber(i + 1);
        }
        return result;
    }

    // 맞춤 유형 → to_concept_depth 매핑 (spec D1)
    private boolean matchesCategory(String category, int depth) {
        if ("wrong".equals(category)) return depth == 0;
        if ("prerequisite".equals(category)) return depth >= 1 && depth <= 2;
        return depth <= 2; // 방어적 기본 (정상 흐름에서는 category==null 이 레거시 경로로 분기됨)
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
