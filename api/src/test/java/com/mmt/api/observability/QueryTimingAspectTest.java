package com.mmt.api.observability;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.service.ConceptService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M1 Spec 03 Task 3.4: {@link QueryTimingAspect} 가 리포지토리 호출 시 Timer 를 기록하는지 검증.
 *
 *  - slow-query-threshold-ms=0 으로 설정하면 1ms 이상 걸린 쿼리마다 WARN 로그가 발생하지만
 *    본 테스트는 WARN 로그 자체가 아닌 Timer 기록 여부 (count > 0) 만 단언한다.
 *  - ConceptService.findNodesIdByConceptIdDepth3 → ConceptRepository.findNodesIdByConceptIdDepth3
 *    호출이 Around 에 걸려 "mmt.query.time" Timer 가 기록되는 것을 확인.
 *  - Reactive repository 는 Flux 조립 시간만 측정되지만 Timer.count() 증가에는 영향 없음.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "mmt.observability.slow-query-threshold-ms=0")
@Testcontainers
class QueryTimingAspectTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void aspectRecordsTimerMetric() {
        // 호출 자체로 ConceptRepository.findNodesIdByConceptIdDepth3 가 proxy 를 통해 실행됨.
        // Flux 는 lazy 이지만 Around 는 메서드 진입 시점에 이미 record 완료.
        conceptService.findNodesIdByConceptIdDepth3(4979);

        Timer timer = meterRegistry.find("mmt.query.time").timer();
        assertThat(timer)
            .as("mmt.query.time Timer 가 등록되어야 함 (QueryTimingAspect 가 intercept 해야 기록됨)")
            .isNotNull();
        assertThat(timer.count())
            .as("최소 1 회 이상 기록되어야 함")
            .isGreaterThan(0);
    }
}
