package com.mmt.api.repository.probability;

import com.mmt.api.domain.Answer;
import com.mmt.api.domain.Probability;
import com.mmt.api.domain.Result;

import java.util.List;

public interface ProbabilityRepository {

    void save(List<Probability> probabilities);

    List<Result> findResults(Long userTestId);

    List<Probability> findProbability(List<Long> answerIdList);
}
