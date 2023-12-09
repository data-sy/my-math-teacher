package com.mmt.api.service;

import com.mmt.api.domain.AnswerCode;
import com.mmt.api.domain.Probability;
import com.mmt.api.dto.AI.AIInputResponse;
import com.mmt.api.dto.answer.AnswerConverter;
import com.mmt.api.dto.answer.AnswerCreateRequest;
import com.mmt.api.repository.answer.AnswerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class AnswerService {
    private final AnswerRepository answerRepository;
    private final UserTestService userTestService;

    public AnswerService(AnswerRepository answerRepository, UserTestService userTestService) {
        this.answerRepository = answerRepository;
        this.userTestService = userTestService;
    }

    public void create(AnswerCreateRequest request) {
        answerRepository.save(AnswerConverter.convertToAnswer(request));
    }

    public AIInputResponse findAIInput(Long userId){
        AIInputResponse aiInputResponse = new AIInputResponse(userId);
        List<List<Integer>> answerCodeResponseList = new ArrayList<>();
        // 조건에 맞는 user_test_id들 찾기
        List<Long> utIdList = userTestService.findBefore(userId);
        // user_test_id별 정오답 기록을 answerCodeList에 넣기 (데이터 500배 증폭 -> 100배로 수정)
        utIdList.forEach(utId -> {
            List<AnswerCode> answerCodeList = answerRepository.findAnswerCode(utId);
            answerCodeList.forEach(answerCode -> {
                IntStream.range(0, 100)
                        .mapToObj(i -> AnswerConverter.convertToIntegerList(answerCode))
                        .forEach(answerCodeResponseList::add);
            });
        });
        aiInputResponse.setAnswerCodeResponseList(answerCodeResponseList);
        return aiInputResponse;
    }

    public List<Probability> findIds(Long userTestId){
        return answerRepository.findIds(userTestId);
    }

}
