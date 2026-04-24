package com.mmt.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M1 Spec 03 Task 3.1: 피처 플래그 · 관측성 설정의 기본값이 실제로 주입되는지 검증.
 *
 *  - application.yml 에 추가된 mmt.migration.*, mmt.observability.* 가
 *    @Value 로 주입되는지 확인.
 *  - test 프로파일 활성 상태에서 공통 기본값 (false / 100ms) 그대로 상속됨을 확인.
 *  - 플래그 별 실제 토글 동작은 Task 3.2 의 ConceptServiceFeatureFlagTest 에서 다룬다.
 *
 * TestcontainersConfig 를 @Import 하는 이유:
 *  - 현 application.yml 이 spring.profiles.include: securelocal 로 항상 securelocal 을
 *    포함 → @ActiveProfiles("test") 여도 securelocal 의 MySQL/Neo4j URL 이 주입됨.
 *  - Testcontainers 의 @ServiceConnection 이 해당 URL 을 오버라이드해 로컬 인프라
 *    미기동 상태에서도 컨텍스트가 부팅되게 함. Redis 는 Lettuce lazy connection 이라
 *    기동만 하면 통과.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
class FeatureFlagIntegrationTest {

    @Value("${mmt.migration.use-mysql-cte-for-graph}")
    private boolean useMysqlCte;

    @Value("${mmt.migration.use-jpa-for-tests}")
    private boolean useJpaForTests;

    @Value("${mmt.migration.use-jpa-for-concepts}")
    private boolean useJpaForConcepts;

    @Value("${mmt.observability.slow-query-threshold-ms}")
    private long slowQueryThresholdMs;

    @Test
    void defaultFlagValuesAreLoaded() {
        assertThat(useMysqlCte).isFalse();
        assertThat(useJpaForTests).isFalse();
        assertThat(useJpaForConcepts).isFalse();
        assertThat(slowQueryThresholdMs).isEqualTo(100L);
    }
}
