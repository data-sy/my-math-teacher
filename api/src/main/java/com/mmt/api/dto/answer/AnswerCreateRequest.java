package com.mmt.api.dto.answer;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AnswerCreateRequest {

    private Long userTestId;
    private List<AnswerCodeCreateRequest> answerCodeCreateRequestList;

}
