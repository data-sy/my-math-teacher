package com.mmt.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;


@RequiredArgsConstructor
@Configuration
@EnableRedisRepositories
public class RedisConfig {

//    private final RedisProperties redisProperties;

    @Value("${spring.redis.port}")
    private int port;
    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.password}")
    private String password;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(host);
        redisStandaloneConfiguration.setPort(port);
        redisStandaloneConfiguration.setPassword(password);
        // System.out.println("redis host" + host);
        // System.out.println("redis port" + port);
        // System.out.println("redis password" + password);
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    // 값 serializer 는 여기서 한 번만 고정한다. 과거에는 RedisUtil.set() 이
    // 매 write 마다 redisTemplate.setValueSerializer(...) 로 공유 싱글턴의
    // serializer 를 갈아끼웠는데(o.getClass() 기반 Jackson), 이는 인스턴스 간
    // (blue/green) round-trip 을 깨뜨렸다: write 를 한 적 없는 인스턴스는 기본
    // StringRedisSerializer 로 남아 캐시된 List/Map 을 String 으로 읽어 소비측
    // (List)/(Map) 캐스트에서 ClassCastException → 401 을 유발했다.
    // GenericJackson2JsonRedisSerializer 는 @class 타입 정보를 값에 심어
    // ArrayList/HashMap/ConceptResponse/String 을 인스턴스·타입 무관하게
    // 동일하게 역직렬화한다. key 는 redis-cli 가독성을 위해 String 유지.
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        return redisTemplate;
    }

}
