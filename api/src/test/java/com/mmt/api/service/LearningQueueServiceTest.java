package com.mmt.api.service;

import com.mmt.api.domain.diagnosis.LearningQueue;
import com.mmt.api.exception.DiagnosisException;
import com.mmt.api.repository.concept.JdbcTemplateConceptRepository;
import com.mmt.api.repository.diagnosis.LearningQueueItemRepository;
import com.mmt.api.repository.diagnosis.LearningQueueRepository;
import com.mmt.api.repository.diagnosis.SelfReportAnswerRepository;
import com.mmt.api.repository.users.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 학습 큐 소유권 에러 계약 — 위반 = DiagnosisException 403
 * (AccessDeniedException 은 /error 디스패치에서 401 마스킹, residual ④·2026-07-13 E2E 실측).
 * 위상정렬 로직은 LearningQueueTopologyTest 소관.
 */
class LearningQueueServiceTest {

    private LearningQueueRepository queueRepo;
    private LearningQueueService service;

    @BeforeEach
    void setUp() {
        queueRepo = mock(LearningQueueRepository.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        service = new LearningQueueService(mock(DiagnosisAnalysisService.class),
                mock(JdbcTemplateConceptRepository.class), queueRepo,
                mock(LearningQueueItemRepository.class), usersRepo,
                mock(SelfReportAnswerRepository.class));
        when(usersRepo.findUserIdByUserEmail(anyString())).thenReturn(Optional.of(7L));
    }

    @Test
    @DisplayName("타인 큐 done 마킹 = DiagnosisException 403 (401 마스킹 우회)")
    void markDoneOnOthersQueueIsForbidden() {
        LearningQueue queue = new LearningQueue(8L, 100L); // 소유자 = 8, 요청자 = 7
        queue.setQueueId(5L);
        when(queueRepo.findById(5L)).thenReturn(Optional.of(queue));

        assertThatThrownBy(() -> service.markDone("s@t.kr", 5L, 1L))
                .isInstanceOf(DiagnosisException.class)
                .satisfies(e -> assertThat(((DiagnosisException) e).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
