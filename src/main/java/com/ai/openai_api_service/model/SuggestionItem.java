package com.ai.openai_api_service.model;

public class SuggestionItem {

    private String text;
    private SuggestionCategory category;
    private double score;
    private String source;

    public SuggestionItem() {
        this.category = SuggestionCategory.GENERIC;
        this.score = 0.5d;
    }

    public SuggestionItem(String text, SuggestionCategory category, double score, String source) {
        this.text = text;
        this.category = category == null ? SuggestionCategory.GENERIC : category;
        this.score = score;
        this.source = source;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public SuggestionCategory getCategory() {
        return category;
    }

    public void setCategory(SuggestionCategory category) {
        this.category = category;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
