package com.mmt.api.dto.ai;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AIInputResponse {

    private Long userTestId;
    private List<List<Integer>> answerCodeResponseList;

    public AIInputResponse(Long userTestId) {
        this.userTestId = userTestId;
    }

}
