package com.ai.openai_api_service.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceErrorsTest {

    @Test
    void detectsQuotaExhaustionMarkers() {
        assertTrue(AiServiceErrors.isQuotaOrCreditExhaustion(
                "{\"error\":{\"code\":\"credit_balance_exhausted\",\"type\":\"insufficient_quota\"}}"));
        assertTrue(AiServiceErrors.isQuotaOrCreditExhaustion("You have no credits remaining."));
        assertFalse(AiServiceErrors.isQuotaOrCreditExhaustion("Rate limit exceeded. Please retry later."));
        assertFalse(AiServiceErrors.isQuotaOrCreditExhaustion(null));
    }
}
