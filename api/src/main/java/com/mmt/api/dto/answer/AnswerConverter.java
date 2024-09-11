package com.mmt.api.dto.answer;

import com.mmt.api.domain.AnswerSave;
import com.mmt.api.domain.AnswerCode;

import java.util.ArrayList;
import java.util.List;

public class AnswerConverter {
    public static AnswerCode convertToAnswerCode(AnswerCodeCreateRequest request){
        AnswerCode answerCode = new AnswerCode();
        answerCode.setItemId(request.getItemId());
        answerCode.setAnswerCode(request.getAnswerCode());
        return answerCode;
    }

    public static List<AnswerCode> convertListToAnswerCodeList(List<AnswerCodeCreateRequest> requestList) {
        List<AnswerCode> answerCodeList = new ArrayList<>();
        for (AnswerCodeCreateRequest request : requestList) {
            answerCodeList.add(convertToAnswerCode(request));
        }
        return answerCodeList;
    }

    public static AnswerSave convertToAnswer(AnswerCreateRequest request) {
        AnswerSave answerSave = new AnswerSave();
        answerSave.setUserTestId(request.getUserTestId());
        answerSave.setAnswerCodeList(convertListToAnswerCodeList(request.getAnswerCodeCreateRequestList()));
        return answerSave;
    }

    public static int[] convertToIntArray(AnswerCode answerCode) {
        return new int[]{answerCode.getSkillId(), answerCode.getAnswerCode()};
    }

    // deprecated : 플라스크 서버 때 사용
//    public static List<Integer> convertToIntegerList(AnswerCode answerCode){
//        List<Integer> integerList = new ArrayList<>();
//        integerList.add(answerCode.getSkillId());
//        integerList.add(answerCode.getAnswerCode());
//        return integerList;
//    }

}
