package com.ai.openai_api_service.service;

import com.ai.openai_api_service.entity.PiiEntityDto;
import com.ai.openai_api_service.model.PresidioAnalyzerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComprehendAnonymizationService {
    private static final Logger log = LoggerFactory.getLogger(ComprehendAnonymizationService.class);

    private final ComprehendService comprehendService;
    private final PresidioService presidioService;

    @Value("${comprehend.anonymization.enabled:true}")
    private boolean enabled;

    // Map Comprehend entity types to Presidio entity types
    private static final Map<String, String> COMPREHEND_TO_PRESIDIO = new HashMap<>();

    static {
        COMPREHEND_TO_PRESIDIO.put("NAME", "PERSON");
        COMPREHEND_TO_PRESIDIO.put("PHONE", "PHONE_NUMBER");
        COMPREHEND_TO_PRESIDIO.put("EMAIL", "EMAIL_ADDRESS");
        COMPREHEND_TO_PRESIDIO.put("ADDRESS", "LOCATION");
        COMPREHEND_TO_PRESIDIO.put("BANK_ACCOUNT_NUMBER", "BANK_ACCOUNT_NUMBER");
        COMPREHEND_TO_PRESIDIO.put("BANK_ROUTING_NUMBER", "BANK_ROUTING_NUMBER");
        COMPREHEND_TO_PRESIDIO.put("CREDIT_DEBIT_NUMBER", "CREDIT_DEBIT_NUMBER");
        COMPREHEND_TO_PRESIDIO.put("DRIVER_ID", "DRIVER_LICENSE");
        COMPREHEND_TO_PRESIDIO.put("PASSPORT", "PASSPORT");
        COMPREHEND_TO_PRESIDIO.put("AWS_ACCESS_KEY", "AWS_ACCESS_KEY");
        COMPREHEND_TO_PRESIDIO.put("AWS_SECRET_KEY", "AWS_SECRET_KEY");
        // Add more mappings as needed
    }

    public ComprehendAnonymizationService(
            ComprehendService comprehendService,
            PresidioService presidioService
    ) {
        this.comprehendService = comprehendService;
        this.presidioService = presidioService;
    }

    /**
     * Detect PII using Comprehend and anonymize using Presidio anonymizer
     */
    public Map<String, Object> detectAndAnonymize(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return createResponseMap(text, text, List.of());
        }

        try {
            // Step 1: Detect PII using Comprehend
            log.info("Detecting PII using AWS Comprehend");
            List<PiiEntityDto> comprehendResults = comprehendService.detectPii(text);

            // Step 2: Convert Comprehend results to Presidio format
            log.info("Converting {} Comprehend results to Presidio format", comprehendResults.size());
            List<PresidioAnalyzerResult> presidioResults = convertToPresidioFormat(comprehendResults);

            // Step 3: Anonymize using Presidio anonymizer with Comprehend-detected entities
            log.info("Anonymizing text using Presidio with external Comprehend results");
            String sanitizedText = presidioService.sanitizeTextWithExternalResults(text, presidioResults);

            return createResponseMap(text, sanitizedText, comprehendResults);
        } catch (Exception e) {
            log.error("Error in Comprehend-based anonymization: {}", e.getMessage(), e);
            throw new IllegalStateException("Comprehend anonymization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert AWS Comprehend PiiEntityDto to Presidio AnalyzerResult format
     */
    private List<PresidioAnalyzerResult> convertToPresidioFormat(List<PiiEntityDto> comprehendEntities) {
        return comprehendEntities.stream()
                .map(entity -> {
                    String presidioType = COMPREHEND_TO_PRESIDIO.getOrDefault(entity.getType(), entity.getType());
                    // Comprehend score is 0-1, use as-is for Presidio (which also expects 0-1)
                    return new PresidioAnalyzerResult(
                            presidioType,
                            entity.getStart(),
                            entity.getEnd(),
                            entity.getScore()
                    );
                })
                .toList();
    }

    /**
     * Create standardized response map
     */
    private Map<String, Object> createResponseMap(String originalText, String sanitizedText, List<PiiEntityDto> entities) {
        Map<String, Object> response = new HashMap<>();
        response.put("originalText", originalText);
        response.put("sanitizedText", sanitizedText);
        response.put("entities", entities);
        response.put("detector", "AWS_COMPREHEND");
        return response;
    }
}
