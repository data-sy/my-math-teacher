package com.mmt.api.controller;

import com.mmt.api.dto.diagnosis.DiagnosisEntry;
import com.mmt.api.dto.diagnosis.DiagnosisFrontierResponse;
import com.mmt.api.dto.diagnosis.DiagnosisNextRequest;
import com.mmt.api.dto.diagnosis.DiagnosisNextResponse;
import com.mmt.api.service.DiagnosisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M7 spec-01 자가진단 API (§4.1). mmt.diagnosis.enabled=false 면 빈 미등록 → 404
 * (구 동작 그대로 — 즉시 롤백 계약, ADR-0010). frontier·next 는 permitAll(익명 문답,
 * F-1 결과-시점 게이트) — SecurityConfig 참조.
 */
@RestController
@RequestMapping("/api/v1/diagnosis")
@ConditionalOnProperty(name = "mmt.diagnosis.enabled", havingValue = "true")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /** 시작 프론티어 (② 영역 진입, F-3) */
    @PostMapping("/frontier")
    public DiagnosisFrontierResponse frontier(@RequestBody DiagnosisEntry entry) {
        return diagnosisService.frontier(entry);
    }

    /** 적응형 순회 — 다음 질문 1개 (③ 문답, 무상태) */
    @PostMapping("/next")
    public DiagnosisNextResponse next(@RequestBody DiagnosisNextRequest request) {
        return diagnosisService.next(request.getEntry(), request.getAnswered());
    }
}
