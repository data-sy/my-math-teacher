package com.mmt.api.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M8 spec-03 §3.1 — 개념별 외부 자료 링크의 응답 shape.
 *
 * preview(익명)·GET /diagnosis/{userTestId} 둘 다 동일 부착 — 무료 자료는 로그인 전 충족(F-1).
 * provider 는 프론트가 렌더하지 않아도 되지만, R4 점검·provider 단위 일괄 비노출의 그룹핑 키라
 * 계약에 남긴다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalLink {

    /** 노출 문구 (예: "무료 강의 (EBS)"). */
    private String title;
    private String url;
    private String provider;
}
