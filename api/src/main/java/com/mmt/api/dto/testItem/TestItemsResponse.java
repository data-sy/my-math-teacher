package com.mmt.api.dto.testItem;

import lombok.Data;

@Data
public class TestItemsResponse {

    private Long itemId;
    private String itemAnswer;
    private String itemImagePath;
    private int testItemNumber;
    private String conceptName;

}
