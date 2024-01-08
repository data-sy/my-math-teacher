package com.mmt.api.controller;

import com.mmt.api.dto.user.LoginDTO;
import com.mmt.api.dto.user.TokenDTO;
import com.mmt.api.jwt.JwtFilter;
import com.mmt.api.jwt.JwtToken;
import com.mmt.api.service.user.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * login
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

//    /**
//     * oauth login
//     */
//    @GetMapping("/login")
//    public ResponseEntity<?> login(@RequestParam("token") String token) {
//        if (token != null && !token.isEmpty()) {
//            // accessToken과 refreshToken 추출
//            String accessToken = extractTokenValue(token, "accessToken");
//            String refreshToken = extractTokenValue(token, "refreshToken");
//
//            // 추출한 토큰 값을 출력하거나 다른 작업 수행
//            System.out.println("AccessToken: " + accessToken);
//            System.out.println("RefreshToken: " + refreshToken);
//
//            JwtToken jwtToken = new JwtToken("Bearer", accessToken, refreshToken);
//
//            // 여기서 추출한 accessToken과 refreshToken을 사용하여 다른 엔드포인트로 보낼 수 있어요.
//             sendTokenToAnotherEndpoint(jwtToken);
//
//            return ResponseEntity.ok("Token received and extracted.");
//        } else {
//            return ResponseEntity.badRequest().body("Token not found in query string.");
//        }
//    }

    /**
     * AccessToken이 만료되었을 때 토큰(AccessToken , RefreshToken)재발급
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
     * 리팩토링 : 프론트 단에서 accessToken의 시간 파싱할 수 있다면 바디에 그 시간만 담아서 보내기
     */
    @DeleteMapping("/authentication")
    public ResponseEntity<Void> logout(@RequestBody @Valid TokenDTO request) {
        authService.logout(request.getAccessToken());
        return ResponseEntity.ok().build();
    }

//    private String extractTokenValue(String token, String tokenType) {
//        String pattern = tokenType + "=([^,&)]+)";
//        Pattern regex = Pattern.compile(pattern);
//        Matcher matcher = regex.matcher(token);
//
//        if (matcher.find()) {
//            return matcher.group(1);
//        }
//        return null;
//    }
//
//    private void sendTokenToAnotherEndpoint(JwtToken jwtToken) {
//        // 여기서 token을 새 엔드포인트로 전송하는 로직을 구현
//        // HTTP 요청을 보내거나 다른 서비스로 전달할 수 있습니다.
//        // 예를 들어 RestTemplate, HttpClient 등을 사용하여 POST 요청을 보낼 수 있습니다.
//        // 아래는 예시 코드입니다.
//
//        String targetEndpoint = "http://localhost:5173/login"; // 바디에 토큰을 담아서 보낼 엔드포인트
//        RestTemplate restTemplate = new RestTemplate();
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.add(JwtFilter.AUTHORIZATION_HEADER, "Bearer " + jwtToken);
//
//        HttpEntity<JwtToken> requestEntity = new HttpEntity<>(jwtToken, headers);
//
//        // 여기서 Connection refused 에러가 뜸
//        ResponseEntity<String> response = restTemplate.exchange(targetEndpoint, HttpMethod.GET, requestEntity, String.class);
//
//        // 만약 다른 엔드포인트로의 응답을 처리해야 한다면 여기서 처리할 수 있습니다.
//        // response.getBody() 등을 통해 응답 내용을 확인할 수 있습니다.
//    }
}
