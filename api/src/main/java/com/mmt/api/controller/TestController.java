package com.mmt.api.controller;

import com.mmt.api.dto.test.TestResponse;
import com.mmt.api.dto.testItem.TestItemsResponse;
import com.mmt.api.dto.userTest.UserTestsResponse;
import com.mmt.api.service.TestItemService;
import com.mmt.api.service.TestService;
import com.mmt.api.service.UserTestService;
import com.mmt.api.service.user.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    private final TestService testService;

    private final TestItemService testItemService;
    private final UserTestService userTestService;
    private final UserService userService;

    public TestController(TestService testService, TestItemService testItemService, UserTestService userTestService, UserService userService) {
        this.testService = testService;
        this.testItemService = testItemService;
        this.userTestService = userTestService;
        this.userService = userService;
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
    @GetMapping("/user")
    public List<UserTestsResponse> getUserTests(){
//        // security 적용 전까지는 테스트 id 사용
//        Long userId = 3L;
        return userTestService.findTests(userService.getMyUserIdWithAuthorities());
    }

    /**
     * 유저의 '정오답 기록한' 학습지 목록 보기
     */
    @GetMapping("/user/is-record")
    public List<UserTestsResponse> getRecoredTests(){
        return userTestService.findRecordedTests(userService.getMyUserIdWithAuthorities());
    }

//    /** deprecated (security 적용해서)
//     * 유저의 학습지 목록
//     */
//    @GetMapping("/user/{userId}")
//    public List<UserTestsResponse> getUserTests(@PathVariable Long userId){
//        return userTestService.findTests(userId);
//    }

    /**
     * '학교군(schoolLevel)'에 따른 학습지 목록 보기
     */
    @GetMapping("/school-level/{schoolLevel}")
    public List<TestResponse> getTests(@PathVariable String schoolLevel){
        return testService.findTestsBySchoolLevel(schoolLevel);
    }

    /**
     * 샘플 학습지 목록 보기
     * 샘플 유저 user_id=3 의 학습지
     */
    @GetMapping("/sample")
    public List<UserTestsResponse> getSampleTests(){
        return userTestService.findTests(3L);
    }

    /**
     * 샘플 '정오답 기록한' 학습지 목록 보기
     * 샘플 유저 user_id=3 의 학습지
     */
    @GetMapping("/sample/is-record")
    public List<UserTestsResponse> getSampleRecoredTests(){
        return userTestService.findRecordedTests(3L);
    }

    /**
     * 학습지 상세보기
     */
    @GetMapping("/detail/{testId}")
    public List<TestItemsResponse> getTestItems(@PathVariable Long testId){
        return testItemService.findTestItems(testId);
    }

    /**
     * 진단학습지 다운로드
     */
    @PostMapping("/{testId}")
    public void create(@PathVariable Long testId) {
        Long userId = userService.getMyUserIdWithAuthorities();
        userTestService.create(userId, testId);
    }

    /**
     * 맞춤학습지 다운로드
     */
}
