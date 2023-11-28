package com.mmt.api.controller;

import com.mmt.api.dto.test.TestResponse;
import com.mmt.api.service.ConceptService;
import com.mmt.api.service.TestItemService;
import com.mmt.api.service.TestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tests")
public class TestController {

    private final TestService testService;
    private final TestItemService testItemService;
    private final ConceptService conceptService;

    public TestController(TestService testService, TestItemService testItemService, ConceptService conceptService) {
        this.testService = testService;
        this.testItemService = testItemService;
        this.conceptService = conceptService;
    }

    /**
     * 전체 학습지 목록
     */
    @GetMapping("")
    public List<TestResponse> getTests(){
        return testService.findTests();
    }

    // level에 따른 학습지 목록은 프론트에서 구현하자.
//    /**
//     * school_level에 따른 학습지 목록
//     */
//    /**
//     * grade_level에 따른 학습지 목록
//     */

    /**
     * 학습지 상세보기
     */
    @GetMapping("/{testId}")
    public List<TestItemsResponse> getTestItems(@PathVariable Long testId){
        return testItemService.findTestItems(testId);
    }

    /**
     * 문항 상세보기 : [상세보기]버튼 클릭 시 단위개념 자세히 보기
     */
    @GetMapping("/items/{conceptId}"기
    public ConceptResponse getConcept(@PathVariable int conceptId){
        return conceptService.findOne(conceptId);
    }

}
