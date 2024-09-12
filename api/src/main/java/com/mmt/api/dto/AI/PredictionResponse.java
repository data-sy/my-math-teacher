package com.mmt.api.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class PredictionResponse {
    private List<List<Double>> predictions;
}