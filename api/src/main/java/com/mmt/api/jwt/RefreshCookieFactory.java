package com.mmt.api.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh 토큰 쿠키의 단일 출처(spec-01 review#4).
 *
 * 발급(OAuth 핸들러·reissue 회전)과 클리어(logout)가 반드시 동일한 속성 세트
 * (name·Path·Secure·HttpOnly·SameSite)를 쓰도록 한 곳에서 만든다. 속성이 일부라도
 * 다르면 브라우저가 별도 쿠키를 만들거나 구쿠키가 잔존/미삭제된다.
 *
 * Path 를 reissue 엔드포인트로 좁혀 전송 표면을 최소화한다. 클리어용 Max-Age=0 쿠키도
 * 같은 Path 여야 브라우저가 해당 쿠키를 삭제한다(다른 Path 의 Max-Age=0 은 무시됨).
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refreshToken";
    public static final String COOKIE_PATH = "/api/v1/auth/reissue";

    private final long refreshTokenValiditySeconds;

    public RefreshCookieFactory(
            @Value("${jwt.refresh-token-validity-in-seconds}") long refreshTokenValiditySeconds) {
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    /** 발급/회전용 refresh 쿠키. */
    public ResponseCookie create(String refreshToken) {
        return base(refreshToken)
                .maxAge(Duration.ofSeconds(refreshTokenValiditySeconds))
                .build();
    }

    /** 로그아웃 클리어용(Max-Age=0). 발급과 동일 속성·Path 라야 실제로 삭제된다. */
    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
