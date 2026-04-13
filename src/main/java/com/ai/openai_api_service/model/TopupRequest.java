package com.ai.openai_api_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TopupRequest {
    @NotBlank(message = "tenantId is required")
    private String tenantId;

    @Min(value = 1, message = "tokens must be greater than 0")
    private int tokens;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }
}
