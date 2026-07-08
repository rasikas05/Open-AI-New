package com.ai.openai_api_service.exception;

public class InvalidLexSlotException extends RuntimeException {

    public InvalidLexSlotException(String userMessage) {
        super(userMessage);
    }

    public String getUserMessage() {
        return getMessage();
    }
}
