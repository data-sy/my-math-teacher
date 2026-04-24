package com.mmt.api.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.MountableFile;

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

    // Neo4j 컨테이너의 /var/lib/neo4j/import 디렉토리에 실제 시드 CSV 를 마운트한다.
    // Spec 02 의 GraphQueryPerformanceTest 가 LOAD CSV 로 프로덕션 수준 데이터를
    // 주입해 현실적인 기준선을 측정할 수 있게 한다. 작은 슬라이스 테스트에는 영향 없음
    // (각 테스트가 필요 시 @BeforeEach 에서 DETACH DELETE 후 자신만의 시드 주입).
    @Bean
    @ServiceConnection
    public Neo4jContainer<?> neo4jContainer() {
        return new Neo4jContainer<>("neo4j:5.12")
            .withoutAuthentication()
            .withCopyFileToContainer(
                MountableFile.forHostPath("../neo4j/init/concepts.csv"),
                "/var/lib/neo4j/import/concepts.csv")
            .withCopyFileToContainer(
                MountableFile.forHostPath("../neo4j/init/knowledge_space.csv"),
                "/var/lib/neo4j/import/knowledge_space.csv")
            .withReuse(true);
    }
}
