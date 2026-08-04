package com.ai.openai_api_service.service.protection;

import java.util.List;

/**
 * Co-located classification + detection metadata (V1). Catalog never invents Unclassified rows.
 *
 * <p>For {@code M3_IDENTIFIER} rows, {@code maxLength} and {@code characterSet} are required
 * catalog metadata (no silent defaults in the validator).
 */
public record FieldClassification(
        String code,
        String meaning,
        InformationCategory category,
        LlmExposurePolicy llmExposurePolicy,
        String placeholderType,
        String confidence,
        String protectionReason,
        List<String> detectionKeywords,
        List<String> detectionAliases,
        String valueShapeKey,
        Integer maxLength,
        IdentifierCharacterSet characterSet
) {
    public FieldClassification {
        detectionKeywords = detectionKeywords == null ? List.of() : List.copyOf(detectionKeywords);
        detectionAliases = detectionAliases == null ? List.of() : List.copyOf(detectionAliases);
    }
}
