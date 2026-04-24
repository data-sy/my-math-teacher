package com.mmt.api.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * M1 Spec 03 Task 3.4: 리포지토리 쿼리 시간 측정 AOP.
 *
 * 동작:
 *  - `com.mmt.api.repository..*` 하위 모든 메서드에 @Around 로 개입
 *  - 메서드별 Timer ("mmt.query.time" + method 태그) 에 실행 시간 기록
 *  - 임계치 (mmt.observability.slow-query-threshold-ms) 초과 시 WARN 로깅
 *
 * 한계:
 *  - Reactive 리포지토리 (예: ConceptRepository) 는 @Around 가 Flux/Mono 조립 시간만 측정.
 *    실제 Neo4j 쿼리 실행 시간은 측정되지 않음. 정확한 측정이 필요하면 Mono/Flux 에
 *    doOnTerminate 로 타이밍을 wiring 하는 별도 구현 필요 (Milestone 2 이후 검토).
 *  - 블로킹 JdbcTemplate 기반 리포지토리에서는 실행 시간을 정확히 반영.
 */
@Aspect
@Component
@Slf4j
public class QueryTimingAspect {

    private final MeterRegistry meterRegistry;
    private final long slowQueryThresholdMs;

    public QueryTimingAspect(
        MeterRegistry meterRegistry,
        @Value("${mmt.observability.slow-query-threshold-ms:100}") long slowQueryThresholdMs
    ) {
        this.meterRegistry = meterRegistry;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    @Around("execution(* com.mmt.api.repository..*(..))")
    public Object measureQueryTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        String methodName = joinPoint.getSignature().toShortString();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - startNanos;
            long elapsedMs = elapsedNanos / 1_000_000;

            meterRegistry.timer("mmt.query.time", "method", methodName)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

            if (elapsedMs > slowQueryThresholdMs) {
                log.warn("느린 쿼리 감지: {} ({}ms)", methodName, elapsedMs);
            }
        }
    }
}
