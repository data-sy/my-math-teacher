package com.mmt.api.dto.testitem;

import lombok.Data;

@Data
public class TestItemsResponse {

    private Long itemId;
    private String itemAnswer;
    private String itemImagePath;
    private int testItemNumber;

}
