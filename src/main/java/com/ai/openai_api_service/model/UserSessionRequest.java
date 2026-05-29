package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UserSessionRequest {

    @NotBlank(message = "sessionId is required")
    @Schema(example = "session-001")
    private String sessionId;

    @NotNull(message = "tokenLimit is required")
    @Schema(example = "1000")
    private Integer tokenLimit;

    public UserSessionRequest() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getTokenLimit() {
        return tokenLimit;
    }

    public void setTokenLimit(Integer tokenLimit) {
        this.tokenLimit = tokenLimit;
    }
}
