package com.mmt.api.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AIServingResponse {
    private List<List<Double>> predictions;
}