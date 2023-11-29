package com.mmt.api.repository.TestItem;

import com.mmt.api.domain.TestItems;

import java.util.List;

public interface TestItemRepository {

    List<TestItems> findByTestId(Long testId);

}
