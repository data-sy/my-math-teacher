package com.mmt.api.repository.probability;

import com.mmt.api.domain.Probability;
import com.mmt.api.domain.Result;

import java.util.List;

public interface ProbabilityRepository {

    void save(List<Probability> probabilities);

    List<Result> findResults(Long userTestId);

    List<Probability> findProbability(List<Long> answerIdList);

    /**
     * M7 spec-01: 자가진단 세션 스코프 저장 — user_test_id 로 기록, answer_id = NULL.
     * 구 경로 조회(findResults·findProbability)는 answer_id 경유라 이 행들에 노출되지 않는다.
     */
    void saveForUserTest(Long userTestId, List<DiagnosisProbabilityRow> rows);

    /**
     * M7 spec-01 결과 재조회 — user_test_id 스코프 직조회 + 개념 단위 dedup
     * (MIN(to_concept_depth), percent 는 skill 단위 동일값). 구 findResults 와 달리
     * answers 조인이 없다 (§6 실측 ③ 확정 경로).
     */
    List<DiagnosisProbabilityRow> findDiagnosisRowsByUserTestId(Long userTestId);
}
