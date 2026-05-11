package com.mmt.api.controller;

import com.mmt.api.util.RedisUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * spec-02 Task 2.2: 그래프 조회 캐시(graph:* prefix) 수동 무효화 endpoint.
 *
 * CSV 재로드 등 운영자가 트리거. 자동 재로드 로직은 부재(audit 확인됨)이므로
 * 본 endpoint 1개로 운영 충분. SecurityConfig 의 "/admin/**" → hasRole("ADMIN")
 * 라우팅이 1차 게이트이고, @PreAuthorize 는 컨트롤러별 의도 명시용 안전망.
 */
@RestController
@RequestMapping("/admin/cache")
public class AdminCacheController {

    private final RedisUtil redisUtil;

    public AdminCacheController(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/graph/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateGraphCache() {
        long deleted = redisUtil.deleteByPrefix("graph:");
        return ResponseEntity.ok(Map.of("deletedKeys", deleted));
    }
}
