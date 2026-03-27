package com.ai.openai_api_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PresidioService {
    private static final Logger log = LoggerFactory.getLogger(PresidioService.class);

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

    public Map<?, ?> analyzeRaw(String text) {
        Map<String, Object> analyzeReq = new HashMap<>();
        analyzeReq.put("text", text);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<?, ?>> analyzeResponse =
                (ResponseEntity<Map<?, ?>>) (ResponseEntity<?>) restTemplate.postForEntity(
                        analyzerUrl,
                        buildEntity(analyzeReq),
                        Map.class
                );
        return analyzeResponse.getBody();
    }

    public Map<?, ?> anonymizeRaw(String text) {
        return anonymizeRaw(text, null);
    }

    public Map<?, ?> anonymizeRaw(String text, List<?> analyzerResults) {
        Map<String, Object> anonymizeReq = new HashMap<>();
        anonymizeReq.put("text", text);
        if (analyzerResults != null && !analyzerResults.isEmpty()) {
            anonymizeReq.put("analyzer_results", analyzerResults);
        }
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<?, ?>> anonymizedResponse =
                (ResponseEntity<Map<?, ?>>) (ResponseEntity<?>) restTemplate.postForEntity(
                        anonymizerUrl,
                        buildEntity(anonymizeReq),
                        Map.class
                );
        return anonymizedResponse.getBody();
    }

    public String sanitizeText(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Presidio is enabled but presidio.api.key is empty.");
        }

        try {
            Map<?, ?> analyzeBody = analyzeRaw(text);
            if (analyzeBody == null) {
                throw new IllegalStateException("Presidio analyze returned empty body.");
            }

            Object analyzeStatus = analyzeBody.get("status");
            if (analyzeStatus == null || !"success".equalsIgnoreCase(analyzeStatus.toString())) {
                throw new IllegalStateException("Presidio analyze status is not success.");
            }

            Object analyzeDataObj = analyzeBody.get("data");
            if (!(analyzeDataObj instanceof Map<?, ?> analyzeDataMap)) {
                throw new IllegalStateException("Presidio analyze data is missing/invalid.");
            }

            Object entitiesObj = analyzeDataMap.get("entities");
            List<?> entities = entitiesObj instanceof List<?> list ? list : List.of();
            log.info("Presidio analyze completed. entitiesCount={}. Calling anonymizer.", entities.size());
            Map<?, ?> body = anonymizeRaw(text, entities);
            if (body == null) {
                throw new IllegalStateException("Presidio anonymize returned empty body.");
            }

            Object status = body.get("status");
            if (status == null || !"success".equalsIgnoreCase(status.toString())) {
                throw new IllegalStateException("Presidio anonymize status is not success.");
            }

            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                throw new IllegalStateException("Presidio anonymize data is missing/invalid.");
            }

            Object anonymizedText = dataMap.get("anonymized_text");
            if (anonymizedText == null || anonymizedText.toString().isBlank()) {
                throw new IllegalStateException("Presidio anonymize text is empty.");
            }
            log.info("Presidio anonymization applied successfully.");
            return anonymizedText != null ? anonymizedText.toString() : text;
        } catch (RestClientException e) {
            throw new IllegalStateException("Presidio call failed: " + e.getMessage(), e);
        }
    }

    private HttpEntity<Map<String, Object>> buildEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(apiKeyHeader, apiKey);
        return new HttpEntity<>(body, headers);
    }
}

