package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class TenantCreateRequest {

    @NotBlank(message = "tenantId is required")
    @Schema(example = "infor")
    private String tenantId;

    @NotBlank(message = "name is required")
    @Schema(example = "Infor Tenant")
    private String name;

    public TenantCreateRequest() {
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
