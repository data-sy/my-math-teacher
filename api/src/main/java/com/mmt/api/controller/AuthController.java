package com.mmt.api.controller;

import com.mmt.api.dto.user.LoginDTO;
import com.mmt.api.dto.user.TokenDTO;
import com.mmt.api.dto.user.UserDTO;
import com.mmt.api.jwt.JwtFilter;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.jwt.RefreshCookieFactory;
import com.mmt.api.service.user.AuthService;
import com.mmt.api.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(UserService userService, AuthService authService,
                          RefreshCookieFactory refreshCookieFactory) {
        this.userService = userService;
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<UserDTO> signup(@Valid @RequestBody UserDTO userDTO) {
        return ResponseEntity.ok(userService.signup(userDTO));
    }

    /**
     * 아이디 중복 검사
     */
    @GetMapping("/duplication")
    public ResponseEntity<Boolean> checkDuplicate(@RequestParam String userEmail) {
        boolean isDuplicate = userService.isDuplicateUser(userEmail);
        return ResponseEntity.ok(isDuplicate);
    }

    /**
     * 로그인
     * 수정할 것 : 로그인DTO를 loginRequest로 바꾸고 변수명도 request로 바꾸기
     * 로그인 한 회원의 id도 꺼내서 토큰과 같이 담아서 loginResponse 만들기
     * 아! 그러면 토큰 DTO 말고 그냥 토큰 자체 담아도 될 듯!
     */
    @PostMapping("/authentication")
    public ResponseEntity<TokenDTO> login(@RequestBody LoginDTO loginDTO) {
        JwtToken token = authService.authorize(loginDTO.getUserEmail(), loginDTO.getUserPassword());
        // (Task 3) 비밀번호 로그인도 OAuth 와 동일하게 refresh 는 쿠키로만, body 는 access 만.
        return accessOnlyResponseWithRefreshCookie(token);
    }

    /**
     * AccessToken이 만료 되었을 때 토큰(AccessToken , RefreshToken)재발급
     */
    @PostMapping("/reissue")
    public ResponseEntity<TokenDTO> reissue(
            @Valid @RequestBody TokenDTO request,
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshCookie) {
        // (DR2#8) 쿠키가 있으면 쿠키만, 없을 때만 body refresh 폴백(전환기). 폴백 제거는 3차 배포(완료기준).
        String refreshToken = StringUtils.hasText(refreshCookie) ? refreshCookie : request.getRefreshToken();

        JwtToken token = authService.reissue(request.getAccessToken(), refreshToken);
        // 회전된 refresh 를 동일 속성 쿠키로(review#4), body 에는 access 만.
        return accessOnlyResponseWithRefreshCookie(token);
    }

    /**
     * 로그인·재발급 공통 응답: access 는 Authorization 헤더+body, refresh 는 HttpOnly 쿠키로만 내린다.
     * 쿠키 속성은 RefreshCookieFactory 단일 출처라 OAuth 핸들러·reissue 회전과 항상 일치(review#4).
     */
    private ResponseEntity<TokenDTO> accessOnlyResponseWithRefreshCookie(JwtToken token) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JwtFilter.AUTHORIZATION_HEADER, "Bearer " + token.getAccessToken());
        httpHeaders.add(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(token.getRefreshToken()).toString());

        TokenDTO body = TokenDTO.builder()
                .grantType(token.getGrantType())
                .accessToken(token.getAccessToken())
                .build();

        return new ResponseEntity<>(body, httpHeaders, HttpStatus.OK);
    }

    /**
     * 로그아웃 했을 때 토큰을 받아 BlackList에 저장
     */
    @DeleteMapping("/authentication")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring(7);
            authService.logout(accessToken);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 현재 비밀번호 확인 (개인정보 수정 시)
     * Q. 필드가 같아서 LoginDTO 재활용 했는데, validateRequest DTO를 따로 만들어주는 게 좋을까? 내용이 같더라도?
     */
    @PostMapping("/validation")
    public ResponseEntity<Boolean> validateUser(@RequestBody LoginDTO loginDTO) {
        boolean isValid = authService.validateCurrentPassword(loginDTO.getUserEmail(), loginDTO.getUserPassword());
        return ResponseEntity.ok(isValid);
    }

}
