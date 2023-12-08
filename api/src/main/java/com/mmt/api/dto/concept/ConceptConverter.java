package com.mmt.api.dto.concept;

import com.mmt.api.domain.Concept;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ConceptConverter {

    public static Mono<ConceptResponse> convertToMonoConceptResponse(Mono<Concept> concept){
        return concept.map(c -> {
            ConceptResponse response = new ConceptResponse();
            response.setConceptId(c.getConceptId());
            response.setConceptName(c.getName());
            response.setConceptDescription(c.getDesc());
            response.setConceptSchoolLevel(c.getSchoolLevel());
            response.setConceptGradeLevel(c.getGradeLevel());
            response.setConceptSemester(c.getSemester());
            response.setConceptChapterId(c.getChapterId());
            response.setConceptChapterMain(c.getChapterMain());
            response.setConceptChapterSub(c.getChapterSub());
            response.setConceptChapterName(c.getChapterName());
            response.setConceptAchievementId(c.getAchievementId());
            response.setConceptAchievementName(c.getAchievementName());
            response.setConceptSection(c.getSection());
            return response;
        });
    }

    public static Flux<ConceptResponse> convertToFluxConceptResponse(Flux<Concept> concept){
        return concept.map(c -> {
            ConceptResponse response = new ConceptResponse();
            response.setConceptId(c.getConceptId());
            response.setConceptName(c.getName());
            response.setConceptDescription(c.getDesc());
            response.setConceptSchoolLevel(c.getSchoolLevel());
            response.setConceptGradeLevel(c.getGradeLevel());
            response.setConceptSemester(c.getSemester());
            response.setConceptChapterId(c.getChapterId());
            response.setConceptChapterMain(c.getChapterMain());
            response.setConceptChapterSub(c.getChapterSub());
            response.setConceptChapterName(c.getChapterName());
            response.setConceptAchievementId(c.getAchievementId());
            response.setConceptAchievementName(c.getAchievementName());
            response.setConceptSection(c.getSection());
            return response;
        });
    }

}
