package com.mmt.api.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AIServingRequest {
    private String signatureName;
    private List<InputInstance> instances;
}