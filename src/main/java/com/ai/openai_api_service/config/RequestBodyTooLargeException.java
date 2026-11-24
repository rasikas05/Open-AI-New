package com.ai.openai_api_service.config;

import java.io.IOException;

/**
 * Thrown when a counting servlet input stream exceeds the configured HTTP body limit.
 * Message is safe for clients (never includes request body or Base64).
 */
public final class RequestBodyTooLargeException extends IOException {

    public static final String SAFE_MESSAGE = "Request body exceeds the maximum allowed size.";

    public RequestBodyTooLargeException() {
        super(SAFE_MESSAGE);
    }
}
