package com.ai.openai_api_service.model;

import com.ai.openai_api_service.model.python_rag.SourceItem;

import java.util.List;
import java.util.Map;

public class ChatResponse {

    // Response returned to API clients for each chat request.
    private String reply;
    private boolean truncated;
    private Boolean sanitizationApplied;
    private String sanitizedUserMessage;
    private List<MessageDto> history;
    private String actionTaken;
    private String pendingTool;
    private Map<String, Object> pendingArgs;
    private String collectingTool;
    private Map<String, Object> collectedArgs;
    private String nextField;
    private Boolean nextFieldOptional;
    private Map<String, Object> m3Data;
    private List<String> suggestions;
    private List<SuggestionDto> suggestionDetails;
    private Boolean limitExceeded;
    private TokenUsageDto usage;
    private List<String> upgradeOptions;
    private String blockReason;
    private OpenAIUsage openAiUsage;
    private String retrievalReason;
    private Integer retrievalTimeMs;
    private Float maxScore;
    private List<SourceItem> sources;

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

    public List<MessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<MessageDto> history) {
        this.history = history;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public String getPendingTool() {
        return pendingTool;
    }

    public void setPendingTool(String pendingTool) {
        this.pendingTool = pendingTool;
    }

    public Map<String, Object> getPendingArgs() {
        return pendingArgs;
    }

    public void setPendingArgs(Map<String, Object> pendingArgs) {
        this.pendingArgs = pendingArgs;
    }

    public String getCollectingTool() {
        return collectingTool;
    }

    public void setCollectingTool(String collectingTool) {
        this.collectingTool = collectingTool;
    }

    public Map<String, Object> getCollectedArgs() {
        return collectedArgs;
    }

    public void setCollectedArgs(Map<String, Object> collectedArgs) {
        this.collectedArgs = collectedArgs;
    }

    public String getNextField() {
        return nextField;
    }

    public void setNextField(String nextField) {
        this.nextField = nextField;
    }

    public Boolean getNextFieldOptional() {
        return nextFieldOptional;
    }

    public void setNextFieldOptional(Boolean nextFieldOptional) {
        this.nextFieldOptional = nextFieldOptional;
    }

    public Map<String, Object> getM3Data() {
        return m3Data;
    }

    public void setM3Data(Map<String, Object> m3Data) {
        this.m3Data = m3Data;
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

    public OpenAIUsage getOpenAiUsage() {
        return openAiUsage;
    }

    public void setOpenAiUsage(OpenAIUsage openAiUsage) {
        this.openAiUsage = openAiUsage;
    }

    public String getRetrievalReason() {
        return retrievalReason;
    }

    public void setRetrievalReason(String retrievalReason) {
        this.retrievalReason = retrievalReason;
    }

    public Integer getRetrievalTimeMs() {
        return retrievalTimeMs;
    }

    public void setRetrievalTimeMs(Integer retrievalTimeMs) {
        this.retrievalTimeMs = retrievalTimeMs;
    }

    public Float getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Float maxScore) {
        this.maxScore = maxScore;
    }

    public List<SourceItem> getSources() {
        return sources;
    }

    public void setSources(List<SourceItem> sources) {
        this.sources = sources;
    }
}

