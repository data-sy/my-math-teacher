package com.mmt.api.performanceTest;

import com.mmt.api.domain.Item;
import com.mmt.api.dto.item.PersonalItemConverter;
import com.mmt.api.dto.item.PersonalItemsResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class PerformanceTestService {
    private final PerformanceTestRepository performanceTestRepository;

    public PerformanceTestService(PerformanceTestRepository performanceTestRepository) {
        this.performanceTestRepository = performanceTestRepository;
    }

    public PersonalItemsResponse originalQuery(Long conceptId) {
        return PersonalItemConverter.convertToPersonalItemsResponse(performanceTestRepository.findOneByConceptId(conceptId));
    }

    public PersonalItemsResponse javaSort(Long conceptId) {
        List<Item> itemList = performanceTestRepository.findListByConceptId(conceptId);
        Random random = new Random();
        int randomIndex = random.nextInt(itemList.size());
        return PersonalItemConverter.convertToPersonalItemsResponse(itemList.get(randomIndex));
    }

    public PersonalItemsResponse javaRandomFetch(Long conceptId) {
        List<Long> itemIdList = performanceTestRepository.findItemIdByConceptId(conceptId);
        Random random = new Random();
        long itemId = random.nextInt(itemIdList.size());
        return PersonalItemConverter.convertToPersonalItemsResponse(performanceTestRepository.findOneByItemId(itemId));
    }

    public PersonalItemsResponse dbOptimized(Long conceptId) {
        return PersonalItemConverter.convertToPersonalItemsResponse(performanceTestRepository.findByConceptIdOpti(conceptId));
    }
}
