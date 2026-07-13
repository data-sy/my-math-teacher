package com.mmt.api.repository.diagnosis;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.domain.diagnosis.LearningQueue;
import com.mmt.api.domain.diagnosis.LearningQueueItem;
import com.mmt.api.domain.diagnosis.SelfReportAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M7 spec-01 신규 JPA 엔티티의 실 MySQL 매핑 검증 (Testcontainers).
 * self_report_answer_id ASC == 저장 순서 (IDENTITY 채번) — 결정론 계약의 영속 반쪽.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
class DiagnosisJpaEntitiesTest {

    @Autowired
    SelfReportAnswerRepository selfReportAnswerRepository;
    @Autowired
    LearningQueueRepository learningQueueRepository;
    @Autowired
    LearningQueueItemRepository learningQueueItemRepository;

    @Test
    @DisplayName("self_report_answers: 저장 순서 == self_report_answer_id ASC (§4.4-2)")
    void insertionOrderEqualsIdAscOrder() {
        List<Integer> inputOrder = List.of(30, 7, 121, 44); // 정렬돼 있지 않은 입력 순서
        for (int conceptId : inputOrder) {
            selfReportAnswerRepository.save(new SelfReportAnswer(900L, conceptId, conceptId % 2 == 0));
        }
        List<SelfReportAnswer> reread =
                selfReportAnswerRepository.findByUserTestIdOrderBySelfReportAnswerIdAsc(900L);
        assertThat(reread).extracting(SelfReportAnswer::getConceptId)
                .containsExactlyElementsOf(inputOrder);
        assertThat(reread).extracting(SelfReportAnswer::getCreatedAt).doesNotContainNull();
    }

    @Test
    @DisplayName("세션 내 개념당 1답 UNIQUE 제약이 테스트 스키마에도 강제된다")
    void uniqueSessionConceptEnforced() {
        selfReportAnswerRepository.saveAndFlush(new SelfReportAnswer(901L, 10, true));
        assertThatThrownBy(() ->
                selfReportAnswerRepository.saveAndFlush(new SelfReportAnswer(901L, 10, false)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("learning_queues/items 왕복: 활성 큐 = 최신 queue_id, position ASC 재조회")
    void queueRoundTrip() {
        LearningQueue first = learningQueueRepository.save(new LearningQueue(5L, 900L));
        LearningQueue second = learningQueueRepository.save(new LearningQueue(5L, 901L));
        learningQueueItemRepository.save(new LearningQueueItem(second.getQueueId(), 2, 20));
        learningQueueItemRepository.save(new LearningQueueItem(second.getQueueId(), 1, 10));

        assertThat(learningQueueRepository.findTopByUserIdOrderByQueueIdDesc(5L))
                .hasValueSatisfying(q -> assertThat(q.getQueueId()).isEqualTo(second.getQueueId()));
        List<LearningQueueItem> items =
                learningQueueItemRepository.findByQueueIdOrderByPositionAsc(second.getQueueId());
        assertThat(items).extracting(LearningQueueItem::getConceptId).containsExactly(10, 20);
        assertThat(items).allSatisfy(i -> assertThat(i.getDone()).isFalse());
        assertThat(first.getQueueId()).isLessThan(second.getQueueId());
    }
}
