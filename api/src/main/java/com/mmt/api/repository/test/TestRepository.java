package com.mmt.api.repository.test;

import com.mmt.api.domain.Test;

import java.util.List;

public interface TestRepository {
    List<Test> findAll();

    List<Test> findTestsBySchoolLevel(String schoolLevel);

}
