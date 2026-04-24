package com.mmt.api.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Neo4jContainer;

// TODO(readme): 로컬에서 .withReuse(true) 효과를 보려면 개발자가
// ~/.testcontainers.properties 에 `testcontainers.reuse.enable=true` 를
// 직접 추가해야 한다. README 업데이트 시 반영할 것.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mmt_test")
            .withReuse(true);
    }

    @Bean
    @ServiceConnection
    public Neo4jContainer<?> neo4jContainer() {
        return new Neo4jContainer<>("neo4j:5.12")
            .withoutAuthentication()
            .withReuse(true);
    }
}
