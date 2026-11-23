package com.ai.openai_api_service.service.vision;

/**
 * Decoded image attachment retained in memory for the current request only.
 * Do not persist {@code bytes} or Base64 to the chat database.
 */
public record ValidatedImageAttachment(
        byte[] bytes,
        String mediaType,
        String fileName
) {
}
