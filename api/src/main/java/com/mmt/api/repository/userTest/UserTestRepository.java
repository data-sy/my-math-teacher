package com.mmt.api.repository.userTest;

import com.mmt.api.domain.Test;
import com.mmt.api.domain.UserTests;

import java.util.List;

public interface UserTestRepository {

    void save(Long userId, Long testId);

    List<UserTests> findByUserId(Long userId);

    List<UserTests> findRecordedTests(Long userId);

    List<Long> findUserTestIds(Long userTestId);

    boolean existsByUserTestIdAndUserId(Long userTestId, Long userId);

    /**
     * M7 spec-01: 자가진단 세션 행 생성 (test_id = NULL — 진단 세션은 학습지 아님).
     * 생성된 user_test_id 를 반환한다. 구 save() 는 무변경 보존.
     */
    Long saveDiagnosisSession(Long userId);

}
