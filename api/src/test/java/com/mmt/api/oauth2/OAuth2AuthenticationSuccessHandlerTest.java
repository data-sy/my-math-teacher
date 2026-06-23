package com.mmt.api.oauth2;

import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.RefreshCookieFactory;
import com.mmt.api.jwt.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * spec-01 Task 1: OAuth 성공 핸들러가 refresh 를 URL 이 아니라 HttpOnly 쿠키로 내리고,
 * 리다이렉트 URL 의 token 파라미터에는 access 문자열만 싣는지 검증.
 *
 * TokenProvider 를 mock 하여 Redis 의존(generateToken 의 Redis 저장) 없이 핸들러 단독 검증.
 * determineTargetUrl 은 protected 이지만 같은 패키지라 직접 호출 가능.
 */
class OAuth2AuthenticationSuccessHandlerTest {

    private static final String ACCESS = "access.jwt.value";
    private static final String REFRESH = "refresh.jwt.value";

    private TokenProvider tokenProvider;
    private OAuth2AuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(TokenProvider.class);
        handler = new OAuth2AuthenticationSuccessHandler(tokenProvider, new RefreshCookieFactory(604800L));

        JwtToken token = JwtToken.builder()
                .grantType("Bearer")
                .accessToken(ACCESS)
                .refreshToken(REFRESH)
                .build();
        when(tokenProvider.generateToken(org.mockito.ArgumentMatchers.any())).thenReturn(token);
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(
                "user@test.com", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void redirectUrl_containsAccessOnly_noRefreshString() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String targetUrl = handler.determineTargetUrl(request, response, auth());

        assertThat(targetUrl).contains("token=" + ACCESS);
        assertThat(targetUrl).doesNotContain(REFRESH);
        assertThat(targetUrl).doesNotContain("refreshToken");
    }

    @Test
    void refreshToken_setAsHardenedHttpOnlyCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.determineTargetUrl(request, response, auth());

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refreshToken=" + REFRESH);
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/v1/auth/reissue");
        assertThat(setCookie).contains("Max-Age=604800");
    }
}
