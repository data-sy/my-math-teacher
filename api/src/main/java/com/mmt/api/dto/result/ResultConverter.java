package com.mmt.api.dto.result;

import com.mmt.api.domain.Result;

import java.util.ArrayList;
import java.util.List;

public class ResultConverter {

    public static ResultResponse convertToResultResponse(Result result) {
        ResultResponse resultResponse = new ResultResponse();
        resultResponse.setProbabilityId(result.getProbabilityId());
        resultResponse.setTestItemNumber(result.getTestItemNumber());
        resultResponse.setConceptId(result.getConceptId());
        resultResponse.setToConceptDepth(result.getToConceptDepth());
        resultResponse.setProbabilityPercent(result.getProbabilityPercent());
        resultResponse.setConceptName(result.getConceptName());
        resultResponse.setLevel(result.getSchoolLevel() + '-' + result.getGradeLevel() + '-' + result.getSemester());
        resultResponse.setChapter(result.getChapterMain() + '-' + result.getChapterSub() + '-' + result.getChapterName());
        return resultResponse;
    }
    public static List<ResultResponse> convertListToResultResponseList(List<Result> resultList) {
        List<ResultResponse> responseList = new ArrayList<>();
        for (Result result : resultList) {
            responseList.add(convertToResultResponse(result));
        }
        return responseList;
    }

//    public static List<ResultResponse> convertListToResultResponseList(List<Result> resultList) {
//        Map<Integer, List<Result>> groupedByTestItemNumber = resultList.stream()
//                .collect(Collectors.groupingBy(Result::getTestItemNumber));
//
//        List<ResultResponse> resultResponses = resultList.stream()
//                .collect(Collectors.groupingBy(Result::getTestItemNumber))
//                .entrySet().stream()
//                .flatMap(entry -> {
//                    List<Result> groupedResults = entry.getValue();
//                    List<Result> zeroDepthResults = groupedResults.stream()
//                            .filter(r -> r.getToConceptDepth() == 0)
//                            .collect(Collectors.toList());
//
//                    List<ResultResponse> nonZeroDepthResults = groupedResults.stream()
//                            .filter(r -> r.getToConceptDepth() > 0)
//                            .map(ResultConverter::convertToResultResponse)
//                            .collect(Collectors.toList());
//
//                    return zeroDepthResults.stream().map(zeroDepthResult -> {
//                        ResultResponse resultResponse = ResultConverter.convertToResultResponse(zeroDepthResult);
//                        resultResponse.setPrerequisiteList(nonZeroDepthResults);
//                        return resultResponse;
//                    });
//                })
//                .collect(Collectors.toList());
//
//        return resultResponses;
//    }
//
//    private static ResultResponse convertToResultResponse(Result result) {
//        ResultResponse resultResponse = new ResultResponse();
//        resultResponse.setProbabilityId(result.getProbabilityId());
//        resultResponse.setTestItemNumber(result.getTestItemNumber());
//        resultResponse.setConceptId(result.getConceptId());
//        resultResponse.setToConceptDepth(result.getToConceptDepth());
//        resultResponse.setProbabilityPercent(result.getProbabilityPercent());
//        resultResponse.setConceptName(result.getConceptName());
//        resultResponse.setSchoolLevel(result.getSchoolLevel());
//        resultResponse.setGradeLevel(result.getGradeLevel());
//        resultResponse.setSemester(result.getSemester());
//        resultResponse.setChapterMain(result.getChapterMain());
//        resultResponse.setChapterSub(result.getChapterSub());
//        resultResponse.setChapterName(result.getChapterName());
//        return resultResponse;
//    }

}

