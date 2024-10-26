package com.mmt.api.controller;

import com.mmt.api.dto.answer.AnswerCreateRequest;
import com.mmt.api.dto.result.ResultResponse;
import com.mmt.api.service.AnswerService;
import com.mmt.api.service.ProbabilityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/weakness-diagnosis")
public class AIController {

    private final AnswerService answerService;
    private final ProbabilityService probabilityService;

    public AIController(AnswerService answerService, ProbabilityService probabilityService) {
        this.answerService = answerService;
        this.probabilityService = probabilityService;
    }

    /**
     *  AI 분석
     */
    @PostMapping("")
    public void create(@RequestBody AnswerCreateRequest request){
        probabilityService.createAndPredict(request);
    }

    /**
     * user_test_id에 따른 분석 결과 보기
     */
    @GetMapping("/result/{userTestId}")
    public List<ResultResponse> getResults(@PathVariable Long userTestId){ return probabilityService.findResults(userTestId);}

    /**
     * 샘플 분석 결과 보기
     * 분리해서 만든 이유 : security filter
     */
    @GetMapping("/result/sample/{userTestId}")
    public List<ResultResponse> getSampleResults(@PathVariable Long userTestId){ return probabilityService.findResults(userTestId);}

}
