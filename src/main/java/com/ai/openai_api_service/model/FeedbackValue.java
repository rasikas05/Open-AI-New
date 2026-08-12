package com.ai.openai_api_service.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Feedback rating for an assistant response ({@code request_logs} turn).
 * Wire values are lowercase only: {@code good}, {@code bad}.
 */
public enum FeedbackValue {
    GOOD("good"),
    BAD("bad");

    private final String wireValue;

    FeedbackValue(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static FeedbackValue fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        for (FeedbackValue feedback : values()) {
            if (feedback.wireValue.equals(value)) {
                return feedback;
            }
        }
        throw new IllegalArgumentException("Unknown feedback: " + value);
    }
}
