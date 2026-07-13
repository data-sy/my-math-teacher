package com.mmt.api.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Object> redisBlackListTemplate;

    public void set(String key, Object o, long duration) {
        // value serializer 는 RedisConfig 에서 GenericJackson2JsonRedisSerializer 로
        // 고정. 여기서 갈아끼우지 않는다(과거 per-write 뮤테이션이 인스턴스 간
        // 캐시 역직렬화를 깨뜨린 원인 — RedisConfig 주석 참조).
        redisTemplate.opsForValue().set(key, o, duration, TimeUnit.MILLISECONDS);
    }

    public Object get(String key) {
        return getOrNullOnFormatMismatch(redisTemplate, key);
    }

    /**
     * 값의 on-wire 포맷이 현재 value serializer 와 호환되지 않으면(예: 무중단
     * 배포 오버랩 중 남은 구 포맷 엔트리, 레거시 값) 예외를 던지지 않고 null 을
     * 반환한다 — fail-closed. 호출측은 이를 캐시 miss(재계산) 또는 값 부재
     * (재로그인)로 안전하게 강등한다. 연결 오류(RedisConnectionFailureException
     * 등)는 잡지 않고 전파해 진짜 장애를 가리지 않는다.
     */
    private Object getOrNullOnFormatMismatch(RedisTemplate<String, Object> template, String key) {
        try {
            return template.opsForValue().get(key);
        } catch (SerializationException e) {
            log.warn("[redis] value deserialization failed, treating key as absent. key={}", key, e);
            return null;
        }
    }

    public boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 주어진 prefix 로 시작하는 모든 키를 SCAN 한 뒤 일괄 삭제. 반환값은 실제로
     * 삭제된 키 개수. KEYS 명령은 운영 환경에서 블록 가능하므로 SCAN 으로 처리.
     * spec-02 Task 2.2 의 운영자 수동 무효화 경로용.
     */
    public long deleteByPrefix(String prefix) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(1000).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) return 0;
        Long count = redisTemplate.delete(keys);
        return count == null ? 0 : count;
    }

    /**
     * M7 spec-01 §4.6 rate limit 용 원자적 INCR + 최초 증가 시 TTL 부여.
     * get-then-set 은 레이스로 임계 초과 버스트를 허용하므로 INCR 를 쓴다
     * (2026-07-13 design-review Note#4). 반환 = 증가 후 카운트.
     */
    public long incrementWithTtl(String key, long ttlSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        long value = count == null ? 1 : count;
        if (value == 1) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return value;
    }

    public void setBlackList(String key, Object o, long duration) {
        redisBlackListTemplate.opsForValue().set(key, o, duration, TimeUnit.MILLISECONDS);
    }

    public Object getBlackList(String key) {
        return getOrNullOnFormatMismatch(redisBlackListTemplate, key);
    }

    public boolean deleteBlackList(String key) {
        return redisBlackListTemplate.delete(key);
    }

    public boolean hasKeyBlackList(String key) {
        return redisBlackListTemplate.hasKey(key);
    }

}
