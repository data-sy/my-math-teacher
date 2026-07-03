package com.mmt.api.controller;

import com.mmt.api.dto.test.TestResponse;
import com.mmt.api.dto.testItem.TestItemsResponse;
import com.mmt.api.dto.userTest.UserTestsResponse;
import com.mmt.api.service.TestItemService;
import com.mmt.api.service.TestService;
import com.mmt.api.service.UserTestService;
import com.mmt.api.service.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
     * 유저의 학습지 목록
     */
    @GetMapping("/user")
    public List<UserTestsResponse> getUserTests(){
        return userTestService.findTests(userService.getMyUserIdWithAuthorities());
    }

    /**
     * 유저의 '정오답 기록한' 학습지 목록 보기
     */
    @GetMapping("/user/is-record")
    public List<UserTestsResponse> getRecoredTests(){
        return userTestService.findRecordedTests(userService.getMyUserIdWithAuthorities());
    }

    /**
     * '학교군(schoolLevel)'에 따른 학습지 목록 보기
     */
    @GetMapping("/school-level/{schoolLevel}")
    public List<TestResponse> getTestsBySchoolLevel(@PathVariable String schoolLevel){
        return testService.findTestsBySchoolLevel(schoolLevel);
    }

    /**
     * 학습지 상세보기
     */
    @GetMapping("/detail/{testId}")
    public List<TestItemsResponse> getTestItems(@PathVariable Long testId){
        return testItemService.findTestItems(testId);
    }

    /**
     * 진단학습지 생성
     */
    @PostMapping("/{testId}")
    public void create(@PathVariable Long testId) {
        Long userId = userService.getMyUserIdWithAuthorities();
        userTestService.create(userId, testId);
    }

    /**
     * 맞춤학습지 생성
     */

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

    // 비로그인 체험용 안내 샘플 학습지 ID 범위 (프론트 DiagView isImageExist 범위와 일치: 복소수와 이차방정식 1~5).
    private static final long SAMPLE_DETAIL_TEST_ID_MIN = 491L;
    private static final long SAMPLE_DETAIL_TEST_ID_MAX = 495L;

    /**
     * 샘플 학습지 상세보기 (비로그인 허용)
     * 안내 샘플(491~495)만 정답(item_answer) 포함 상세를 공개 — 게스트 다운로드가 원래 의도한 노출 범위.
     * 그 외 testId 는 403 → 나머지 시험 정답은 /detail(인증 필수)로 계속 보호.
     */
    @GetMapping("/sample/detail/{testId}")
    public List<TestItemsResponse> getSampleTestItems(@PathVariable Long testId){
        if (testId < SAMPLE_DETAIL_TEST_ID_MIN || testId > SAMPLE_DETAIL_TEST_ID_MAX) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "샘플 학습지가 아닙니다.");
        }
        return testItemService.findTestItems(testId);
    }

}
