package com.mmt.api.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Chapter {

    private int chapterId;
    private String chapterName;
//    private String schoolLevel;
//    private String gradeLevel;
//    private String semester;
    private String chapterMain;
    private String chapterSub;

}
