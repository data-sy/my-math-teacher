package com.mmt.api.controller;

import com.mmt.api.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M2 spec-02 Task 2.2 단위 테스트.
 * 컨트롤러 메서드가 RedisUtil.deleteByPrefix("graph:") 를 호출하고 삭제 키
 * 개수를 응답에 담는지 검증. 인증/라우팅 통합 검증은 SecurityConfig
 * 통합 테스트 범위(별도).
 */
@ExtendWith(MockitoExtension.class)
class AdminCacheControllerTest {

    @Mock RedisUtil redisUtil;

    @InjectMocks AdminCacheController controller;

    @Test
    void invalidateGraphCache_returnsDeletedCount() {
        when(redisUtil.deleteByPrefix("graph:")).thenReturn(5L);

        ResponseEntity<Map<String, Object>> response = controller.invalidateGraphCache();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("deletedKeys", 5L);
        verify(redisUtil).deleteByPrefix("graph:");
    }

    @Test
    void invalidateGraphCache_zeroWhenNoKeys() {
        when(redisUtil.deleteByPrefix("graph:")).thenReturn(0L);

        ResponseEntity<Map<String, Object>> response = controller.invalidateGraphCache();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("deletedKeys", 0L);
    }
}
