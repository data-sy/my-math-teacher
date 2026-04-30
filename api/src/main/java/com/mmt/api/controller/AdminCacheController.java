package com.mmt.api.controller;

import com.mmt.api.service.ConceptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * spec-02 Task 2.2: 그래프 쿼리 캐시 무효화 endpoint.
 * SecurityConfig 의 {@code /admin/**} 매핑이 ROLE_ADMIN 을 요구하므로 별도 보안 어노테이션 불필요.
 * CSV 재로드 후 운영자가 수동으로 호출.
 */
@RestController
@RequestMapping("/admin/cache/graph")
public class AdminCacheController {

    private final ConceptService conceptService;

    public AdminCacheController(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    @PostMapping("/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateGraphCaches() {
        long deleted = conceptService.invalidateGraphCaches();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
