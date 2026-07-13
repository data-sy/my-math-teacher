package com.mmt.api.domain.diagnosis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * M7 spec-01 자가진단 답안 (S2=C — 구 answers 무접촉 신규 테이블).
 *
 * self_report_answer_id ASC = 답변 입력 순서 (IDENTITY 채번이 저장 순서를 보존) —
 * DKT 시퀀스 재구성의 정본 순서이므로 이 계약을 깨는 배치/재정렬 저장 금지 (spec-01 §4.4-2).
 *
 * users_tests·concepts 로의 FK 는 평컬럼으로만 모델링 — 실 FK 제약은 api/sql/create.sql
 * 에만 둔다 (테스트 ddl-auto 가 비엔티티 테이블 FK 를 만들 수 없음, ADR-0010 Decision-3).
 */
@Entity
@Table(name = "self_report_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_sra_session_concept",
                columnNames = {"user_test_id", "concept_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SelfReportAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "self_report_answer_id")
    private Long selfReportAnswerId;

    @Column(name = "user_test_id", nullable = false)
    private Long userTestId;

    @Column(name = "concept_id", nullable = false)
    private Integer conceptId;

    /** 안다=true / 모른다=false. 정오답 변환(1/0)은 DKT 시퀀스 생성 시. */
    @Column(name = "known", nullable = false)
    private Boolean known;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public SelfReportAnswer(Long userTestId, Integer conceptId, Boolean known) {
        this.userTestId = userTestId;
        this.conceptId = conceptId;
        this.known = known;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
