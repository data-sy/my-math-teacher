package com.mmt.api.controller;

import com.mmt.api.dto.result.ResultResponse;
import com.mmt.api.service.ProbabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/result")
public class ResultController {

    private final ProbabilityService probabilityService;

    public ResultController(ProbabilityService probabilityService) {
        this.probabilityService = probabilityService;
    }

    /**
     * user_test_id에 따른 분석 결과 보기
     */
    @GetMapping("/{userTestId}")
    public List<ResultResponse> getResults(@PathVariable Long userTestId){ return probabilityService.findResults(userTestId);}

}
