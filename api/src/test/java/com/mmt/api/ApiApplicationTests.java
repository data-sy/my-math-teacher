package com.mmt.api;

import com.mmt.api.config.MySqlOnlyTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 전체 컨텍스트 기동 스모크.
 *
 * <p><b>CI 이식성</b> — 프로파일을 안 붙이면 {@code spring.profiles.default: securelocal} 이 걸려
 * gitignore 된 파일에 의존한다. CI 엔 그 파일이 없어 DataSource 가 만들어지지 않는다
 * ({@code DataSourceBeanCreationException}). 정본: {@code docs/backlog/test-suite-not-portable-to-ci.md}
 *
 * <p><b>왜 MySQL 만 띄우나</b> — 프로덕션이 M2 이후 CTE-only + 더미 {@code GDB_*} 로 뜨는 형상과
 * 같게 맞춘 것이다. Neo4j 리포지토리·드라이버 빈은 여기서도 그대로 배선되고(드라이버 lazy connect)
 * 실제 접속만 하지 않는다 — 그 접속은 어느 환경도 하지 않으므로 실 Neo4j 컨테이너를 띄우면
 * 존재하지 않는 형상을 검증하면서 러너 자원만 먹는다(원인 C). 접속 부재 상태의 기동 무결성은
 * {@link Neo4jAbsentBootSmokeTest} 가 플래그 ON 쪽에서 함께 지킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlOnlyTestcontainersConfig.class)
@TestPropertySource(properties = {
	"spring.neo4j.uri=bolt://localhost:7687",
	"spring.neo4j.authentication.username=neo4j",
	"spring.neo4j.authentication.password=dummy"
})
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
