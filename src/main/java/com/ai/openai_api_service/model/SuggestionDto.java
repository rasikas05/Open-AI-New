package com.ai.openai_api_service.model;

public class SuggestionDto {

    private String text;
    private String source;

    public SuggestionDto() {
    }

    public SuggestionDto(String text, String source) {
        this.text = text;
        this.source = source;
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
}
