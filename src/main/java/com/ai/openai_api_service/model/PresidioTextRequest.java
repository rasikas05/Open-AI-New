package com.ai.openai_api_service.model;

import jakarta.validation.constraints.NotBlank;

public class PresidioTextRequest {

    @NotBlank(message = "text is required")
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
