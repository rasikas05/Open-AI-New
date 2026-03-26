package com.ai.openai_api_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class PresidioService {

    @Value("${presidio.analyzer.url}")
    private String analyzerUrl;

    @Value("${presidio.anonymizer.url}")
    private String anonymizerUrl;

    @Value("${presidio.enabled:true}")
    private boolean enabled;

    @Value("${presidio.api.key:}")
    private String apiKey;

    @Value("${presidio.api.key.header:x-api-key}")
    private String apiKeyHeader;

    private final RestTemplate restTemplate = new RestTemplate();

    public String sanitizeText(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }

        try {
            Map<String, Object> analyzeReq = new HashMap<>();
            analyzeReq.put("text", text);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<?, ?>> analyzeResponse =
                    (ResponseEntity<Map<?, ?>>) (ResponseEntity<?>) restTemplate.postForEntity(
                            analyzerUrl,
                            buildEntity(analyzeReq),
                            Map.class
                    );
            Map<?, ?> analyzeBody = analyzeResponse.getBody();
            if (analyzeBody == null) {
                return text;
            }

            Map<String, Object> anonymizeReq = new HashMap<>();
            anonymizeReq.put("text", text);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<?, ?>> anonymizedResponse =
                    (ResponseEntity<Map<?, ?>>) (ResponseEntity<?>) restTemplate.postForEntity(
                            anonymizerUrl,
                            buildEntity(anonymizeReq),
                            Map.class
                    );

            Map<?, ?> body = anonymizedResponse.getBody();
            if (body == null) {
                return text;
            }

            Object status = body.get("status");
            if (status == null || !"success".equalsIgnoreCase(status.toString())) {
                return text;
            }

            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                return text;
            }

            Object anonymizedText = dataMap.get("anonymized_text");
            return anonymizedText != null ? anonymizedText.toString() : text;
        } catch (RestClientException e) {
            return text;
        }
    }

    private HttpEntity<Map<String, Object>> buildEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(apiKeyHeader, apiKey);
        }
        return new HttpEntity<>(body, headers);
    }
}

