package com.ai.openai_api_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PresidioService {

    @Value("${presidio.analyzer.url}")
    private String analyzerUrl;

    @Value("${presidio.anonymizer.url}")
    private String anonymizerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        try {
            Map<String, Object> analyzeReq = new HashMap<>();
            analyzeReq.put("text", text);
            analyzeReq.put("language", "en");

            @SuppressWarnings("unchecked")
            ResponseEntity<List<?>> analyzeResponse =
                    (ResponseEntity<List<?>>) (ResponseEntity<?>) restTemplate.postForEntity(analyzerUrl, analyzeReq, List.class);

            List<?> analyzerResults = analyzeResponse.getBody();

            Map<String, Object> anonymizeReq = new HashMap<>();
            anonymizeReq.put("text", text);
            anonymizeReq.put("analyzer_results", analyzerResults);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<?, ?>> anonymizedResponse =
                    (ResponseEntity<Map<?, ?>>) (ResponseEntity<?>) restTemplate.postForEntity(anonymizerUrl, anonymizeReq, Map.class);

            Map<?, ?> body = anonymizedResponse.getBody();
            if (body == null) {
                return text;
            }

            Object anonymizedText = body.get("text");
            return anonymizedText != null ? anonymizedText.toString() : text;
        } catch (RestClientException e) {
            return text;
        }
    }
}

