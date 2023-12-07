package com.mmt.api.dto.AI;

import lombok.Data;

@Data
public class AIOutputRequest {

    private Long userTestId;
    private double[] probabilityList;

}
