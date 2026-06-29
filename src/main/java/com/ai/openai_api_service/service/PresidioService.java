package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.model.PresidioAnalyzerResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

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

    @Value("${presidio.api.timeout-ms:15000}")
    private int presidioTimeoutMs;

    private RestTemplate restTemplate;

    @PostConstruct
    void initRestTemplate() {
        this.restTemplate = RestTemplateFactory.create(presidioTimeoutMs);
    }

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
        return sanitizeTextInternal(text, false);
    }

    /**
     * Sanitize text but return the original on Presidio failure instead of throwing.
     */
    public String sanitizeTextSafe(String text) {
        return sanitizeTextInternal(text, true);
    }

    private String sanitizeTextInternal(String text, boolean failSoft) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Presidio is enabled but presidio.api.key is empty.");
        }

        try {
            Map<?, ?> body = anonymizeRaw(text);
            if (body == null) {
                throw new IllegalStateException("Presidio anonymize returned empty body.");
            }

            // New Python contract:
            // { "originalText": "...", "sanitizedText": "...", "entities": [...] }
            Object newSanitizedText = body.get("sanitizedText");
            if (newSanitizedText != null && !newSanitizedText.toString().isBlank()) {
                log.info("Presidio anonymization applied successfully (new contract).");
                return newSanitizedText.toString();
            }

            // Backward compatibility with old wrapped contract:
            // { "status":"success", "data":{"anonymized_text":"..."} }
            Object status = body.get("status");
            if (status == null || !"success".equalsIgnoreCase(status.toString())) {
                throw new IllegalStateException("Presidio anonymize response is invalid.");
            }
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                throw new IllegalStateException("Presidio anonymize data is missing/invalid.");
            }

            Object anonymizedText = dataMap.get("anonymized_text");
            if (anonymizedText == null || anonymizedText.toString().isBlank()) {
                throw new IllegalStateException("Presidio anonymize text is empty.");
            }
            log.info("Presidio anonymization applied successfully (legacy contract).");
            return anonymizedText.toString();
        } catch (HttpStatusCodeException e) {
            HttpHeaders h = e.getResponseHeaders();
            if (h != null) {
                // Presidio rate limiting headers vary; log everything we might get.
                log.warn("Presidio error x-ratelimit-limit-requests={}", h.getFirst("x-ratelimit-limit-requests"));
                log.warn("Presidio error x-ratelimit-remaining-requests={}", h.getFirst("x-ratelimit-remaining-requests"));
                log.warn("Presidio error x-ratelimit-reset-requests={}", h.getFirst("x-ratelimit-reset-requests"));
                log.warn("Presidio error x-ratelimit-limit-tokens={}", h.getFirst("x-ratelimit-limit-tokens"));
                log.warn("Presidio error x-ratelimit-remaining-tokens={}", h.getFirst("x-ratelimit-remaining-tokens"));
                log.warn("Presidio error x-ratelimit-reset-tokens={}", h.getFirst("x-ratelimit-reset-tokens"));
            }

            String errorBody = e.getResponseBodyAsString();
            log.warn("Presidio error status={} body={}", e.getStatusCode(), errorBody);
            if (failSoft) {
                log.warn("Presidio soft-fail: returning original text");
                return text;
            }
            throw new IllegalStateException("Presidio call failed: " + e.getStatusCode() + " body=" + errorBody, e);
        } catch (RestClientException e) {
            if (failSoft) {
                log.warn("Presidio soft-fail: returning original text ({})", e.getMessage());
                return text;
            }
            throw new IllegalStateException("Presidio call failed: " + e.getMessage(), e);
        }
    }

    public String sanitizeTextWithExternalResults(String text, List<PresidioAnalyzerResult> externalResults) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Presidio is enabled but presidio.api.key is empty.");
        }
        if (externalResults == null || externalResults.isEmpty()) {
            log.info("No external results provided, returning original text");
            return text;
        }

        try {
            log.info("Anonymizing text with {} external PII results", externalResults.size());
            Map<?, ?> body = anonymizeRaw(text, externalResults);
            if (body == null) {
                throw new IllegalStateException("Presidio anonymize returned empty body.");
            }

            // New Python contract:
            // { "originalText": "...", "sanitizedText": "...", "entities": [...] }
            Object newSanitizedText = body.get("sanitizedText");
            if (newSanitizedText != null && !newSanitizedText.toString().isBlank()) {
                log.info("Presidio anonymization with external results applied successfully (new contract).");
                return newSanitizedText.toString();
            }

            // Backward compatibility with old wrapped contract:
            // { "status":"success", "data":{"anonymized_text":"..."} }
            Object status = body.get("status");
            if (status == null || !"success".equalsIgnoreCase(status.toString())) {
                throw new IllegalStateException("Presidio anonymize response is invalid.");
            }
            Object dataObj = body.get("data");
            if (!(dataObj instanceof Map<?, ?> dataMap)) {
                throw new IllegalStateException("Presidio anonymize data is missing/invalid.");
            }

            Object anonymizedText = dataMap.get("anonymized_text");
            if (anonymizedText == null || anonymizedText.toString().isBlank()) {
                throw new IllegalStateException("Presidio anonymize text is empty.");
            }
            log.info("Presidio anonymization with external results applied successfully (legacy contract).");
            return anonymizedText.toString();
        } catch (HttpStatusCodeException e) {
            HttpHeaders h = e.getResponseHeaders();
            if (h != null) {
                log.warn("Presidio error x-ratelimit-limit-requests={}", h.getFirst("x-ratelimit-limit-requests"));
                log.warn("Presidio error x-ratelimit-remaining-requests={}", h.getFirst("x-ratelimit-remaining-requests"));
                log.warn("Presidio error x-ratelimit-reset-requests={}", h.getFirst("x-ratelimit-reset-requests"));
                log.warn("Presidio error x-ratelimit-limit-tokens={}", h.getFirst("x-ratelimit-limit-tokens"));
                log.warn("Presidio error x-ratelimit-remaining-tokens={}", h.getFirst("x-ratelimit-remaining-tokens"));
                log.warn("Presidio error x-ratelimit-reset-tokens={}", h.getFirst("x-ratelimit-reset-tokens"));
            }

            String errorBody = e.getResponseBodyAsString();
            log.warn("Presidio error status={} body={}", e.getStatusCode(), errorBody);
            throw new IllegalStateException("Presidio call failed: " + e.getStatusCode() + " body=" + errorBody, e);
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

