package com.mmt.api.service;

import com.mmt.api.dto.test.TestConverter;
import com.mmt.api.dto.test.TestResponse;
import com.mmt.api.repository.test.TestRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestService {

    private final TestRepository testRepository;

    public TestService(TestRepository testRepository) {
        this.testRepository = testRepository;
    }

    public List<TestResponse> findTestsBySchoolLevel(String schoolLevel){
        return TestConverter.convertListToTestResponseList(testRepository.findTestsBySchoolLevel(schoolLevel));
    }

}
