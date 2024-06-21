package com.mmt.api.oauth2;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Slf4j
public class OAuth2UserInfoFactory {
    public static OAuth2UserInfo getOAuth2UserInfo(AuthProvider authProvider, Map<String, Object> attributes) {
        log.debug("Creating OAuth2UserInfo for provider: {}", authProvider);
        log.debug("Attributes received: {}", attributes);
        switch (authProvider) {
            case GOOGLE: return new GoogleOAuth2User(attributes);
            case NAVER: return new NaverOAuth2User(attributes);
            case KAKAO: return new KakaoOAuth2User(attributes);

            default: throw new IllegalArgumentException("Invalid Provider Type.");
        }
    }
}
