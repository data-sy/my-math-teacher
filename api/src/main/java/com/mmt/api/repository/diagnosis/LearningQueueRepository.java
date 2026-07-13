package com.mmt.api.repository.diagnosis;

import com.mmt.api.domain.diagnosis.LearningQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningQueueRepository extends JpaRepository<LearningQueue, Long> {

    /** 활성 큐 = 최신 queue_id (파생 — 재진단 시 새 큐, 구 큐 보관). */
    Optional<LearningQueue> findTopByUserIdOrderByQueueIdDesc(Long userId);
}
