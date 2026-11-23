package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class ChatPrepareRequest {

    @NotBlank(message = "tenantCode is required")
    @Schema(example = "EM47JBNURMACJZ8F_DEM")
    private String tenantCode;

    @NotBlank(message = "userId is required")
    @Schema(example = "RASIKAS")
    private String userId;

    @NotBlank(message = "sessionId is required")
    @Schema(example = "session-001")
    private String sessionId;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
