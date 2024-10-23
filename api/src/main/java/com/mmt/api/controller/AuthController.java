package com.mmt.api.controller;

import com.mmt.api.dto.user.LoginDTO;
import com.mmt.api.dto.user.TokenDTO;
import com.mmt.api.dto.user.UserDTO;
import com.mmt.api.jwt.JwtFilter;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.service.user.AuthService;
import com.mmt.api.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
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

        // 토큰을 Response Header에도 넣어주자
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JwtFilter.AUTHORIZATION_HEADER, "Bearer " + token);

        return new ResponseEntity<>(TokenDTO.from(token), httpHeaders, HttpStatus.OK);
    }

    /**
     * AccessToken이 만료 되었을 때 토큰(AccessToken , RefreshToken)재발급
     */
    @PostMapping("/reissue")
    public ResponseEntity<TokenDTO> reissue(@Valid @RequestBody TokenDTO request) {
        JwtToken token = authService.reissue(request.getAccessToken(), request.getRefreshToken());

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JwtFilter.AUTHORIZATION_HEADER, "Bearer " + token);

        return new ResponseEntity<>(TokenDTO.from(token), httpHeaders, HttpStatus.OK);

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
        System.out.println(isValid);
        return ResponseEntity.ok(isValid);
    }

}
