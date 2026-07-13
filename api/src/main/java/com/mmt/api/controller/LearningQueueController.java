package com.mmt.api.controller;

import com.mmt.api.dto.diagnosis.LearningQueueResponse;
import com.mmt.api.service.LearningQueueService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M7 spec-01 §4.6 통합 학습 큐 API — 전 엔드포인트 인증 필수
 * (SecurityConfig anyRequest().authenticated() 커버, permitAll 없음) + 소유권 검사.
 * mmt.diagnosis.enabled=false 면 빈 미등록 → 404 (즉시 롤백 계약).
 */
@RestController
@RequestMapping("/api/v1/learning-queues")
@ConditionalOnProperty(name = "mmt.diagnosis.enabled", havingValue = "true")
public class LearningQueueController {

    private final LearningQueueService learningQueueService;

    public LearningQueueController(LearningQueueService learningQueueService) {
        this.learningQueueService = learningQueueService;
    }

    /** 큐 생성 (귀속 진단 결과 위에서 — F-4) */
    @PostMapping("")
    public LearningQueueResponse create(@RequestBody Map<String, Long> request) {
        Long userTestId = request.get("userTestId");
        return learningQueueService.create(currentUserEmail(), userTestId);
    }

    /** 이어가기 — 활성 큐 + 파생 현재 위치 (재진입, F-4) */
    @GetMapping("/me")
    public LearningQueueResponse me() {
        return learningQueueService.getMyQueue(currentUserEmail());
    }

    /** 완료 self-mark (멱등) */
    @PatchMapping("/{queueId}/items/{queueItemId}/done")
    public LearningQueueResponse markDone(@PathVariable Long queueId, @PathVariable Long queueItemId) {
        return learningQueueService.markDone(currentUserEmail(), queueId, queueItemId);
    }

    private static String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
