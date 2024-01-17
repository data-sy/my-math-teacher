package com.mmt.api.dto.userTest;

import com.mmt.api.domain.UserTests;

import java.util.ArrayList;
import java.util.List;

public class UserTestsConverter {

    public static UserTestsResponse convertToResponse(UserTests userTests){
        UserTestsResponse response = new UserTestsResponse();
        response.setUserTestId(userTests.getUserTestId());
        response.setTestDate(userTests.getTestDate());
        response.setTestId(userTests.getTestId());
        response.setTestName(userTests.getTestName());
        response.setTestSchoolLevel(userTests.getTestSchoolLevel());
        response.setTestGradeLevel(userTests.getTestGradeLevel());
        response.setTestSemester(userTests.getTestSemester());
        response.setRecord(userTests.isRecord());
        return response;
    }

    public static List<UserTestsResponse> convertListToTestResponseList(List<UserTests> userTestsList){
        List<UserTestsResponse> responseList = new ArrayList<>();
        for (UserTests userTests : userTestsList){
            responseList.add(convertToResponse(userTests));
        }
        return responseList;
    }

}
