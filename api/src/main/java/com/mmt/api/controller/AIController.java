package com.mmt.api.controller;

import com.mmt.api.dto.ai.AIServingResponse;
import com.mmt.api.dto.answer.AnswerCreateRequest;
import com.mmt.api.service.AnswerService;
import com.mmt.api.service.ProbabilityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/weakness-diagnosis")
// ai 말고 weakness-diagnosis(취약점-진단)으로 수정
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
     * 텐서플로우_서빙 서버 테스트
     */
    @GetMapping("/serving-test")
    public AIServingResponse getPrediction(){
//        return probabilityService.getPredictionTest();
        return probabilityService.getPrediction(3L);
    }

//    /**
//     * 인풋 모양 테스트
//     */
//    @GetMapping("/input-detail")
//    public List<InputInstance> getInputDetail(){
//        return answerService.findAIInput(3L);
//    }

    // deprecated : 플라스크 서버 때 사용했던
//    /**
//     * AI input 데이터 플라스크에 제공
//     */
//    @GetMapping("/{userTestId}")
//    public AIInputResponse getAIInput(@PathVariable Long userTestId){
//        return answerService.findAIInput(userTestId);
//    }
//
//    /**
//     * AI output 데이터 DB에 저장
//     */
//    @PostMapping("")
//    public void create(@RequestBody AIOutputRequest request){
//        probabilityService.create(request.getUserTestId(), request.getProbabilityList());
//    }

}
