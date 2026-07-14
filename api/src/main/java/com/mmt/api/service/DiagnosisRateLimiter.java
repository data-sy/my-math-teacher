package com.mmt.api.service;

import com.mmt.api.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.mmt.api.exception.DiagnosisException;

/**
 * 익명 preview 남용 방어 (spec-01 §4.6): permitAll + TF Serving 실호출 경로에만
 * IP 단위 분당 카운터. next 는 순수 DB/캐시 계산이라 미적용 (스펙 완화 항목).
 * 카운터는 Redis INCR (원자적 — RedisUtil.incrementWithTtl).
 */
@Service
public class DiagnosisRateLimiter {

    private static final String KEY_PREFIX = "diag:rl:preview:";

    private final RedisUtil redisUtil;
    private final int limitPerMinute;

    public DiagnosisRateLimiter(RedisUtil redisUtil,
                                @Value("${mmt.diagnosis.preview-rate-limit-per-minute}") int limitPerMinute) {
        this.redisUtil = redisUtil;
        this.limitPerMinute = limitPerMinute;
    }

    public void checkPreview(HttpServletRequest request) {
        String window = String.valueOf(System.currentTimeMillis() / 60_000);
        String key = KEY_PREFIX + clientIp(request) + ":" + window;
        if (redisUtil.incrementWithTtl(key, 120) > limitPerMinute) {
            throw new DiagnosisException(HttpStatus.TOO_MANY_REQUESTS,
                    "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
