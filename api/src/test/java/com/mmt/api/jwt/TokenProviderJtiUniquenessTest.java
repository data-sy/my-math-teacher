package com.mmt.api.jwt;

import com.mmt.api.util.RedisUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M7 A4 하드닝 회귀 — access 토큰이 발급마다 유일한지 검증 (커밋 1c7c29e).
 *
 * 왜 필요한가: access 토큰은 원래 {sub, auth, exp} 뿐이라 토큰 문자열이
 * (subject, authorities, exp-초)의 결정적 함수였다. exp 는 초 단위라 같은 유저가
 * 같은 초에 두 번 발급받으면 **바이트 동일** 토큰이 나왔다.
 * AuthService.reissue/logout 은 블랙리스트 키로 access 문자열 자체를 쓰므로,
 * 같은 초에 재발급이 겹치면 "방금 내준 토큰을 블랙리스트에 올리는" 자기-무효화가
 * 성립했다(→ 로그아웃한 상태입니다 401).
 *
 * jti(UUID) + iat 부여로 이 경로가 원천 소멸한다.
 * 정본: docs/backlog/m7-prod-auth-fresh-token-401.md
 */
class TokenProviderJtiUniquenessTest {

    // 테스트 전용 시크릿 — Base64 디코딩 결과가 HS256 최소 길이(32B) 이상이어야 한다.
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "mmt-test-secret-for-jti-uniqueness-regression-0123456789".getBytes(StandardCharsets.UTF_8));

    private final TokenProvider tokenProvider =
            new TokenProvider(SECRET, 3600, 7200, mock(RedisUtil.class));

    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            "tester", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private Claims parse(String token) {
        Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    @Test
    @DisplayName("같은 유저를 연속 발급해도 access 토큰 문자열이 서로 다르다 (자기-블랙리스트 차단)")
    void accessTokensAreUniquePerIssuance() {
        // 연속 호출 — 같은 초에 떨어지는 것이 이 테스트의 관심사다.
        String first = tokenProvider.generateToken(authentication).getAccessToken();
        String second = tokenProvider.generateToken(authentication).getAccessToken();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("access 토큰에 jti·iat 가 실리고 jti 는 발급마다 다르다")
    void accessTokenCarriesUniqueJtiAndIat() {
        Claims first = parse(tokenProvider.generateToken(authentication).getAccessToken());
        Claims second = parse(tokenProvider.generateToken(authentication).getAccessToken());

        assertThat(first.getId()).isNotBlank();
        assertThat(first.getIssuedAt()).isNotNull();
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("jti·iat 추가 후에도 기존 클레임(sub·auth)과 검증 경로는 그대로다")
    void existingClaimsAndValidationUnaffected() {
        String token = tokenProvider.generateToken(authentication).getAccessToken();

        Claims claims = parse(token);
        assertThat(claims.getSubject()).isEqualTo("tester");
        assertThat(claims.get("auth")).isEqualTo("ROLE_USER");
        // 블랙리스트 미등록(mock 은 false 반환) + 서명 정상 → 유효
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getEmail(token)).isEqualTo("tester");
    }
}
