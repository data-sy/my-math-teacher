package com.mmt.api.service;

import com.mmt.api.dto.answer.AnswerConverter;
import com.mmt.api.dto.answer.AnswerCreateRequest;
import com.mmt.api.repository.answer.AnswerRepository;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {
    private final AnswerRepository answerRepository;

    public AnswerService(AnswerRepository answerRepository) {
        this.answerRepository = answerRepository;
    }

    public void create(AnswerCreateRequest request) {
        answerRepository.save(AnswerConverter.convertToAnswer(request));
    }

}
