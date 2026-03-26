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
                log.warn("Presidio analyze returned empty body. Sending original text to OpenAI.");
                return text;
            }

            Object analyzeStatus = analyzeBody.get("status");
            if (analyzeStatus == null || !"success".equalsIgnoreCase(analyzeStatus.toString())) {
                log.warn("Presidio analyze status is not success. Sending original text to OpenAI.");
                return text;
            }

            Object analyzeDataObj = analyzeBody.get("data");
            if (!(analyzeDataObj instanceof Map<?, ?> analyzeDataMap)) {
                log.warn("Presidio analyze data is missing/invalid. Sending original text to OpenAI.");
                return text;
            }

            Object entitiesObj = analyzeDataMap.get("entities");
            if (!(entitiesObj instanceof List<?> entities) || entities.isEmpty()) {
                log.info("Presidio analyze detected no entities. Sending original text to OpenAI.");
                return text;
            }

            log.info("Presidio analyze detected {} entities. Calling anonymizer.", entities.size());

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
                log.warn("Presidio anonymize returned empty body. Sending original text to OpenAI.");
                return text;
            }

            Object status = body.get("status");
            if (status == null || !"success".equalsIgnoreCase(status.toString())) {
                log.warn("Presidio anonymize status is not success. Sending original text to OpenAI.");
                return text;
            }

            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                return text;
            }

            Object anonymizedText = dataMap.get("anonymized_text");
            if (anonymizedText == null || anonymizedText.toString().isBlank()) {
                log.warn("Presidio anonymize text is empty. Sending original text to OpenAI.");
                return text;
            }
            log.info("Presidio anonymization applied successfully.");
            return anonymizedText != null ? anonymizedText.toString() : text;
        } catch (RestClientException e) {
            log.warn("Presidio call failed. Sending original text to OpenAI. reason={}", e.getMessage());
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

