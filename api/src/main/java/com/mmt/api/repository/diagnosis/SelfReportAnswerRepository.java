package com.mmt.api.repository.diagnosis;

import com.mmt.api.domain.diagnosis.SelfReportAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * M7 spec-01 — 신규 리포지토리는 JPA (api/CLAUDE.md 영속성 규칙).
 */
public interface SelfReportAnswerRepository extends JpaRepository<SelfReportAnswer, Long> {

    /** 귀속 시퀀스 재구성 정본 순서: self_report_answer_id ASC = 답변 입력 순서 (spec-01 §4.4-2). */
    List<SelfReportAnswer> findByUserTestIdOrderBySelfReportAnswerIdAsc(Long userTestId);
}
