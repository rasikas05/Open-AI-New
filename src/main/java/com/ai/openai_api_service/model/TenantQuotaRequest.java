package com.ai.openai_api_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TenantQuotaRequest {
    @NotBlank(message = "tenantId is required")
    private String tenantId;

    @Min(value = 1, message = "baseLimit must be greater than 0")
    private int baseLimit;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getBaseLimit() {
        return baseLimit;
    }

    public void setBaseLimit(int baseLimit) {
        this.baseLimit = baseLimit;
    }
}
