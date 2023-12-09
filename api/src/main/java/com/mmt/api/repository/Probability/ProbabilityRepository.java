package com.mmt.api.repository.Probability;

import com.mmt.api.domain.Probability;

import java.util.List;

public interface ProbabilityRepository {

    void save(List<Probability> probabilities);

}
