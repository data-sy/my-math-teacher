package com.mmt.api.service;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.Probability;
import com.mmt.api.dto.item.PersonalItemConverter;
import com.mmt.api.dto.item.PersonalItemsRequest;
import com.mmt.api.dto.item.PersonalItemsResponse;
import com.mmt.api.repository.answer.AnswerRepository;
import com.mmt.api.repository.item.ItemRepository;
import com.mmt.api.repository.probability.ProbabilityRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemService {
    private final AnswerService answerService;
    private final AnswerRepository answerRepository;
    private final ProbabilityRepository probabilityRepository;
    private final ItemRepository itemRepository;

    public ItemService(AnswerService answerService, AnswerRepository answerRepository, ProbabilityRepository probabilityRepository, ItemRepository itemRepository) {
        this.answerService = answerService;
        this.answerRepository = answerRepository;
        this.probabilityRepository = probabilityRepository;
        this.itemRepository = itemRepository;
    }

    public List<PersonalItemsResponse> findPersonalItems(PersonalItemsRequest request) {

        List<PersonalItemsResponse> personalItemsResponseList = new ArrayList<>();

        /**
         * ANSWERS TABLE에서 user_test_id에 따른 answer_id 쿼리 (조건 : 정오답 여부 0)
         */
        List<Answer> answerList = answerRepository.findAnswersByUserTestId(request.getUserTestId());

        // 리팩토링) 정오답 여부 0인 결과가 없을 때, 어떻게 처리할 것인가
            // 우선은 쿼리 결과 리스트가 isEmpty라면 빈 List<PersonalItemsResponse> 리턴하도록 처리해 둠
        if (answerList.isEmpty()) return personalItemsResponseList;

        // 다음 쿼리의 인풋을 위해 필터링 결과물에서 answer_id만 추출
        List<Long> answerIdList = answerList.stream().map(Answer::getAnswerId).collect(Collectors.toList());


        /**
         * PROBABILITIES TABLE에서 answer_id에 따른 concept_id 쿼리 (조건 : 없음. 우선 다 가져와서 자바에서 필터링 테스트)
         */
        List<Probability> probabilityList = probabilityRepository.findProbability(answerIdList);

        // 리팩토링 : 필터링 조건은 계속 수정될 부분 !!! (핵심 비즈니스 로직)
            // 우선은 "선수지식 2 이하" 만 필터 걸자 & conceptId만 추출
        List<Integer> conceptIdList = probabilityList.stream()
                .filter(pro -> pro.getToConceptDepth() <= 2)
                .map(Probability::getConceptId)
                .collect(Collectors.toList());


        /**
         * (여기가 성능 테스트 파트이므로 다양한 시도 고고!)
         * ITEMS TABLE에서 concept_id에 맞는 item 쿼리 (조건 : 각각 1개만 랜덤으로 추출)
         */
        // 1. for문 돌려서 각각의 결과물 받아서 add
        for (int conceptId : conceptIdList) {
            personalItemsResponseList.add(PersonalItemConverter.convertToPersonalItemsResponse(itemRepository.findByConceptId(conceptId)));
        }
        // 2. DB 단에서 ROW_NUMBER() 방식 사용해서 한 쿼리로 처리

        // 3.

        // 4.

        return personalItemsResponseList;
    }
}
