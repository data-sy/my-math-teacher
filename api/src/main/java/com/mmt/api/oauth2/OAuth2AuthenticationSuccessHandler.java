package com.mmt.api.oauth2;

import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    // getDefaultTargetUrl를 통해 "/"가 리다이렉트 경로가 되는 중..

//    @Value("${oauth2.authorizedRedirectUri}")
//    private String redirectUri;
//    private String redirectUri = "http://localhost:8080/login/oauth2/code/google";
//    private String redirectUri = "http://localhost:8080/login/oauth2/code/naver";
//    private String redirectUri = "http://localhost:8080/login/oauth2/code/kakao";


    private final TokenProvider tokenProvider;

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

//        // 프론트 만들면 이걸로 수정
//        String targetUrl = "http://localhost:3000";
        String targetUrl = "http://localhost:5173";

        //JWT 생성
        JwtToken token = tokenProvider.generateToken(authentication);

        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("token", token)
                .build().toUriString();

    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
    }

}
