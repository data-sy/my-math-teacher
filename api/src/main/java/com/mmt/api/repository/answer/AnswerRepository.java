package com.mmt.api.repository.answer;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.AnswerSave;
import com.mmt.api.domain.AnswerCode;
import com.mmt.api.domain.Probability;

import java.util.List;

public interface AnswerRepository {

    public void save(AnswerSave answerSave);

    List<AnswerCode> findAnswerCode(Long userTestId);

    List<Probability> findIds(Long userTestId);

    List<Answer> findAnswersByUserTestId(Long userTestId);

}
