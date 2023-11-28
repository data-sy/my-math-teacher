package com.mmt.api.repository.test;

import com.mmt.api.domain.Test;

import java.util.List;

public interface TestRepository {

    Long save(String testName, String testComments);

    List<Test> findAll();

    Test findById(Long testId);

}
