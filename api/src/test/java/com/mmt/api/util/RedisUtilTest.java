package com.mmt.api.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
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
