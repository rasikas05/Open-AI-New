package com.ai.openai_api_service.model;

import java.util.List;

public class SuggestionResult {

    private List<String> suggestions;
    private List<SuggestionDto> details;

    public SuggestionResult() {
    }

    public SuggestionResult(List<String> suggestions, List<SuggestionDto> details) {
        this.suggestions = suggestions;
        this.details = details;
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
}
