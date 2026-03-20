package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ChatRequest {

    @NotBlank(message = "userMessage is required")
    @Schema(example = "String")
    private String userMessage;

    @Valid
    @Schema(example = "[]")
    private List<MessageDto> history;

    public ChatRequest() {
    }

    public ChatRequest(String userMessage, List<MessageDto> history) {
        this.userMessage = userMessage;
        this.history = history;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public List<MessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<MessageDto> history) {
        this.history = history;
    }
}

