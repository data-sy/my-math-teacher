package com.mmt.api.dto.userTest;

import lombok.Data;

@Data
public class UserTestsResponse {

    private Long userTestId;
    private Long testId;
    private String testName;
    private boolean isRecord;

    // boolean 타입의 isㅇㅇㅇ 필드는 네이밍 컨벤션에 따른 getter, setter는 명명 규칙이 달라서 따로 적어둠
    // getter
    public boolean isRecord() {
        return isRecord;
    }
    // setter
    public void setRecord(boolean record) {
        isRecord = record;
    }

}