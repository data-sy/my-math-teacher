package com.mmt.api.dto.item;

import com.mmt.api.domain.Item;

import java.util.ArrayList;
import java.util.List;

public class PersonalItemConverter {

    public static PersonalItemsResponse convertToPersonalItemsResponse(Item item) {
        PersonalItemsResponse personalItemsResponse = new PersonalItemsResponse();
        personalItemsResponse.setItemId(item.getItemId());
        personalItemsResponse.setItemAnswer(item.getItemAnswer());
        personalItemsResponse.setItemImagePath(item.getItemImagePath());
        personalItemsResponse.setConceptName(item.getConceptName());
        personalItemsResponse.setSchoolLevel(item.getSchoolLevel());
        personalItemsResponse.setGradeLevel(item.getGradeLevel());
        personalItemsResponse.setSemester(item.getSemester());
        return personalItemsResponse;
    }

    public static List<PersonalItemsResponse> convertListToPersonalItemsResponseList(List<Item> itemList) {
        List<PersonalItemsResponse> responseList = new ArrayList<>();
        for (Item item : itemList) {
            responseList.add(convertToPersonalItemsResponse(item));
        }
        return responseList;
    }
}
