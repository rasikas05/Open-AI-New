package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class ChatRequest {

    @NotBlank(message = "tenantCode is required")
    @Schema(example = "infor")
    private String tenantCode;

    @NotBlank(message = "userId is required")
    @Schema(example = "rasika")
    private String userId;

    @NotBlank(message = "sessionId is required")
    @Schema(example = "session-001")
    private String sessionId;

    @NotBlank(message = "userMessage is required")
    @Schema(example = "String")
    private String userMessage;

    @Valid
    @Schema(example = "[]")
    private List<MessageDto> history;

    public ChatRequest() {
    }

    public ChatRequest(String userMessage, List<MessageDto> history) {
        this.userMessage = userMessage;
        this.history = history;
    }

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

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public List<MessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<MessageDto> history) {
        this.history = history;
    }
}

