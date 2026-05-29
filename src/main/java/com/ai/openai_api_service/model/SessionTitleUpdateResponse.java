package com.ai.openai_api_service.model;

public class SessionTitleUpdateResponse {

    private String sessionId;
    private String title;
    private String message;

    public SessionTitleUpdateResponse() {
    }

    public SessionTitleUpdateResponse(String sessionId, String title, String message) {
        this.sessionId = sessionId;
        this.title = title;
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
