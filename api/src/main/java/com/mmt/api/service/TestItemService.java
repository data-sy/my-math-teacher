package com.mmt.api.service;

import com.mmt.api.dto.testitem.TestItemConverter;
import com.mmt.api.dto.testitem.TestItemsResponse;
import com.mmt.api.repository.TestItem.TestItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestItemService {

    private final TestItemRepository testItemRepository;

    public TestItemService(TestItemRepository testitemRepository) {
        this.testItemRepository = testitemRepository;
    }

    public List<TestItemsResponse> findTestItems(Long testId){
        return TestItemConverter.convertListToTestItemsResponseList(testItemRepository.findByTestId(testId));
    }

}
