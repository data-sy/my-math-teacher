package com.mmt.api.dto.network;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EdgeRequest {

    private List<Integer> conceptIdList;

}
