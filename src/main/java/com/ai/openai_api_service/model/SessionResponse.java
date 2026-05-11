package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class SessionResponse {

    private Long id;
    private String tenantCode;
    private String userId;
    private String sessionId;
    private String status;
    private int tokensUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime endTime;

    public SessionResponse() {
    }

    public SessionResponse(Long id, String tenantCode, String userId, String sessionId, String status, int tokensUsed, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime endTime) {
        this.id = id;
        this.tenantCode = tenantCode;
        this.userId = userId;
        this.sessionId = sessionId;
        this.status = status;
        this.tokensUsed = tokensUsed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
