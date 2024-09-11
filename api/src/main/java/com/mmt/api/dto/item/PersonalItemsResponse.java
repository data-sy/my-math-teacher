package com.mmt.api.dto.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PersonalItemsResponse {

    private Long itemId;
    private String itemAnswer;
    private String itemImagePath;
    private int testItemNumber;
    private String conceptName;
    private String schoolLevel;
    private String gradeLevel;
    private String semester;

}
