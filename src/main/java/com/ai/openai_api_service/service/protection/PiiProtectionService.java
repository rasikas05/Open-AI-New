package com.ai.openai_api_service.service.protection;

import com.ai.openai_api_service.service.ComprehendAnonymizationService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Applies Comprehend/Presidio PII anonymization onto {@link ProtectionSession}
 * (Decision #26–#28). Mutates only via {@link ProtectionSession#applyPiiSanitizedText(String)}.
 */
@Service
public class PiiProtectionService {

    private final ComprehendAnonymizationService comprehendAnonymizationService;

    public PiiProtectionService(ComprehendAnonymizationService comprehendAnonymizationService) {
        this.comprehendAnonymizationService = comprehendAnonymizationService;
    }

    /**
     * PII runs on business-protected text when present, otherwise original.
     */
    public ProtectionSession protect(ProtectionSession session) {
        if (session == null) {
            return null;
        }
        String input = session.businessProtectedText() != null
                ? session.businessProtectedText()
                : session.originalText();
        if (input == null) {
            session.applyPiiSanitizedText(null);
            return session;
        }
        String sanitized = anonymize(input);
        session.applyPiiSanitizedText(sanitized);
        return session;
    }

    public String anonymize(String text) {
        if (text == null) {
            return null;
        }
        Map<String, Object> result = comprehendAnonymizationService.detectAndAnonymize(text);
        Object sanitized = result != null ? result.get("sanitizedText") : null;
        return sanitized != null ? sanitized.toString() : text;
    }
}
