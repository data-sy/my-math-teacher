package com.mmt.api.dto.testItem;

import com.mmt.api.domain.TestItems;

import java.util.ArrayList;
import java.util.List;

public class TestItemConverter {


    public static TestItemsResponse convertToTestItemsResponse(TestItems testItems) {
        TestItemsResponse testItemsResponse = new TestItemsResponse();
        testItemsResponse.setItemId(testItems.getItemId());
        testItemsResponse.setItemAnswer(testItems.getItemAnswer());
        testItemsResponse.setItemImagePath(testItems.getItemImagePath());
        testItemsResponse.setTestItemNumber(testItems.getTestItemNumber());
        testItemsResponse.setConceptName(testItems.getConceptName());
        return testItemsResponse;
    }

    public static List<TestItemsResponse> convertListToTestItemsResponseList(List<TestItems> testItemsList) {
        List<TestItemsResponse> responseList = new ArrayList<>();
        for (TestItems testItems : testItemsList) {
            responseList.add(convertToTestItemsResponse(testItems));
        }
        return responseList;
    }

}
