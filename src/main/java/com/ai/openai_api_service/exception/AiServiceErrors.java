package com.ai.openai_api_service.exception;

/**
 * Shared constants and classification for AI-provider availability failures.
 * Only quota/credit exhaustion is non-retryable — ordinary 429 rate limits remain retryable.
 */
public final class AiServiceErrors {

    public static final String ERROR_CODE = "AI_SERVICE_UNAVAILABLE";

    public static final String USER_MESSAGE =
            "We're currently unable to process your request. Please contact your administrator for assistance.";

    private AiServiceErrors() {
    }

    /**
     * True when the OpenAI (or compatible) error body indicates non-retryable quota/credit exhaustion.
     * Ordinary rate-limit 429 responses without these markers return false.
     */
    public static boolean isQuotaOrCreditExhaustion(String bodyOrMessage) {
        if (bodyOrMessage == null || bodyOrMessage.isBlank()) {
            return false;
        }
        String lower = bodyOrMessage.toLowerCase();
        return lower.contains("insufficient_quota")
                || lower.contains("credit_balance_exhausted")
                || lower.contains("no credits remaining")
                || lower.contains("exceeded your current quota");
    }

    public static OpenAIException unavailable(String technicalDetail) {
        return new OpenAIException(USER_MESSAGE, 503, ERROR_CODE, true, technicalDetail);
    }
}
