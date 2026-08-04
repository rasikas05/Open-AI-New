package com.ai.openai_api_service.service.protection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Facade: detect → classify (Optional) → apply policy.
 * Catalog never invents Unclassified; missing classification → temporary Unclassified → BLOCK.
 *
 * <p>Phase 7A: additive stage at OpenAI egress (temporary order). Prefer
 * {@link #protect(ProtectionSession, ProtectionContext)} so callers share one request-state object.
 */
@Service
public class BusinessInformationProtectionService {

    private final boolean enabled;
    private final BusinessInformationDetector detector;
    private final FieldClassificationCatalog catalog;
    private final LLMPolicyApplier policyApplier;

    public BusinessInformationProtectionService(
            BusinessInformationDetector detector,
            FieldClassificationCatalog catalog,
            LLMPolicyApplier policyApplier,
            @Value("${business-information.protection.enabled:false}") boolean enabled
    ) {
        this.detector = detector;
        this.catalog = catalog;
        this.policyApplier = policyApplier;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Applies business protection into {@code session}. No-op when flag is off.
     */
    public ProtectionSession protect(ProtectionSession session, ProtectionContext context) {
        if (session == null) {
            return null;
        }
        if (!enabled || (context != null && !context.enabled())) {
            // BIP off: leave business fields clear so PII runs on original (7B).
            session.clearBusinessResult();
            return session;
        }
        String input = session.inputForBusinessProtection();
        if (input == null || input.isBlank()) {
            session.clearBusinessResult();
            return session;
        }
        ProtectionContext effectiveContext = context != null
                ? context
                : ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true);

        ProtectedText result = protect(input, effectiveContext);
        session.applyBusinessResult(result);
        return session;
    }

    public ProtectedText protect(String text, ProtectionContext context) {
        if (!enabled || text == null || text.isBlank()) {
            return new ProtectedText(text, List.of());
        }
        if (context != null && !context.enabled()) {
            return new ProtectedText(text, List.of());
        }
        ProtectionContext effectiveContext = context != null
                ? context
                : ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true);

        List<DetectedSpan> spans = detector.detect(text);

        List<SpanClassification> classified = new ArrayList<>();
        for (DetectedSpan span : spans) {
            Optional<FieldClassification> classification = catalog.lookup(span.code());
            classified.add(new SpanClassification(span, classification));
        }
        return policyApplier.apply(text, classified, effectiveContext);
    }

    /**
     * Convenience used by OpenAIService: returns protected text string (or original when disabled).
     */
    public String protectText(String text, ProtectionPurpose purpose) {
        ProtectionSession session = ProtectionSession.fromPiiSanitized(text);
        protect(session, ProtectionContext.forPurpose(purpose, true));
        return session.textForLlm();
    }
}
