package com.mmt.api.controller;

import com.mmt.api.dto.test.TestResponse;
import com.mmt.api.dto.testitem.TestItemsResponse;
import com.mmt.api.service.TestItemService;
import com.mmt.api.service.TestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    private final TestService testService;

    private final TestItemService testItemService;

    public TestController(TestService testService, TestItemService testItemService) {
        this.testService = testService;
        this.testItemService = testItemService;
    }

    /**
     * 전체 학습지 목록 보기
     */
    @GetMapping("")
    public List<TestResponse> getTests(){
        return testService.findTests();
    }

    // level에 따른 학습지 목록은 프론트에서 구현하자.

    /**
     * 학습지 상세보기
     */
    @GetMapping("/{testId}")
    public List<TestItemsResponse> getTestItems(@PathVariable Long testId){
        return testItemService.findTestItems(testId);
    }

}
