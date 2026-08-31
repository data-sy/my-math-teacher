package com.mmt.api.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.mmt.api.config.MySqlOnlyTestcontainersConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CI 이식성 — 프로파일 미지정이면 {@code spring.profiles.default: securelocal}(gitignore) 에
 * 의존해 CI 에서 DataSource 가 없다. {@code test} 프로파일 + MySQL 컨테이너로 자기완결화한다.
 * MySQL-only 근거는 {@code ApiApplicationTests} 주석 참조.
 * 정본: {@code docs/backlog/test-suite-not-portable-to-ci.md}
 *
 * <p>Redis 는 주변 환경의 6379 를 쓴다 — CI 는 워크플로의 redis 서비스, 로컬은 requirepass 가
 * 걸려 있으면 {@code TEST_REDIS_PASSWORD} 로 넘긴다(application-test.yml).
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlOnlyTestcontainersConfig.class)
@TestPropertySource(properties = {
    "spring.neo4j.uri=bolt://localhost:7687",
    "spring.neo4j.authentication.username=neo4j",
    "spring.neo4j.authentication.password=dummy"
})
class RedisUtilTest {
    final String KEY = "key";
    final String VALUE = "value";
    final long MILLISECONDS = 10*1000;

    @Autowired
    private RedisUtil redisUtil;

    @BeforeEach
    void shutDown() {
        redisUtil.set(KEY, VALUE, MILLISECONDS);
    }

    @AfterEach
    void tearDown() {
        redisUtil.delete(KEY);
    }

    @Test
    @DisplayName("Redis에 데이터를 저장하면 정상적으로 조회된다.")
    void saveAndFindTest() throws Exception {
        // when
        Object findValue = redisUtil.get(KEY);
        // then
        assertThat(VALUE).isEqualTo(findValue);
    }

    @Test
    @DisplayName("Redis에 저장된 데이터를 수정할 수 있다.")
    void updateTest() throws Exception {
        // given
        String updateValue = "updateValue";
        redisUtil.set(KEY, updateValue, MILLISECONDS);

        // when
        Object findValue = redisUtil.get(KEY);

        // then
        assertThat(updateValue).isEqualTo(findValue);
        assertThat(VALUE).isNotEqualTo(findValue);
    }

    @Test
    @DisplayName("Redis에 저장된 데이터를 삭제할 수 있다.")
    void deleteTest() throws Exception {
        // when
        redisUtil.delete(KEY);
        Object findValue = redisUtil.get(KEY);

        // then
        assertThat(findValue).isEqualTo(null);
    }

    @Test
    @DisplayName("같은 prefix 의 키들을 일괄 삭제할 수 있다.")
    void deleteByPrefixTest() {
        // spec-02 Task 2.2: 운영자 수동 무효화 경로 검증.
        // 테스트 전용 prefix 로 격리(실 운영 키 `graph:*` 와 충돌 회피).
        String prefix = "m2-spec02-test:";
        redisUtil.set(prefix + "ids:1:3",       "v1", MILLISECONDS);
        redisUtil.set(prefix + "to-concepts:5", "v2", MILLISECONDS);
        redisUtil.set(prefix + "objs:7:5",      "v3", MILLISECONDS);
        redisUtil.set("m2-spec02-other:99",     "v4", MILLISECONDS);

        long deleted = redisUtil.deleteByPrefix(prefix);

        assertThat(deleted).isEqualTo(3);
        assertThat(redisUtil.get(prefix + "ids:1:3")).isNull();
        assertThat(redisUtil.get(prefix + "to-concepts:5")).isNull();
        assertThat(redisUtil.get(prefix + "objs:7:5")).isNull();
        assertThat(redisUtil.get("m2-spec02-other:99")).isEqualTo("v4");

        redisUtil.delete("m2-spec02-other:99");
    }

    @Test
    @DisplayName("일치하는 키가 없으면 deleteByPrefix 는 0 을 반환한다.")
    void deleteByPrefixNoMatchTest() {
        long deleted = redisUtil.deleteByPrefix("m2-spec02-test-nomatch-" + System.nanoTime() + ":");

        assertThat(deleted).isEqualTo(0);
    }

    @Test
    @DisplayName("Redis에 저장된 데이터는 만료시간이 지나면 삭제된다.")
    void expiredTest() throws Exception {
        redisUtil.set(KEY, VALUE, MILLISECONDS);
        Object retrievedValue = redisUtil.get(KEY);
        assertEquals(VALUE, retrievedValue);
        // 데이터가 만료되기를 기다림
        Thread.sleep(20*1000);
        // 만료된 데이터를 다시 가져왔을 때
        Object expiredValue = redisUtil.get(KEY);
        // null 이어야 함
        assertNull(expiredValue);
        // 만료되어 삭제된 데이터를 삭제하려고 시도하면 false
        boolean deleted = redisUtil.delete(KEY);
        assertFalse(deleted);
    }

}
