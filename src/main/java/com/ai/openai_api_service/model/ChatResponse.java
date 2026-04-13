package com.ai.openai_api_service.model;

import java.util.List;

public class ChatResponse {

    // Response returned to API clients for each chat request.
    private String reply;
    private boolean truncated;
    private Boolean sanitizationApplied;
    private String sanitizedUserMessage;
    private List<String> suggestions;
    private List<SuggestionDto> suggestionDetails;
    private Boolean limitExceeded;
    private TokenUsageDto usage;
    private List<String> upgradeOptions;
    private String blockReason;

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

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public List<SuggestionDto> getSuggestionDetails() {
        return suggestionDetails;
    }

    public void setSuggestionDetails(List<SuggestionDto> suggestionDetails) {
        this.suggestionDetails = suggestionDetails;
    }

    public Boolean getLimitExceeded() {
        return limitExceeded;
    }

    public void setLimitExceeded(Boolean limitExceeded) {
        this.limitExceeded = limitExceeded;
    }

    public TokenUsageDto getUsage() {
        return usage;
    }

    public void setUsage(TokenUsageDto usage) {
        this.usage = usage;
    }

    public List<String> getUpgradeOptions() {
        return upgradeOptions;
    }

    public void setUpgradeOptions(List<String> upgradeOptions) {
        this.upgradeOptions = upgradeOptions;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }
}

