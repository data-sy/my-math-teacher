package com.mmt.api.repository.answer;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.AnswerCode;

import java.util.List;

public interface AnswerRepository {

    public void save(Answer answer);

    List<AnswerCode> findAnswerCode(Long userTestId);

}
