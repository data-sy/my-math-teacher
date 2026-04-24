package com.mmt.api.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M1 Spec 03 Task 3.4: MeterRegistry 수동 등록.
 *
 * Spring Boot Actuator 를 도입하지 않은 상태이므로 MeterRegistry 자동 설정 bean 이 없다.
 * {@link QueryTimingAspect} 가 Timer 를 기록할 MeterRegistry 를 수동으로 제공한다.
 *
 * 추후 Prometheus · Grafana 연동이 로드맵에 들어오면 {@code spring-boot-starter-actuator}
 * 로 승급하고 이 bean 은 제거한다.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
