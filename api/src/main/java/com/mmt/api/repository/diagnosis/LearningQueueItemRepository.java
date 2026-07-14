package com.mmt.api.repository.diagnosis;

import com.mmt.api.domain.diagnosis.LearningQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningQueueItemRepository extends JpaRepository<LearningQueueItem, Long> {

    List<LearningQueueItem> findByQueueIdOrderByPositionAsc(Long queueId);

    Optional<LearningQueueItem> findByQueueItemIdAndQueueId(Long queueItemId, Long queueId);
}
