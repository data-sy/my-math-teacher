package com.mmt.api.controller;

import com.mmt.api.dto.diagnosis.DiagnosisEntry;
import com.mmt.api.dto.diagnosis.DiagnosisFrontierResponse;
import com.mmt.api.dto.diagnosis.DiagnosisNextRequest;
import com.mmt.api.dto.diagnosis.DiagnosisNextResponse;
import com.mmt.api.dto.diagnosis.DiagnosisResultResponse;
import com.mmt.api.dto.diagnosis.DiagnosisSubmitResponse;
import com.mmt.api.service.DiagnosisAnalysisService;
import com.mmt.api.service.DiagnosisRateLimiter;
import com.mmt.api.service.DiagnosisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M7 spec-01 자가진단 API (§4.1). mmt.diagnosis.enabled=false 면 빈 미등록 → 404
 * (구 동작 그대로 — 즉시 롤백 계약, ADR-0010).
 *
 * F-1 결과-시점 게이트: frontier·next·preview = permitAll(익명 완주, SecurityConfig),
 * 귀속 POST ""·GET /{userTestId} = 인증 + 소유권 (ItemService IDOR 선례).
 */
@RestController
@RequestMapping("/api/v1/diagnosis")
@ConditionalOnProperty(name = "mmt.diagnosis.enabled", havingValue = "true")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;
    private final DiagnosisAnalysisService diagnosisAnalysisService;
    private final DiagnosisRateLimiter rateLimiter;

    public DiagnosisController(DiagnosisService diagnosisService,
                               DiagnosisAnalysisService diagnosisAnalysisService,
                               DiagnosisRateLimiter rateLimiter) {
        this.diagnosisService = diagnosisService;
        this.diagnosisAnalysisService = diagnosisAnalysisService;
        this.rateLimiter = rateLimiter;
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

    /** 익명 결과 preview (④, S1-A 무영속) — TF 실호출 경로라 IP rate limit (§4.6) */
    @PostMapping("/preview")
    public DiagnosisResultResponse preview(@RequestBody DiagnosisNextRequest request,
                                           HttpServletRequest httpRequest) {
        rateLimiter.checkPreview(httpRequest);
        return diagnosisAnalysisService.previewResult(request.getAnswered());
    }

    /** 귀속 저장 (게이트) — preview 와 동일 입력이 동일 결과를 내는 결정론이 계약 */
    @PostMapping("")
    public DiagnosisSubmitResponse submit(@RequestBody DiagnosisNextRequest request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        DiagnosisAnalysisService.SubmitOutcome outcome =
                diagnosisAnalysisService.submitResult(userEmail, request.getAnswered());
        return new DiagnosisSubmitResponse(outcome.userTestId(), outcome.result());
    }

    /** 귀속 결과 재조회 — 소유권 검사 */
    @GetMapping("/{userTestId}")
    public DiagnosisResultResponse getResult(@PathVariable Long userTestId) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return diagnosisAnalysisService.getResult(userTestId, userEmail);
    }
}
