package com.mmt.api.service;

import com.mmt.api.dto.item.PersonalItemsRequest;
import com.mmt.api.dto.item.PersonalItemsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemService {
    public List<PersonalItemsResponse> findPersonalItems(PersonalItemsRequest request) {

        // ANSWERS TABLE에서 user_test_id에 따른 answer_id 쿼리 (조건 : 정오답 여부 0)
            // 쿼리 결과가 null 이면 100점인 학습지이므로 "해당 학습지로는 판별할 선수지식이 없다" 전달하는 예외처리

        // PROBABILITYS TABLE에서 answer_id에 따른 concept_id 쿼리

        // request에 있는 조건에 맞춰 필터링

        // ITEMS TABLE에서 concept_id에 맞는 item 쿼리
            // 조건 : 각각 1개만 랜덤으로 추출
            // Item 도메인에 conceptName 추가 (testItem 참고)

        return null;
    }
}
