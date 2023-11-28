package com.mmt.api.dto.test;

import lombok.Data;

@Data
public class TestResponse {

    private Long testId;
    private String testName;
    private String testComments;
    private String testSchoolLevel;
    private String testGradeLevel;

}
