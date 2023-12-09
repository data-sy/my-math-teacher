package com.mmt.api.controller;

import com.mmt.api.dto.AI.AIInputResponse;
import com.mmt.api.dto.AI.AIOutputRequest;
import com.mmt.api.service.AnswerService;
import com.mmt.api.service.ProbabilityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AIController {

    private final AnswerService answerService;
    private final ProbabilityService probabilityService;

    public AIController(AnswerService answerService, ProbabilityService probabilityService) {
        this.answerService = answerService;
        this.probabilityService = probabilityService;
    }

    /**
     * AI input 데이터 플라스크에 제공
     */
    @GetMapping("/{userTestId}")
    public AIInputResponse getAIInput(@PathVariable Long userTestId){
        return answerService.findAIInput(userTestId);
    }

    /**
     * AI output 데이터 DB에 저장
     */
    @PostMapping("")
    public void create(@RequestBody AIOutputRequest request){
        probabilityService.create(request.getUserTestId(), request.getProbabilityList());
    }

}
