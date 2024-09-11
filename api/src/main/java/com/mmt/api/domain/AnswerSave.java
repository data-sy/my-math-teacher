package com.mmt.api.domain;

import lombok.Data;

import java.util.List;

@Data
public class AnswerSave {

    private Long userTestId;
    private List<AnswerCode> answerCodeList;

}
