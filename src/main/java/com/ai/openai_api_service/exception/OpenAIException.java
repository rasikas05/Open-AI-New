package com.ai.openai_api_service.exception;

public class OpenAIException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public OpenAIException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
