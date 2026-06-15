package com.mmt.api;

import com.mmt.api.config.MySqlOnlyTestcontainersConfig;
import com.mmt.api.service.ConceptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 spec-01 R1: <b>가장 위험한 가정의 로컬 판정.</b>
 *
 * <p>검증하는 명제 — "피처 플래그 {@code use-mysql-cte-for-graph=true} + Neo4j 컨테이너 부재 +
 * 더미 {@code GDB_*}(spring.neo4j.*) 상황에서 <b>풀 애플리케이션 컨텍스트가 기동되는가</b>".
 * flag ON 으로 런타임 그래프 호출은 전부 MySQL CTE 로 우회되지만(§8 확인), {@code conceptRepository}
 * (ReactiveNeo4jRepository) 빈은 여전히 주입되어 Spring 이 Neo4j Driver 빈을 만든다 — 이 빈 생성이
 * Neo4j 미가동 상태에서 기동을 깨뜨리지 않는지가 R1 의 미검증 지점이었다(드라이버 lazy connect 라
 * 대체로 뜨지만 실측 필요). 이 테스트가 그 답을 §9 프로비저닝 전에 로컬에서 고정한다.
 *
 * <p>🟡 초록의 의미 — 이 테스트가 닫는 것은 <b>"기동 무결성"</b>까지다. "CTE 가 prod 데이터로
 * non-empty 를 반환한다"(R1b·R4 환경 정합)는 시드/Redis 가 필요한 별도 검증으로, 배포 시점
 * smoke grader / G4(spec-02 §4) 가 소유한다.
 *
 * <p>기동 시점엔 쿼리가 실행되지 않으므로 cte 시드(@Sql)·Redis 가동은 불필요(둘 다 lazy).
 * MySQL 컨테이너만 datasource/JPA ddl-auto 를 위해 필요.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(MySqlOnlyTestcontainersConfig.class)
@TestPropertySource(properties = {
    "mmt.migration.use-mysql-cte-for-graph=true",
    "spring.neo4j.uri=bolt://localhost:7687",
    "spring.neo4j.authentication.username=neo4j",
    "spring.neo4j.authentication.password=dummy"
})
class Neo4jAbsentBootSmokeTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextBootsWithNeo4jAbsentAndCteFlagOn() {
        // 기동 자체가 R1 의 증명 — 컨텍스트 로드 실패 시 이 테스트는 에러로 떨어진다.
        assertThat(ctx.getBean(ConceptService.class)).isNotNull();
        // flag ON 이어도 Neo4j 빈은 여전히 와이어링됨(드라이버 lazy connect 라 기동은 통과).
        assertThat(ctx.containsBean("conceptRepository")).isTrue();
    }
}
