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
                                .requestMatchers("/login.html", "/oauth2/**", "/login/**").permitAll()
                                .requestMatchers("api/v1/auth/**", "/api/v1/chapters/**", "/api/v1/concepts/**", "/api/v1/tests/school-level/**", "/api/v1/tests/detail/**").permitAll()
                                .requestMatchers("api/v1/weakness-diagnosis/serving-test", "/api/v1/tests/sample/**", "/api/v1/result/sample/**").permitAll()
                                .requestMatchers("api/v1/items/**", "api/v1/perf-test/*").permitAll() // API 테스트 중이라 잠시 열어둠
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
