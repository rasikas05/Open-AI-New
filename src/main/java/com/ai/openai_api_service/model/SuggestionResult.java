package com.ai.openai_api_service.model;

import java.util.List;

public class SuggestionResult {

    private List<String> suggestions;
    private List<SuggestionDto> details;

    public SuggestionResult() {
    }

    private int promptTokens;
    private int completionTokens;

    public SuggestionResult(List<String> suggestions, List<SuggestionDto> details) {
        this(suggestions, details, 0, 0);
    }

    public SuggestionResult(List<String> suggestions, List<SuggestionDto> details, int promptTokens, int completionTokens) {
        this.suggestions = suggestions;
        this.details = details;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public List<SuggestionDto> getDetails() {
        return details;
    }

    public void setDetails(List<SuggestionDto> details) {
        this.details = details;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return Math.max(0, promptTokens) + Math.max(0, completionTokens);
    }
}
