package com.mmt.api.controller;

import com.mmt.api.dto.answer.AnswerCreateRequest;
import com.mmt.api.service.AnswerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/record")
public class RecordController {
    private final AnswerService answerService;

    public RecordController(AnswerService answerService) {
        this.answerService = answerService;
    }

    /**
     * 정오답 기록 저장하기
     */
    @PostMapping("")
    public void create(@RequestBody AnswerCreateRequest request){
        answerService.create(request);
    }

}
