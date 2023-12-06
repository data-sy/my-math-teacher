package com.mmt.api.dto.answer;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnswerCodeCreateRequest {

    private Long itemId;
    private int answerCode;

}
