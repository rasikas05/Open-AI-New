package com.ai.openai_api_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TenantQuotaRequest {
    @NotBlank(message = "tenantCode is required")
    private String tenantCode;

    @Min(value = 1, message = "baseLimit must be greater than 0")
    private int baseLimit;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public int getBaseLimit() {
        return baseLimit;
    }

    public void setBaseLimit(int baseLimit) {
        this.baseLimit = baseLimit;
    }
}
