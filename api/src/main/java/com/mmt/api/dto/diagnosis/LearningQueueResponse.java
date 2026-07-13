package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 통합 학습 큐 응답 (spec-01 §4.6). current = position 순 첫 done=false (파생값, S5).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningQueueResponse {
    private Long queueId;
    private Long userTestId;
    private List<QueueItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueItem {
        private Long queueItemId;
        private int position;
        private int conceptId;
        private String conceptName;
        private boolean done;
        private boolean current;
    }
}
