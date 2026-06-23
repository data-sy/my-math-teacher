package com.mmt.api.service.user;

import com.mmt.api.exception.UnauthorizedException;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.TokenProvider;
import com.mmt.api.util.RedisUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // 회전 시 직전 refresh 슬롯을 즉시 삭제하지 않고 이 시간만큼 유지(grace window) — 다탭/동시
    // reissue 경쟁에서 정상 사용자의 spurious 401 방지 (spec-01 review#2/DR2#7, 값은 §4b ADR 확정).
    private static final long ROTATION_GRACE_MS = 10_000L;

    private final TokenProvider tokenProvider;
    private final RedisUtil redisUtil;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    public AuthService(TokenProvider tokenProvider, RedisUtil redisUtil, AuthenticationManagerBuilder authenticationManagerBuilder) {
        this.tokenProvider = tokenProvider;
        this.redisUtil = redisUtil;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
    }

    /**
     * authorize : 로그인 요청이 들어오면 유저 정보로 인증 과정을 진행
     * Authentication Token 객체를 생성해 검증 과정을 진행. 그 검증된 인증 정보로 JWT 토큰을 생성
     */
    public JwtToken authorize(String email, String password) {
        // Authentication 객체 생성
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(email, password);
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        // 검증된 인증 정보로 JWT 토큰 생성
        JwtToken token = tokenProvider.generateToken(authentication);
        return token;
    }

    /**
     * reissue : 토큰 재발급
     */
    @Transactional
    public JwtToken reissue(String accessToken, String refreshToken) {
        // refresh 서명·블랙리스트·만료 검증
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("유효하지 않은 RefreshToken 입니다");
        }
        // 주체 확정: body access 에서 email 추출(만료 access 도 claims 반환). body-access 요구는 CSRF 2차 방어.
        String userEmail = tokenProvider.getEmail(accessToken);

        // (DR2#10) refresh.subject == access.subject 교차검증 — body-access 방어층을 유지(단순화 금지)
        String refreshSubject = tokenProvider.getSubject(refreshToken);
        if (refreshSubject == null || !refreshSubject.equals(userEmail)) {
            throw new UnauthorizedException("토큰 주체가 일치하지 않습니다.");
        }

        // refresh 의 jti 슬롯이 Redis 에 살아있고 값이 일치해야 함 (회전 후 grace window 안이면 직전 jti 도 통과)
        String jti = tokenProvider.getJti(refreshToken);
        String slotKey = TokenProvider.refreshKey(userEmail, jti);
        if (!redisUtil.hasKey(slotKey) || !refreshToken.equals(redisUtil.get(slotKey))) {
            throw new UnauthorizedException("만료됐거나 유효하지 않은 Token 입니다. 다시 로그인해주세요.");
        }

        // 기존 accessToken 블랙리스트에 넣고 (refresh 토큰의 잔여 기간만큼)
        redisUtil.setBlackList(accessToken, "logout", tokenProvider.getExpiration(refreshToken));

        // 새 토큰 발급 — generateToken 이 새 jti 로 새 슬롯을 저장(회전)
        Authentication authentication = tokenProvider.getAuthentication(accessToken);
        JwtToken newToken = tokenProvider.generateToken(authentication);

        // 직전 refresh 슬롯은 즉시 삭제하지 않고 grace window 로 TTL 만 단축(다탭 경쟁 흡수)
        redisUtil.set(slotKey, refreshToken, ROTATION_GRACE_MS);

        return newToken;
    }


    /**
     * logout : accessToken - 블랙리스트에 올리기, refreshToken - RedisDB에서 삭제
     */
    @Transactional
    public void logout(String accessToken) {
        // (DR2#1) SecurityContext 가 아니라 access 토큰에서 직접 주체 확정 — JwtFilter 는 만료/무효
        // 토큰이면 컨텍스트를 세팅하지 않으므로, idle 후 만료 access 로 로그아웃하는 가장 흔한 케이스에서
        // SecurityUtil 기반은 email="" → no-op = 로그아웃이 세션을 못 끊는 보안 회귀가 된다.
        String userEmail;
        try {
            userEmail = tokenProvider.getEmail(accessToken); // 만료 access 도 parseClaims 가 claims 반환
        } catch (Exception e) {
            // (DR2#1 잔여 한계, 수용) access 완전 부재/파싱불가면 서버측 폐기 skip — reissue 가 body access 를
            // 필수로 요구하므로 refresh 단독 재발급 불가(2차 방어 유지). 클라이언트는 토큰/쿠키를 클리어한다.
            return;
        }

        // (review#1) 채택한 멀티슬롯 스킴에서 "logout = 해당 사용자 refresh 전수 폐기" 성립
        redisUtil.deleteByPrefix(TokenProvider.refreshKeyPrefix(userEmail));

        // access 토큰 블랙리스트 — 이미 만료된 access 는 블랙리스트 불필요(validateToken 이 만료로 거절)
        try {
            redisUtil.setBlackList(accessToken, "logout", tokenProvider.getExpiration(accessToken));
        } catch (ExpiredJwtException ignored) {
        }
    }

    /**
     * 현재 비밀번호 확인 (개인정보 수정 시)
     */
    public boolean validateCurrentPassword(String email, String password) {
        try {
            // Authentication 객체 생성
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(email, password);
            // 인증 시도
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            // 인증 성공
            return authentication.isAuthenticated();
        } catch (AuthenticationException e) {
            // 인증 실패
            return false;
        }
    }

}

