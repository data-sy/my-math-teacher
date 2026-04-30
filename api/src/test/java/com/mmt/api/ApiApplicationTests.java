package com.mmt.api;

import com.mmt.api.config.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
