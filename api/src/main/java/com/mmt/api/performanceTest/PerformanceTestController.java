package com.mmt.api.performanceTest;

import com.mmt.api.dto.item.PersonalItemsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/perf-test")
public class PerformanceTestController {

    private final PerformanceTestService performanceTestService;
    public PerformanceTestController(PerformanceTestService performanceTestService) {
        this.performanceTestService = performanceTestService;
    }

    /**
     * 맞춤 API에서 conceptId에 따른 item을 랜덤으로 추출하는 부분 성능 테스트
     */
    @GetMapping("/originalQuery")
    public PersonalItemsResponse originalQuery(@RequestParam("conceptId") Long conceptId) {
        return performanceTestService.originalQuery(conceptId);
    }

    @GetMapping("/javaSort")
    public PersonalItemsResponse javaSort(@RequestParam("conceptId") Long conceptId) {
        return performanceTestService.javaSort(conceptId);
    }

    @GetMapping("/javaRandomFetch")
    public PersonalItemsResponse javaRandomFetch(@RequestParam("conceptId") Long conceptId) {
        return performanceTestService.javaRandomFetch(conceptId);
    }

    @GetMapping("/dbOptimized")
    public PersonalItemsResponse dbOptimized(@RequestParam("conceptId") Long conceptId) {
        return performanceTestService.dbOptimized(conceptId);
    }


}
