package com.ai.openai_api_service.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TenantQuotaUpdateRequest {
    @NotBlank(message = "tenantId is required")
    private String tenantId;

    @Min(value = 1, message = "baseLimit must be greater than 0")
    private int baseLimit;

    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
