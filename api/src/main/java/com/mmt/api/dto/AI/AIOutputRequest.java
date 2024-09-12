package com.mmt.api.dto.ai;

import lombok.Data;

@Data
public class AIOutputRequest {

    private Long userTestId;
    private double[] probabilityList;

}
