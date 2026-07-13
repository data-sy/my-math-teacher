package com.mmt.api.dto.diagnosis;

import lombok.Data;

/**
 * 영역 진입(spec-01 §4.1 ②): chapterId 단원 시작 또는 scope="full"(학교급 전체 훑기 escape).
 * 둘 중 하나만 유효 — 검증은 DiagnosisService.resolveFrontier.
 */
@Data
public class DiagnosisEntry {
    private Integer chapterId;
    private String scope;        // "full" 이면 schoolLevel 필수
    private String schoolLevel;
}
