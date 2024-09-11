package com.mmt.api.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Item {

    // items
    private Long itemId;
    private String itemAnswer;
    private String itemImagePath;
    // concepts
    private String conceptName;
    // chapters
    private String schoolLevel;
    private String gradeLevel;
    private String semester;

}
