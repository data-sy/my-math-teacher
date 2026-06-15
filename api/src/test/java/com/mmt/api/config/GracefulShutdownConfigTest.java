package com.mmt.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 spec-01 A.5: graceful shutdown 설정 회귀 가드.
 *
 * <p>🟡 초록의 의미 고정 — 이 테스트가 닫는 것은 <b>"두 프로퍼티가 유효하고 컨텍스트가 그걸 안고
 * 기동한다"(설정 회귀 방지)</b>까지다. {@code webEnvironment=MOCK} 라 실제 톰캣이 안 떠
 * <b>드레인이 502 를 막는지는 검증하지 않는다</b> — 그 불변식은 spec-01 §4 유실 grader / G4 가
 * 배포 때 소유한다.
 *
 * <p>Testcontainers(MySQL+Neo4j) 기반이라 securelocal/로컬 인프라에 비의존하며 CI 에서 돈다.
 * ({@code application.yml} 의 {@code spring.profiles.include: securelocal} 은 {@code @ServiceConnection}
 * 이 datasource/neo4j 연결을 컨테이너로 오버라이드하므로 무해.)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class GracefulShutdownConfigTest {

    @Autowired
    private Environment env;

    @Test
    void gracefulShutdownPropertiesLoadOnBoot() {
        assertThat(env.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
    }

}
