package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class SessionMessageDto {

    private Long id;
    private String sessionId;
    private String originalText;
    private String sanitizedText;
    private String openaiResponse;
    private Integer tokensUsed;
    private LocalDateTime createdAt;

    public SessionMessageDto() {
    }

    public SessionMessageDto(
            Long id,
            String sessionId,
            String originalText,
            String sanitizedText,
            String openaiResponse,
            Integer tokensUsed,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.originalText = originalText;
        this.sanitizedText = sanitizedText;
        this.openaiResponse = openaiResponse;
        this.tokensUsed = tokensUsed;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getSanitizedText() {
        return sanitizedText;
    }

    public void setSanitizedText(String sanitizedText) {
        this.sanitizedText = sanitizedText;
    }

    public String getOpenaiResponse() {
        return openaiResponse;
    }

    public void setOpenaiResponse(String openaiResponse) {
        this.openaiResponse = openaiResponse;
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
}
