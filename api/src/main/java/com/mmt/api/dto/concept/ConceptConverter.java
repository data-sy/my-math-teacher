package com.mmt.api.dto.concept;

import com.mmt.api.domain.Concept;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

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

    public static ConceptNameResponse convertToConceptNameResponse(Concept concept) {
        ConceptNameResponse conceptResponse = new ConceptNameResponse();
        conceptResponse.setConceptId(concept.getConceptId());
        conceptResponse.setConceptName(concept.getName());
        return conceptResponse;
    }

    public static List<ConceptNameResponse> convertListToConceptNameResponseList(List<Concept> conceptList) {
        List<ConceptNameResponse> responseList = new ArrayList<>();
        for (Concept concept : conceptList) {
            responseList.add(convertToConceptNameResponse(concept));
        }
        return responseList;
    }

    public static ConceptResponse convertToConceptResponse(Concept concept){
        ConceptResponse conceptResponse = new ConceptResponse();
        conceptResponse.setConceptId(concept.getConceptId());
        conceptResponse.setConceptName(concept.getName());
        conceptResponse.setConceptDescription(concept.getDesc());
        conceptResponse.setConceptSchoolLevel(concept.getSchoolLevel());
        conceptResponse.setConceptGradeLevel(concept.getGradeLevel());
        conceptResponse.setConceptSemester(concept.getSemester());
        conceptResponse.setConceptChapterMain(concept.getChapterMain());
        conceptResponse.setConceptChapterSub(concept.getChapterSub());
        conceptResponse.setConceptChapterName(concept.getChapterName());
        conceptResponse.setConceptAchievementName(concept.getAchievementName());
        return conceptResponse;
    }
}
