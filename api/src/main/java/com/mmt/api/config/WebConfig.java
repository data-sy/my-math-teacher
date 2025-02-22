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
//                .allowedOrigins("/*") // 외부에서 들어오는 모든 url 허용
                .allowedOrigins(allowedOrigins1, allowedOrigins2)  // EC2
                .allowedOrigins("http://localhost:8080", "http://localhost:5173", "http://localhost:8000") // local
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true) // 클라이언트에서 쿠키를 받기 위해
                .maxAge(3600);
    }
}
