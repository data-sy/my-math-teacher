package com.mmt.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 무중단 배포(M4 spec-01) 전환 게이트용 헬스 엔드포인트.
 * 컨텍스트 기동 후 200 을 반환해 "앱이 요청을 받을 준비가 됐다"만 표현한다.
 * 의존성(DB/Redis) 검사는 하지 않는다 (spec-01 §6 Out of scope).
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping("")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

}
