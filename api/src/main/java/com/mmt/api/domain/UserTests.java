package com.mmt.api.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserTests {

    // user_tests 테이블 + users 테이블 + tests 테이블

    private Long userTestId;
    private String testDate;
    //    // users
//    private Long userId;
//    private String userName;
//    private LocalDate userBirthdate;
    // tests
    private Long testId;
    private String testName;
    private String testSchoolLevel;
    private String testGradeLevel;
    private String testSemester;

//    private String testComments;

    private boolean isRecord;

//    // 디버깅 용 : System.out.println(userTests);
//    @Override
//    public String toString() {
//        return String.format("UserTests{ userTestId = %d, testName = %s, isRecord = %b }", userTestId, testName, isRecord);
//    }

}
