package com.mmt.api.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Object> redisBlackListTemplate;

    public void set(String key, Object o, long duration) {
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer(o.getClass()));
        redisTemplate.opsForValue().set(key, o, duration, TimeUnit.MILLISECONDS);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public void setBlackList(String key, Object o, long duration) {
        redisBlackListTemplate.setValueSerializer(new Jackson2JsonRedisSerializer(o.getClass()));
        redisBlackListTemplate.opsForValue().set(key, o, duration, TimeUnit.MILLISECONDS);
    }

    public Object getBlackList(String key) {
        return redisBlackListTemplate.opsForValue().get(key);
    }

    public boolean deleteBlackList(String key) {
        return redisBlackListTemplate.delete(key);
    }

    public boolean hasKeyBlackList(String key) {
        return redisBlackListTemplate.hasKey(key);
    }

    /**
     * SCAN 으로 prefix 일치 키를 수집한 뒤 일괄 삭제. KEYS 는 O(N) 블로킹이라 비권장 → SCAN.
     * spec-02 Task 2.2 (그래프 캐시 무효화) 용도. 호출 빈도가 낮은 관리자 endpoint 에서만 사용.
     *
     * @param pattern Redis 패턴 (예: "graph:prerequisites:ids:*")
     * @return 삭제된 키 개수
     */
    public long deleteByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                conn -> conn.scan(options))) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        }
        if (keys.isEmpty()) {
            return 0L;
        }
        Long deleted = redisTemplate.delete(keys);
        return deleted == null ? 0L : deleted;
    }

}
