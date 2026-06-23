package com.mmt.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${allowed.origins1}")
    private String allowedOrigins1;
    @Value("${allowed.origins2}")
    private String allowedOrigins2;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // (#5) allowedOrigins 는 누적이 아니라 덮어쓰기다. 이전엔 EC2 origin 이
                // localhost 호출에 덮여 무시됐다 → 운영/로컬 origin 을 한 번에 나열한다.
                .allowedOrigins(
                        allowedOrigins1, allowedOrigins2,                                    // EC2(운영)
                        "http://localhost:8080", "http://localhost:5173", "http://localhost:8000" // 로컬 개발
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // 클라이언트에서 쿠키를 받기 위해
                .maxAge(3600);
    }
}
