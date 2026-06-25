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
import java.util.regex.Pattern;

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
        COMPREHEND_TO_PRESIDIO.put("DRIVER_ID", "US_DRIVER_LICENSE");  // Map to handle license detections
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
            log.debug("detectAndAnonymize: disabled or blank text, skipping Comprehend/Presidio flow");
            return createResponseMap(text, text, List.of());
        }

        try {
            log.info("Detecting PII using AWS Comprehend");
            List<PiiEntityDto> comprehendResults = comprehendService.detectPii(text);
            log.info("AWS Comprehend detected {} entities", comprehendResults.size());
            log.debug("Comprehend entities: {}", comprehendResults.stream().map(PiiEntityDto::getType).toList());

            log.info("Converting {} Comprehend results to Presidio format", comprehendResults.size());
            List<PresidioAnalyzerResult> presidioResults = convertToPresidioFormat(comprehendResults);
            log.debug("Presidio external results count={}", presidioResults.size());

            String sanitizedText;
            if (presidioResults.isEmpty()) {
                log.info("No Presidio external results produced by Comprehend; invoking Presidio raw anonymization fallback");
                sanitizedText = presidioService.sanitizeText(text);
            } else {
                log.info("Anonymizing text using Presidio with external Comprehend results");
                sanitizedText = presidioService.sanitizeTextWithExternalResults(text, presidioResults);
            }

            log.debug("Presidio anonymization result='{}'", sanitizedText);

            sanitizedText = applyFallbackSanitization(text, sanitizedText);
            return createResponseMap(text, sanitizedText, comprehendResults);
        } catch (Exception e) {
            log.error("Error in Comprehend-based anonymization: {}", e.getMessage(), e);
            try {
                log.warn("Comprehend unavailable; falling back to Presidio-only anonymization");
                String sanitizedText = presidioService.sanitizeTextSafe(text);
                return createResponseMap(text, sanitizedText, List.of());
            } catch (Exception presidioError) {
                log.warn("Presidio fallback failed, using original text: {}", presidioError.getMessage());
                return createResponseMap(text, text, List.of());
            }
        }
    }

    /**
     * Convert AWS Comprehend PiiEntityDto to Presidio AnalyzerResult format
     */
    private static final Pattern MARKDOWN_EMAIL_LINK_PATTERN = Pattern.compile("\\[([^\\]]+@[A-Za-z0-9._%+-]+\\.[A-Za-z]{2,})\\]\\(mailto:[^)]+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+?\\d[\\d\\- ]{7,}\\d)\\b");
    private static final Pattern INVOICE_NUMBER_PATTERN = Pattern.compile("(?i)\\b((?:invoice|inv|order|ord|bill|receipt)\\s*(?:no\\.?|number|id)?\\s*[:#]?\\s*)([0-9]{4,})\\b");

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

    private String applyFallbackSanitization(String originalText, String sanitizedText) {
        if (sanitizedText == null) {
            return originalText;
        }

        String result = sanitizedText;

        // Replace markdown-formatted email links entirely when present
        result = MARKDOWN_EMAIL_LINK_PATTERN.matcher(result).replaceAll("[EMAIL]");

        // Replace any remaining plain email addresses
        result = EMAIL_PATTERN.matcher(result).replaceAll("[EMAIL]");

        // Replace phone numbers as a fallback
        result = PHONE_PATTERN.matcher(result).replaceAll("[PHONE]");

        // Replace invoice/order-related numeric identifiers
        result = INVOICE_NUMBER_PATTERN.matcher(result).replaceAll("$1[NUMBER]");

        return result;
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
