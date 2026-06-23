package com.mmt.api.controller;

import com.mmt.api.dto.user.LoginDTO;
import com.mmt.api.dto.user.TokenDTO;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.RefreshCookieFactory;
import com.mmt.api.service.user.AuthService;
import com.mmt.api.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * spec-01 Task 3: 비밀번호 로그인도 refresh 를 HttpOnly 쿠키로만 발급하고 body 에는 access 만 싣는지 검증.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    private static final String ACCESS = "access.jwt";
    private static final String REFRESH = "refresh.jwt";

    @Mock AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(mock(UserService.class), authService, new RefreshCookieFactory(604800L));
        when(authService.authorize(any(), any())).thenReturn(
                JwtToken.builder().grantType("Bearer").accessToken(ACCESS).refreshToken(REFRESH).build());
    }

    @Test
    void login_setsRefreshAsCookie_andOmitsRefreshFromBody() {
        LoginDTO login = new LoginDTO();
        login.setUserEmail("user@test.com");
        login.setUserPassword("pw");

        ResponseEntity<TokenDTO> response = controller.login(login);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refreshToken=" + REFRESH)
                .contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/api/v1/auth/reissue");

        assertThat(response.getBody().getAccessToken()).isEqualTo(ACCESS);
        assertThat(response.getBody().getRefreshToken()).isNull();
    }
}
