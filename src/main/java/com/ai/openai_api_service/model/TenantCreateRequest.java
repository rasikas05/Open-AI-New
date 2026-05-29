package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class TenantCreateRequest {

    @NotBlank(message = "tenantCode is required")
    @Schema(example = "infor")
    private String tenantCode;

    @NotBlank(message = "name is required")
    @Schema(example = "Infor Tenant")
    private String name;

    public TenantCreateRequest() {
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
