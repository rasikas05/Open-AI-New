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

    @Schema(
            description = "Optional routing mode. Omit or auto = existing Python route. "
                    + "m3 forces live pipeline; docs forces documentation/RAG. Lowercase only.",
            allowableValues = {"auto", "m3", "docs"},
            example = "auto"
    )
    private ChatMode mode;

    @Schema(
            description = "When set, this turn edits the given request_logs.id. "
                    + "Must be the latest active turn in the session. Creates a new request row "
                    + "and supersedes the old one.",
            example = "123"
    )
    private Long editOfRequestLogId;

    @Valid
    @Schema(example = "[]")
    private List<MessageDto> history;

    @Valid
    @Schema(description = "Optional M3 MI execution report from widget (pagination cursor)")
    private M3ClientReportDto m3ClientReport;

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

    public ChatMode getMode() {
        return mode;
    }

    public void setMode(ChatMode mode) {
        this.mode = mode;
    }

    public Long getEditOfRequestLogId() {
        return editOfRequestLogId;
    }

    public void setEditOfRequestLogId(Long editOfRequestLogId) {
        this.editOfRequestLogId = editOfRequestLogId;
    }

    public List<MessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<MessageDto> history) {
        this.history = history;
    }

    public M3ClientReportDto getM3ClientReport() {
        return m3ClientReport;
    }

    public void setM3ClientReport(M3ClientReportDto m3ClientReport) {
        this.m3ClientReport = m3ClientReport;
    }
}

