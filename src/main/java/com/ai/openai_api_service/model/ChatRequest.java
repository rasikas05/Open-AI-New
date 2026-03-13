package com.ai.openai_api_service.model;

import java.util.List;

public class ChatRequest {

    private String userMessage;
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

