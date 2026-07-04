package com.mmt.api.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * M4 spec-01 R1 전용: <b>Neo4j 없이</b> MySQL 컨테이너만 띄운다.
 * 기본 {@link TestcontainersConfig} 는 Neo4j 컨테이너도 함께 올리므로 "Neo4j 부재" 시나리오를
 * 재현할 수 없다. 이 설정은 datasource/JPA 기동에 필요한 MySQL 만 제공하고, Neo4j 연결정보는
 * 테스트가 더미 props 로 주입한다(드라이버 lazy connect).
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlOnlyTestcontainersConfig {

    @Bean
    @ServiceConnection
    public MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mmt_test")
            .withReuse(true);
    }
}
