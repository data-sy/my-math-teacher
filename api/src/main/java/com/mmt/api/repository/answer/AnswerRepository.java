package com.mmt.api.repository.answer;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.AnswerCode;
import com.mmt.api.domain.Probability;

import java.util.List;

public interface AnswerRepository {

    public void save(Answer answer);

    List<AnswerCode> findAnswerCode(Long userTestId);

    List<Probability> findIds(Long userTestId);

}
