package com.ai.openai_api_service.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Request-level chat routing mode for {@code POST /api/chat/comprehend}.
 * Wire values are lowercase only: {@code auto}, {@code m3}, {@code docs}.
 */
public enum ChatMode {
    AUTO("auto"),
    M3("m3"),
    DOCS("docs");

    private final String wireValue;

    ChatMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ChatMode fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        for (ChatMode mode : values()) {
            if (mode.wireValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown mode: " + value);
    }
}
