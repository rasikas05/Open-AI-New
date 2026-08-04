package com.ai.openai_api_service.service.protection;

public record ProtectionAction(
        DetectedSpan span,
        LlmExposurePolicy policyApplied,
        String placeholderType,
        String placeholderToken,
        String originalValue
) {
    public ProtectionAction(DetectedSpan span, LlmExposurePolicy policyApplied, String placeholderType) {
        this(span, policyApplied, placeholderType, null, null);
    }
}
