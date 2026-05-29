package com.ai.openai_api_service.exception;

import com.ai.openai_api_service.model.TokenUsageDto;

public class TenantQuotaExceededException extends RuntimeException {
    private final TokenUsageDto usage;

    public TenantQuotaExceededException(String message, TokenUsageDto usage) {
        super(message);
        this.usage = usage;
    }

    public TokenUsageDto getUsage() {
        return usage;
    }
}
