package com.mmt.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class Answer {

    private Long userTestId;
    private List<AnswerCode> answerCodeList;

}
