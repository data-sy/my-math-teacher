package com.mmt.api.controller;

import com.mmt.api.dto.item.PersonalItemsRequest;
import com.mmt.api.dto.item.PersonalItemsResponse;
import com.mmt.api.service.ItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * 맞춤학습지 출제 문항 목록
     */
    @GetMapping("/personal")
    public List<PersonalItemsResponse> getPersonalItems(@RequestBody PersonalItemsRequest request){
        return itemService.findPersonalItems(request);
    }

    // 아이템 하나 바꾸는 건 아이템 컨트롤러로 추가

}
