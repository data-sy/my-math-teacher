package com.mmt.api.domain.diagnosis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * M7 spec-01 §4.6 통합 학습 큐. 유저당 활성 큐 = 최신 queue_id 1개 (파생 —
 * 재진단 시 새 큐가 생기고 구 큐는 보관, 포인터 컬럼 없음: S5 드리프트 차단 원칙의 확장).
 * FK 는 평컬럼 (ADR-0010 Decision-3).
 */
@Entity
@Table(name = "learning_queues")
@Getter
@Setter
@NoArgsConstructor
public class LearningQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "queue_id")
    private Long queueId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_test_id", nullable = false)
    private Long userTestId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LearningQueue(Long userId, Long userTestId) {
        this.userId = userId;
        this.userTestId = userTestId;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
