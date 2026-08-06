package com.mmt.api.config;

import com.mmt.api.jwt.JwtAccessDeniedHandler;
import com.mmt.api.jwt.JwtAuthenticationEntryPoint;
import com.mmt.api.jwt.JwtFilter;
import com.mmt.api.jwt.TokenProvider;
import com.mmt.api.oauth2.OAuth2AuthenticationFailureHandler;
import com.mmt.api.oauth2.OAuth2AuthenticationSuccessHandler;
import com.mmt.api.service.user.CustomOAuth2UserService;
import com.mmt.api.util.RedisUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private final TokenProvider tokenProvider;
    private final RedisUtil redisUtil;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(TokenProvider tokenProvider, RedisUtil redisUtil,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint, JwtAccessDeniedHandler jwtAccessDeniedHandler,
                          OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler, OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler,
                          CustomOAuth2UserService customOAuth2UserService) {
        this.tokenProvider = tokenProvider;
        this.redisUtil = redisUtil;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.oAuth2AuthenticationFailureHandler = oAuth2AuthenticationFailureHandler;
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화
                .csrf(CsrfConfigurer::disable)
                // 나중에 cors 설정할 때 수정 고고 ===========================
                // CORS 설정
                .cors(Customizer.withDefaults()) // CORS 설정을 기본값으로 사용
                // 잘못된 접근에 대한 예외 처리
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling
                                .accessDeniedHandler(jwtAccessDeniedHandler)
                                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                // 사용하지 않는 것들 비활성화
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .rememberMe(rememberMe -> rememberMe.disable())
                // 요청 접근 제한 설정
                .authorizeHttpRequests(request ->
                        request
//                                // 개발을 위해 우선 열어두자
//                                .requestMatchers("/api/v1/**").permitAll()
                                // 인증 없이 접근 가능
                                .requestMatchers("/", "/favicon.ico", "/api/v1/hello/**").permitAll()
                                // (M7 A4) Spring 의 ERROR 디스패치(/error)를 permitAll 로 열어 마스킹 제거.
                                // JwtFilter 는 ERROR 디스패치에서 안 돌아(SecurityContext 비어있음) /error 가
                                // anyRequest().authenticated() 에 걸리면 404·500·모든 미처리 예외가 401 로 위장된다.
                                // (server.error.include-message=Boot3 기본 never → 예외 메시지 유출 없음)
                                // 정본: docs/backlog/m7-prod-auth-fresh-token-401.md
                                .requestMatchers("/error").permitAll()
                                // M4 spec-01: 무중단 배포 전환 게이트. 하위 리소스 없는 단일 GET 이므로
                                // 와일드카드 없이 정확 매칭 + GET-only 로 표면 최소화 (G6)
                                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                                .requestMatchers("/login.html", "/oauth2/**", "/login/**").permitAll()
                                // 인증 진입점만 공개. (#4) /api/v1/auth/validation 은 현재 비밀번호 확인
                                // 오라클이므로 의도적으로 제외 → anyRequest().authenticated() 로 보호.
                                .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/duplication", "/api/v1/auth/authentication", "/api/v1/auth/reissue").permitAll()
                                // 공개 카탈로그(정답 미포함): 단원·개념·시험 목록
                                .requestMatchers("/api/v1/chapters/**", "/api/v1/concepts/**", "/api/v1/tests/school-level/**").permitAll()
                                // 비로그인 체험용 샘플
                                .requestMatchers("/api/v1/tests/sample/**", "/api/v1/weakness-diagnosis/sample/**").permitAll()
                                // M7 spec-01: 익명 자가진단 문답 (F-1 결과-시점 게이트 — 문답·preview 는
                                // 비로그인 완주, 귀속 /api/v1/diagnosis·큐는 anyRequest().authenticated() 커버)
                                .requestMatchers("/api/v1/diagnosis/frontier", "/api/v1/diagnosis/next", "/api/v1/diagnosis/preview").permitAll()
                                // (#2/#3) 정답(item_answer) 포함 응답(items, tests/detail)과 perf-test 는
                                // 인증 필수 → permitAll 제거. anyRequest().authenticated() 가 커버.
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/db/**").access(new WebExpressionAuthorizationManager("hasRole('ADMIN') and hasRole('DBA')"))
                                .anyRequest().authenticated()
                )
                // OAuth2 로그인 설정
                .oauth2Login(oauth2Login ->
                        oauth2Login
                                .userInfoEndpoint(userInfoEndpointConfig ->
                                        userInfoEndpointConfig
                                                .userService(customOAuth2UserService))
//                                .anonymous() ??
//                                .tokenEndpoint(tokenEndpointConfig -> tokenEndpointConfig.accessTokenResponseClient())
                                .successHandler(oAuth2AuthenticationSuccessHandler)
                                .failureHandler(oAuth2AuthenticationFailureHandler)
                );
        // 필터 추가
        http.addFilterBefore(new JwtFilter(tokenProvider, redisUtil), UsernamePasswordAuthenticationFilter.class);
        // 로그아웃 설정
        http.logout(logoutConfig ->
                logoutConfig
                        .logoutSuccessUrl("/")
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
