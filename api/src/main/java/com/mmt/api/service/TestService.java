package com.mmt.api.service;

import com.mmt.api.repository.test.TestRepository;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    private final TestRepository testRepository;

    public TestService(TestRepository testRepository) {
        this.testRepository = testRepository;
    }

    public Long create(String testName, String testComments){
        Long testId = testRepository.save(testName, testComments);
        return testId;
    }

    public List<TestResponse> findTests(){
        return TestConverter.convertListToTestResponseList(testRepository.findAll());
    }

    public TestResponse findOne(Long testId){
        return TestConverter.convertToTestResponse(testRepository.findById(testId));
    }

}