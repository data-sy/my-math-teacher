package com.mmt.api.dto.AI;

import lombok.Data;

import java.util.List;

@Data
public class InputInstance {
    private List<int[]> input;
}