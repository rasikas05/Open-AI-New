package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class ChatPrepareResponse {

    @Schema(description = "Server-generated message id for IDM upload and chat association")
    private String messageId;

    @Schema(description = "Echo of the prepared session id")
    private String sessionId;

    public ChatPrepareResponse() {
    }

    public ChatPrepareResponse(String messageId, String sessionId) {
        this.messageId = messageId;
        this.sessionId = sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
