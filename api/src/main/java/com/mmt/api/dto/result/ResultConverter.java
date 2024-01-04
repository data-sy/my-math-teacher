package com.mmt.api.dto.result;

import com.mmt.api.domain.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ResultConverter {

    public static List<ResultResponse> convertListToResultResponseList(List<Result> resultList) {
        Map<Integer, List<Result>> groupedByTestItemNumber = resultList.stream()
                .collect(Collectors.groupingBy(Result::getTestItemNumber));

        List<ResultResponse> resultResponses = resultList.stream()
                .collect(Collectors.groupingBy(Result::getTestItemNumber))
                .entrySet().stream()
                .flatMap(entry -> {
                    List<Result> groupedResults = entry.getValue();
                    List<Result> zeroDepthResults = groupedResults.stream()
                            .filter(r -> r.getToConceptDepth() == 0)
                            .collect(Collectors.toList());

                    List<ResultResponse> nonZeroDepthResults = groupedResults.stream()
                            .filter(r -> r.getToConceptDepth() > 0)
                            .map(ResultConverter::convertToResultResponse)
                            .collect(Collectors.toList());

                    return zeroDepthResults.stream().map(zeroDepthResult -> {
                        ResultResponse resultResponse = ResultConverter.convertToResultResponse(zeroDepthResult);
                        resultResponse.setPrerequisiteList(nonZeroDepthResults);
                        return resultResponse;
                    });
                })
                .collect(Collectors.toList());

        return resultResponses;
    }

    private static ResultResponse convertToResultResponse(Result result) {
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

