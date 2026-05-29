package com.ai.openai_api_service.model;

public class SuggestionDto {

    private String text;
    private String source;
    private SuggestionCategory category;
    private Double score;

    public SuggestionDto() {
    }

    public SuggestionDto(String text, String source) {
        this(text, source, SuggestionCategory.GENERIC, null);
    }

    public SuggestionDto(String text, String source, SuggestionCategory category, Double score) {
        this.text = text;
        this.source = source;
        this.category = category == null ? SuggestionCategory.GENERIC : category;
        this.score = score;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public SuggestionCategory getCategory() {
        return category;
    }

    public void setCategory(SuggestionCategory category) {
        this.category = category;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
