package com.mmt.api.dto.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PersonalItemsRequest {
    // 나중에 조건 추가될 거라서 파람이나 패쓰베리어블 안 쓰고 리퀘스트 바디로 받음
    private Long userTestId;

}
