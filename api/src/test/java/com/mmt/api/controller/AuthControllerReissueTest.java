package com.mmt.api.controller;

import com.mmt.api.dto.user.TokenDTO;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.RefreshCookieFactory;
import com.mmt.api.service.user.AuthService;
import com.mmt.api.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * spec-01 Task 2: reissue 컨트롤러 로직 단위 검증(레포 컨벤션상 직접 Mockito — @WebMvcTest 미사용).
 *
 * 검증: (DR2#8) 쿠키 present 면 쿠키 우선·없으면 body 폴백, 회전 refresh 를 동일 속성 Set-Cookie 로 내려줌,
 * 응답 body 에 refresh 미포함. @CookieValue 바인딩/HTTP 401 매핑은 프레임워크 계층이라 범위 밖
 * (AuthService 예외 흐름은 RefreshTokenRotationIntegrationTest 에서 커버).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerReissueTest {

    private static final String ACCESS = "old.access";
    private static final String BODY_RT = "body.refresh";
    private static final String COOKIE_RT = "cookie.refresh";
    private static final String NEW_ACCESS = "new.access";
    private static final String NEW_RT = "new.refresh";

    @Mock AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(mock(UserService.class), authService, new RefreshCookieFactory(604800L));
        JwtToken rotated = JwtToken.builder()
                .grantType("Bearer").accessToken(NEW_ACCESS).refreshToken(NEW_RT).build();
        when(authService.reissue(eq(ACCESS), org.mockito.ArgumentMatchers.anyString())).thenReturn(rotated);
    }

    private TokenDTO body() {
        return TokenDTO.builder().grantType("Bearer").accessToken(ACCESS).refreshToken(BODY_RT).build();
    }

    @Test
    void cookiePresent_usesCookieRefresh_rotatesAndOmitsRefreshFromBody() {
        ResponseEntity<TokenDTO> response = controller.reissue(body(), COOKIE_RT);

        // (DR2#8) 쿠키가 있으면 쿠키 refresh 로 reissue
        ArgumentCaptor<String> refreshArg = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(authService).reissue(eq(ACCESS), refreshArg.capture());
        assertThat(refreshArg.getValue()).isEqualTo(COOKIE_RT);

        // 회전 refresh 가 동일 속성 Set-Cookie 로
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refreshToken=" + NEW_RT)
                .contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/api/v1/auth/reissue");

        // body 에는 access 만, refresh 없음
        assertThat(response.getBody().getAccessToken()).isEqualTo(NEW_ACCESS);
        assertThat(response.getBody().getRefreshToken()).isNull();
    }

    @Test
    void cookieAbsent_fallsBackToBodyRefresh() {
        controller.reissue(body(), null);

        ArgumentCaptor<String> refreshArg = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(authService).reissue(eq(ACCESS), refreshArg.capture());
        assertThat(refreshArg.getValue()).isEqualTo(BODY_RT);
    }
}
