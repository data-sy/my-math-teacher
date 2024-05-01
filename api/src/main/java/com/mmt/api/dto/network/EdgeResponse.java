package com.mmt.api.dto.network;

import com.mmt.api.domain.KnowledgeSpace;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EdgeResponse {

    // vue에서 필요한 엣지의 형테를 맞춰주기 위해 굳이 1개인데도 객체로 따로 만듦
    // vue에서 조작 없이 바로 사용할 수 있도록
    private KnowledgeSpace data;

}
