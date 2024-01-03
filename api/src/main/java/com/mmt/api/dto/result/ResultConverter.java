package com.mmt.api.dto.result;

import com.mmt.api.domain.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultConverter {
    public static List<ResultResponse> convertListToResultResponseList(List<Result> resultList) {
        Map<Integer, ResultResponse> resultResponseMap = new HashMap<>();
        Map<Integer, List<ResultResponse>> prerequisiteMap = new HashMap<>();

        for (Result result : resultList) {
            int conceptId = result.getConceptId();
            ResultResponse response = copyResultToResponse(result);

            if (result.getToConceptDepth() == 0) {
                resultResponseMap.put(conceptId, response);
            } else {
                prerequisiteMap.putIfAbsent(conceptId, new ArrayList<>());
                prerequisiteMap.get(conceptId).add(response);
            }
        }

        for (Map.Entry<Integer, ResultResponse> entry : resultResponseMap.entrySet()) {
            int conceptId = entry.getKey();
            ResultResponse response = entry.getValue();
            if (prerequisiteMap.containsKey(conceptId)) {
                response.setPrerequisiteList(prerequisiteMap.get(conceptId));
            }
        }

        return new ArrayList<>(resultResponseMap.values());
    }


    public static ResultResponse copyResultToResponse(Result result) {
        ResultResponse resultResponse = new ResultResponse();
        resultResponse.setProbabilityId(result.getProbabilityId());
        resultResponse.setTestItemNumber(result.getTestItemNumber());
        resultResponse.setConceptId(result.getConceptId());
        resultResponse.setToConceptDepth(result.getToConceptDepth());
        resultResponse.setProbabilityPercent(result.getProbabilityPercent());
        resultResponse.setConceptName(result.getConceptName());
        resultResponse.setSchoolLevel(result.getSchoolLevel());
        resultResponse.setGradeLevel(result.getGradeLevel());
        resultResponse.setSemester(result.getSemester());
        resultResponse.setChapterMain(result.getChapterMain());
        resultResponse.setChapterSub(result.getChapterSub());
        resultResponse.setChapterName(result.getChapterName());
        return resultResponse;
    }

}

