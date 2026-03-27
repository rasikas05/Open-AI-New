package com.ai.openai_api_service.model;

public class ChatResponse {

    // Response returned to API clients for each chat request.
    private String reply;
    private boolean truncated;
    private Boolean sanitizationApplied;
    private String sanitizedUserMessage;

    public ChatResponse() {
    }

    public ChatResponse(String reply, boolean truncated) {
        this.reply = reply;
        this.truncated = truncated;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public Boolean getSanitizationApplied() {
        return sanitizationApplied;
    }

    public void setSanitizationApplied(Boolean sanitizationApplied) {
        this.sanitizationApplied = sanitizationApplied;
    }

    public String getSanitizedUserMessage() {
        return sanitizedUserMessage;
    }

    public void setSanitizedUserMessage(String sanitizedUserMessage) {
        this.sanitizedUserMessage = sanitizedUserMessage;
    }
}

