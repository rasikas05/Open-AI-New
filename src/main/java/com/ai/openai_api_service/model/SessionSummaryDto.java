package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class SessionSummaryDto {

    private String sessionId;
    private String title;
    private String status;
    private Integer tokensUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SessionSummaryDto() {
    }

    public SessionSummaryDto(
            String sessionId,
            String title,
            String status,
            Integer tokensUsed,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.sessionId = sessionId;
        this.title = title;
        this.status = status;
        this.tokensUsed = tokensUsed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
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
}
