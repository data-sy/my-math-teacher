package com.mmt.api.dto.concept;

import lombok.Getter;
import lombok.Setter;

/**
 * 개념 검색(GET /api/v1/concepts/search) 결과용 경량 DTO.
 * 자동완성 드롭다운·breadcrumb·그래프 점프에 필요한 최소 필드만 담는다
 * (concept_description 등 상세는 제외 — 상세는 /{conceptId} 로 별도 조회).
 */
@Getter
@Setter
public class ConceptSearchResponse {
    private int conceptId;
    private String conceptName;
    private String conceptSchoolLevel;
    private String conceptGradeLevel;
    private String conceptSemester;
    private String conceptChapterName;
}
