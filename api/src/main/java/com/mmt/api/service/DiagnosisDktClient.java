package com.mmt.api.service;

import com.mmt.api.dto.ai.AIServingRequest;
import com.mmt.api.dto.ai.AIServingResponse;
import com.mmt.api.dto.ai.InputInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * M7 spec-01 진단 전용 TF Serving 클라이언트 (ADR-0010 Decision-3).
 *
 * 공유 RestTemplate 빈(타임아웃 미설정)과 분리 — 구 경로 동작을 바꾸지 않고 신규
 * 익명 preview 경로에만 타임아웃을 강제한다. 실패는 예외 대신 null 반환 (fail-soft:
 * 시급도 등급만 결측, "몰라요" 목록 기반 결과는 성립 — spec-01 §4.7).
 */
@Service
public class DiagnosisDktClient {

    private final RestTemplate restTemplate;
    private final String servingUrl;

    public DiagnosisDktClient(RestTemplateBuilder builder,
                              @Value("${mmt.diagnosis.serving-url}") String servingUrl,
                              @Value("${mmt.diagnosis.serving-connect-timeout-ms}") long connectTimeoutMs,
                              @Value("${mmt.diagnosis.serving-read-timeout-ms}") long readTimeoutMs) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.servingUrl = servingUrl;
    }

    /**
     * @param sequencePairs [skillId, answerCode] 시퀀스 (입력 순서 그대로 — 재정렬 금지)
     * @return 확률 벡터, 실패 시 null (가드 1: null 응답)
     */
    public double[] predict(List<int[]> sequencePairs) {
        InputInstance instance = new InputInstance();
        instance.setInput(sequencePairs);
        AIServingRequest request = new AIServingRequest();
        request.setSignatureName("serving_default");
        request.setInstances(List.of(instance));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<AIServingResponse> response =
                    restTemplate.postForEntity(servingUrl, new HttpEntity<>(request, headers), AIServingResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || response.getBody().getPredictions() == null
                    || response.getBody().getPredictions().isEmpty()) {
                return null;
            }
            return response.getBody().getPredictions().get(0).stream()
                    .mapToDouble(Double::doubleValue).toArray();
        } catch (Exception e) {
            // 익명 preview 가 서빙 장애에 학생 친화적으로 degrade 해야 하므로 예외를 삼키고 null.
            return null;
        }
    }
}
