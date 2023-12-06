package com.mmt.api.repository.testItem;

import com.mmt.api.domain.TestItems;

import java.util.List;

public interface TestItemRepository {

    List<TestItems> findByTestId(Long testId);

}
