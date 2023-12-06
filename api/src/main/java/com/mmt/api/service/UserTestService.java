package com.mmt.api.service;

import com.mmt.api.dto.userTest.UserTestsConverter;
import com.mmt.api.dto.userTest.UserTestsResponse;
import com.mmt.api.repository.userTest.UserTestRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserTestService {

    private final UserTestRepository userTestRepository;

    public UserTestService(UserTestRepository userTestRepository) {
        this.userTestRepository = userTestRepository;
    }

    public void create(Long userId, Long testId){
        userTestRepository.save(userId, testId);
    }

    public List<UserTestsResponse> findTests(Long userId){
        return UserTestsConverter.convertListToTestResponseList(userTestRepository.findByUserId(userId));
    }

}
