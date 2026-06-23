package com.mmt.api.oauth2;

import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.RefreshCookieFactory;
import com.mmt.api.jwt.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenProvider tokenProvider;
    private final RefreshCookieFactory refreshCookieFactory;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        if (response.isCommitted()) {
            log.debug("Response has already been committed.");
            return;
        }

        // 리다이렉트
        String targetUrl = determineTargetUrl(request, response, authentication);
        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);

    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        
        // 백엔드에서
//        String targetUrl = "http://localhost:8080/api/v1/login";
        // 프론트 추가 후
        // String targetUrl = "http://localhost:5173/login";
        // EC2 배포 후
        String targetUrl = "https://www.my-math-teacher.com/login";

        //JWT 생성
        JwtToken token = tokenProvider.generateToken(authentication);

        // (#1) refresh 토큰은 URL 이 아니라 HttpOnly 쿠키로 내려준다(단일 출처 RefreshCookieFactory).
        // URL/브라우저 히스토리/access 로그/Referer 로 새지 않게 refresh 는 절대 URL 에 두지 않는다.
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookieFactory.create(token.getRefreshToken()).toString());

        // URL 에는 access 토큰 문자열만 싣는다(refresh 잔류 금지).
        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("token", token.getAccessToken())
                .build().toUriString();

    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
    }

}
