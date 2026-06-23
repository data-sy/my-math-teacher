package com.mmt.api.repository.item;

import com.mmt.api.domain.Item;

import java.util.List;

public interface ItemRepository {

    Item findByConceptId(int conceptId);

    /**
     * 재출제(Scope B)용 — 학생이 원래 응시한 문항을 그대로 조회한다.
     * @param onlyWrong true 면 오답 문항(answer_code=0)만, false 면 응시 문항 전체.
     */
    List<Item> findOriginalItemsByUserTestId(Long userTestId, boolean onlyWrong);

    /**
     * 범위 채우기(Scope B)용 — 주어진 개념들에 속한 문항 전체를 랜덤 순서로 조회한다.
     * count 채우기 시 개념당 복수 문항을 round-robin 으로 뽑기 위한 풀.
     */
    List<Item> findItemsByConceptIds(List<Integer> conceptIds);
}
