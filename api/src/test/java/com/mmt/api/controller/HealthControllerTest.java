package com.mmt.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 헬스 엔드포인트 단위 테스트 (레포 최초 @WebMvcTest).
 *
 * <p>⚠️ 이 초록 체크의 의미를 고정한다: 본 테스트는 <b>컨트롤러 계약(GET /api/v1/health → 200 "OK")만</b>
 * 검증한다. {@code addFilters = false} 가 시큐리티 필터체인을 통째로 끄므로 <b>"헬스가 인증 없이
 * 공개로 닿는가"(permitAll 계약)는 검증하지 않는다.</b> 누가 SecurityConfig 의 permitAll 한 줄을
 * 지워도 이 테스트는 여전히 green 이다 — 그래서 초록을 "게이트 작동 검증됨"으로 오독하면 안 된다.
 *
 * <p>그 익명 도달성 불변식은 <b>배포 시점 G4 smoke grader / 헬스 폴링(spec-02 §4, §5)</b>이 소유한다.
 * CI 에서 닫으려면 {@code @Import(SecurityConfig.class)} + 필터 ON 이 필요하나 JWT/OAuth 빈 와이어링
 * 토끼굴을 다시 여는 비용이라, 의식적으로 "보안 계약 = G4, 컨트롤러 계약 = 본 테스트"로 분담한다.
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

}
