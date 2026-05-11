package com.ai.openai_api_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TopupRequest {
    @NotBlank(message = "tenantCode is required")
    private String tenantCode;

    @Min(value = 1, message = "tokens must be greater than 0")
    private int tokens;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }
}
