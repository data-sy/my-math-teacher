package com.mmt.api.repository.userTest;

import com.mmt.api.domain.Test;
import com.mmt.api.domain.UserTests;

import java.util.List;

public interface UserTestRepository {

    void save(Long userId, Long testId);

    List<UserTests> findByUserId(Long userId);

}
