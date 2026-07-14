package com.mmt.api.domain.diagnosis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 학습 큐 계단 1칸 (spec-01 §4.6). position = 위상순 지배 정렬 결과 (선수 먼저 —
 * 계단 불변식). 현재 위치 = position 순 첫 done=false (파생값, S5 — 포인터 컬럼 없음).
 * 완료 = 유저 self-mark, 앱은 외부 학습을 검증하지 않음 (PRD §4.3).
 */
@Entity
@Table(name = "learning_queue_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_lqi_queue_position",
                columnNames = {"queue_id", "position"}))
@Getter
@Setter
@NoArgsConstructor
public class LearningQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "queue_item_id")
    private Long queueItemId;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "concept_id", nullable = false)
    private Integer conceptId;

    @Column(name = "done", nullable = false)
    private Boolean done = Boolean.FALSE;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    public LearningQueueItem(Long queueId, Integer position, Integer conceptId) {
        this.queueId = queueId;
        this.position = position;
        this.conceptId = conceptId;
        this.done = Boolean.FALSE;
    }
}
