package com.mmt.api.controller;

import com.mmt.api.dto.item.PersonalItemsResponse;
import com.mmt.api.service.ItemService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * 맞춤학습지 출제 문항 목록.
     * 조건 파라미터(category·reExam·count)는 옵셔널 — 전부 생략 시 레거시 Scope A 동작(하위호환).
     * spec: docs/specs/product/spec-01-personalview-conditional-items-scope-b.md
     */
    @GetMapping("/personal")
    public List<PersonalItemsResponse> getPersonalItems(
            @RequestParam("userTestId") Long userTestId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "reExam", required = false) String reExam,
            @RequestParam(value = "count", required = false) Integer count){
        // (#2) 인증된 사용자 본인의 학습 기록만 조회 가능하도록 소유권 검사를 서비스에 위임한다.
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return itemService.findPersonalItems(userTestId, userEmail, category, reExam, count);
    }

}
