package com.mmt.api.dto.AI;

import lombok.Data;

import java.util.List;

@Data
public class AIServingResponse {
    private List<List<Double>> predictions;
}