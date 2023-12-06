package com.mmt.api.controller;

import com.mmt.api.dto.test.TestCreateRequest;
import com.mmt.api.dto.test.TestResponse;
import com.mmt.api.dto.testItem.TestItemsResponse;
import com.mmt.api.dto.userTest.UserTestsResponse;
import com.mmt.api.service.TestItemService;
import com.mmt.api.service.TestService;
import com.mmt.api.service.UserTestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    private final TestService testService;

    private final TestItemService testItemService;
    private final UserTestService userTestService;

    public TestController(TestService testService, TestItemService testItemService, UserTestService userTestService) {
        this.testService = testService;
        this.testItemService = testItemService;
        this.userTestService = userTestService;
    }

    /**
     * 전체 학습지 목록 보기
     */
    @GetMapping("")
    public List<TestResponse> getTests(){
        return testService.findTests();
    }

    /**
     * 유저의 학습지 목록
     */
    @GetMapping("/user/{userId}")
    public List<UserTestsResponse> getTests(@PathVariable Long userId){
        return userTestService.findTests(userId);
    }

    /**
     * '학교군(schoolLevel)'에 따른 학습지 목록 보기
     */
    @GetMapping("/school-level/{schoolLevel}")
    public List<TestResponse> getTests(@PathVariable String schoolLevel){
        return testService.findTestsBySchoolLevel(schoolLevel);
    }

    /**
     * 학습지 상세보기
     */
    @GetMapping("/{testId}")
    public List<TestItemsResponse> getTestItems(@PathVariable Long testId){
        return testItemService.findTestItems(testId);
    }

    /**
     * 진단학습지 다운로드
     */
    @PostMapping("")
    public void create(@RequestBody TestCreateRequest request) {
        userTestService.create(request.getUserId(), request.getTestId());
    }

    /**
     * 맞춤학습지 다운로드
     */
}
