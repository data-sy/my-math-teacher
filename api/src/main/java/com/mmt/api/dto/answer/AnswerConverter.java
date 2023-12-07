package com.mmt.api.dto.answer;

import com.mmt.api.domain.Answer;
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

    public static Answer convertToAnswer(AnswerCreateRequest request) {
        Answer answer = new Answer();
        answer.setUserTestId(request.getUserTestId());
        answer.setAnswerCodeList(convertListToAnswerCodeList(request.getAnswerCodeCreateRequestList()));
        return answer;
    }

    public static List<Integer> convertToIntegerList(AnswerCode answerCode){
        List<Integer> integerList = new ArrayList<>();
        integerList.add(answerCode.getSkillId());
        integerList.add(answerCode.getAnswerCode());
        return integerList;
    }

}
