package com.ai.openai_api_service.service.protection;

import java.util.Optional;

/**
 * Span plus optional classification. Catalog miss → empty classification (Unclassified).
 */
public record SpanClassification(DetectedSpan span, Optional<FieldClassification> classification) {
    public SpanClassification {
        classification = classification == null ? Optional.empty() : classification;
    }
}
