package com.mmt.api.service;

import com.mmt.api.config.TestcontainersConfig;
import com.mmt.api.exception.UnauthorizedException;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.TokenProvider;
import com.mmt.api.service.user.AuthService;
import com.mmt.api.util.RedisUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * spec-01 Task 4a (DR2#5): refresh 토큰 하드닝의 "실제 무효" 증명은 service mock 으로 불가하므로
 * Testcontainers(Redis) 통합 테스트로 검증한다. @WebMvcTest 는 쿠키 바인딩/401 흐름만 다룬다.
 *
 * 검증 대상:
 *  - generateToken 이 refresh 를 멀티슬롯 refresh:{email}:{jti} 로 저장(subject+jti 부여)
 *  - logout 이 채택 스킴(멀티슬롯)에서 해당 사용자 refresh 를 전수 폐기 — 유효 access·만료 access 양쪽
 *    (DR2#1 합격선: 만료-access 케이스가 통과해야 거짓 신뢰가 아님)
 *  - reissue 회전 시 직전 jti 가 grace window 동안 살아있어 다탭 동시 reissue 가 spurious 401 을 안 겪음
 *
 * Redis 컨테이너는 이 테스트에만 부착(@Container)해 다른 통합 테스트 부팅에 영향을 주지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Testcontainers
class RefreshTokenRotationIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AuthService authService;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${jwt.secret}")
    private String secret;

    private String uniqueEmail() {
        return "rot-" + UUID.randomUUID() + "@test.com";
    }

    private Authentication authFor(String email) {
        return new UsernamePasswordAuthenticationToken(
                email, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    /** TokenProvider 와 동일한 키로, 이미 만료된 access 토큰을 직접 생성(만료-access 로그아웃 케이스용). */
    private String expiredAccessFor(String email) {
        Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        return Jwts.builder()
                .setSubject(email)
                .claim("auth", "ROLE_USER")
                .signWith(key, SignatureAlgorithm.HS256)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .compact();
    }

    private String slotKey(String email, JwtToken token) {
        return TokenProvider.refreshKey(email, tokenProvider.getJti(token.getRefreshToken()));
    }

    @Test
    void generateToken_storesRefreshUnderJtiKey_withSubject() {
        String email = uniqueEmail();
        JwtToken token = tokenProvider.generateToken(authFor(email));

        assertThat(tokenProvider.getSubject(token.getRefreshToken())).isEqualTo(email);
        assertThat(tokenProvider.getJti(token.getRefreshToken())).isNotBlank();
        assertThat(redisUtil.hasKey(slotKey(email, token))).isTrue();
    }

    @Test
    void logout_purgesAllRefreshSlots_withValidAccess() {
        String email = uniqueEmail();
        JwtToken device1 = tokenProvider.generateToken(authFor(email));
        JwtToken device2 = tokenProvider.generateToken(authFor(email));
        assertThat(redisUtil.hasKey(slotKey(email, device1))).isTrue();
        assertThat(redisUtil.hasKey(slotKey(email, device2))).isTrue();

        authService.logout(device1.getAccessToken());

        // 전 기기 폐기: 로그아웃에 쓰지 않은 슬롯까지 사라진다
        assertThat(redisUtil.hasKey(slotKey(email, device1))).isFalse();
        assertThat(redisUtil.hasKey(slotKey(email, device2))).isFalse();
        // 포획된 refresh 로 reissue 시도 → 401
        assertThatThrownBy(() -> authService.reissue(device2.getAccessToken(), device2.getRefreshToken()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logout_purgesAllRefreshSlots_withExpiredAccess() {
        // DR2#1 합격선: idle 후 만료 access 로 로그아웃하는 가장 흔한 케이스
        String email = uniqueEmail();
        JwtToken device1 = tokenProvider.generateToken(authFor(email));
        assertThat(redisUtil.hasKey(slotKey(email, device1))).isTrue();

        authService.logout(expiredAccessFor(email));

        assertThat(redisUtil.hasKey(slotKey(email, device1))).isFalse();
        assertThatThrownBy(() -> authService.reissue(device1.getAccessToken(), device1.getRefreshToken()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void reissue_rotatesRefresh_andGraceWindowAbsorbsConcurrentReissue() {
        String email = uniqueEmail();
        JwtToken t0 = tokenProvider.generateToken(authFor(email));

        JwtToken t1 = authService.reissue(t0.getAccessToken(), t0.getRefreshToken());
        assertThat(t1.getRefreshToken()).isNotEqualTo(t0.getRefreshToken());
        assertThat(redisUtil.hasKey(slotKey(email, t1))).isTrue();

        // 직전 jti 슬롯이 grace window 동안 살아있으므로, 같은 RT0 를 보낸 두 번째(다탭) reissue 도 성공
        assertThat(redisUtil.hasKey(slotKey(email, t0))).isTrue();
        JwtToken t1b = authService.reissue(t0.getAccessToken(), t0.getRefreshToken());
        assertThat(t1b.getRefreshToken()).isNotEqualTo(t0.getRefreshToken());
    }

    @Test
    void reissue_rejectsWhenRefreshSubjectMismatchesAccess() {
        // DR2#10: refresh.subject != body access.subject 면 거절 (body-access 방어층 유지)
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        JwtToken tokenA = tokenProvider.generateToken(authFor(emailA));
        JwtToken tokenB = tokenProvider.generateToken(authFor(emailB));

        assertThatThrownBy(() -> authService.reissue(tokenA.getAccessToken(), tokenB.getRefreshToken()))
                .isInstanceOf(UnauthorizedException.class);
    }
}
