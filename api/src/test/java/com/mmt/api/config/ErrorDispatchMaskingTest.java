package com.mmt.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 A4 회귀 — <b>/error 디스패치가 오류 상태코드를 401 로 마스킹하지 않는지</b> 검증 (커밋 d0f4c41).
 *
 * <p>무엇이 문제였나: 미처리 예외·404 는 Spring 의 ERROR 디스패치(/error)를 타는데,
 * {@code /error} 가 permitAll 목록에 없어 {@code anyRequest().authenticated()} 에 걸렸다.
 * 게다가 {@code JwtFilter} 는 {@code OncePerRequestFilter} 기본값
 * ({@code shouldNotFilterErrorDispatch()=true})이라 ERROR 디스패치에서 아예 실행되지 않아
 * SecurityContext 가 비어 있다 → 원래 상태코드가 전부 <b>401 로 위장</b>됐다.
 * 프로덕션에서 M7 테이블 부재로 난 500 이 401 로 보여 "인증 불능"으로 오진된 것이 이 결함이다.
 *
 * <p>⚠️ 테스트 대상 선정 주의 — <b>임의의 존재하지 않는 경로로는 이 회귀를 못 잡는다.</b>
 * 익명 요청이 매칭 없는 경로로 가면 {@code anyRequest().authenticated()} 가
 * <b>시큐리티 계층에서</b> 401 을 내는 것이 정상 동작이고, 이는 수정 전후가 같다.
 * 마스킹을 드러내려면 <b>permitAll 로 시큐리티를 통과한 뒤 핸들러가 없어 404 가 나는</b>
 * 경로여야 한다 — {@code /api/v1/hello/**} 가 그 조건을 만족한다.
 *
 * <p>실제 ERROR 디스패치가 일어나야 하므로 MockMvc 가 아니라 {@code RANDOM_PORT}
 * (실 서블릿 컨테이너) + TestRestTemplate 를 쓴다.
 *
 * <p>정본: docs/backlog/m7-prod-auth-fresh-token-401.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MySqlOnlyTestcontainersConfig.class)
@TestPropertySource(properties = {
    "mmt.migration.use-mysql-cte-for-graph=true",
    "spring.neo4j.uri=bolt://localhost:7687",
    "spring.neo4j.authentication.username=neo4j",
    "spring.neo4j.authentication.password=dummy"
})
class ErrorDispatchMaskingTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("permitAll 경로에 핸들러가 없으면 404 로 나간다 (401 마스킹 아님)")
    void handlerMissingUnderPermitAllReturns404() {
        // /api/v1/hello/** 는 permitAll → 시큐리티 통과 → 핸들러 없음 → 404.
        // 수정 전에는 이 404 가 ERROR 디스패치에서 401 로 위장됐다.
        ResponseEntity<String> res = rest.getForEntity("/api/v1/hello/no-such-sub", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("무토큰으로 인증필수 엔드포인트를 치면 401 이 그대로 유지된다")
    void unauthenticatedRequestStillReturns401() {
        // /error permitAll 이 인증 게이트를 뚫지 않는다는 확인 — 이게 깨지면 보안 회귀다.
        // GET 을 쓰는 이유: JDK HttpURLConnection 은 401 응답에 재시도를 걸어
        // 스트리밍 바디가 있는 POST 에서 "cannot retry due to server authentication" 로 깨진다
        // (인증 로직과 무관한 클라이언트 제약). /api/v1/users 는 permitAll 목록에 없어 인증필수.
        ResponseEntity<String> res = rest.getForEntity("/api/v1/users", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("오류 응답 바디에 예외 메시지·스택이 실리지 않는다 (include-message=never 유지)")
    void errorBodyDoesNotLeakExceptionDetail() {
        // /error 를 익명에 열었으므로 노출면 점검 — Boot3 기본값이 유지되는지 고정한다.
        ResponseEntity<String> res = rest.getForEntity("/api/v1/hello/no-such-sub", String.class);

        String payload = res.getBody() == null ? "" : res.getBody();
        assertThat(payload).doesNotContain("Exception");
        assertThat(payload).doesNotContain("trace");
    }
}
