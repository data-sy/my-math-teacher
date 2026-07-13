package com.mmt.api.service;

import com.mmt.api.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.mmt.api.exception.DiagnosisException;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** spec-01 §4.6 익명 preview 남용 방어. */
class DiagnosisRateLimiterTest {

    @Test
    @DisplayName("분당 상한 이내 허용, 초과분부터 429")
    void limitsPerMinute() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        AtomicLong counter = new AtomicLong();
        when(redisUtil.incrementWithTtl(anyString(), anyLong()))
                .thenAnswer(inv -> counter.incrementAndGet());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        DiagnosisRateLimiter limiter = new DiagnosisRateLimiter(redisUtil, 3);
        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> limiter.checkPreview(request)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.checkPreview(request))
                .isInstanceOf(DiagnosisException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                        ((DiagnosisException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
