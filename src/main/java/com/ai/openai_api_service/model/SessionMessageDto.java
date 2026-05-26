package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class SessionMessageDto {

    private Long id;
    private String sessionId;
    private String title;
    private String originalText;
    private String sanitizedText;
    private String openaiResponse;
    private Boolean sanitizedFlag;
    private String actionTaken;
    private Integer tokensUsed;
    private LocalDateTime createdAt;

    public SessionMessageDto() {
    }

    public SessionMessageDto(
            Long id,
            String sessionId,
            String title,
            String originalText,
            String sanitizedText,
            String openaiResponse,
            Boolean sanitizedFlag,
            String actionTaken,
            Integer tokensUsed,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.title = title;
        this.originalText = originalText;
        this.sanitizedText = sanitizedText;
        this.openaiResponse = openaiResponse;
        this.sanitizedFlag = sanitizedFlag;
        this.actionTaken = actionTaken;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Boolean getSanitizedFlag() {
        return sanitizedFlag;
    }

    public void setSanitizedFlag(Boolean sanitizedFlag) {
        this.sanitizedFlag = sanitizedFlag;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
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
