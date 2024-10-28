package com.mmt.api.service;

import com.mmt.api.domain.AnswerCode;
import com.mmt.api.domain.Probability;
import com.mmt.api.dto.ai.InputInstance;
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

    public List<InputInstance> findAIInput(Long userTestId){
        List<InputInstance> inputInstanceList = new ArrayList<>();
        InputInstance inputInstance = new InputInstance();
        List<int[]> inputList = new ArrayList<>();
        // 조건에 맞는 user_test_id들 찾기
        List<Long> utIdList = userTestService.findBefore(userTestId);
        // user_test_id별 정오답 기록을 answerCodeList에 넣기 (데이터 10배 증폭 (ai input size(3)를 안정적으로 넘기고 문항 수 적은 것을 보완))
        utIdList.forEach(utId -> {
            List<AnswerCode> answerCodeList = answerRepository.findAnswerCode(utId);
            answerCodeList.forEach(answerCode -> {
                IntStream.range(0, 10)
                        .mapToObj(i -> AnswerConverter.convertToIntArray(answerCode))
                        .forEach(inputList::add);
            });
        });
        inputInstance.setInput(inputList);
        inputInstanceList.add(inputInstance);
        return inputInstanceList;
    }

    public List<Probability> findIds(Long userTestId){
        return answerRepository.findIds(userTestId);
    }

}
