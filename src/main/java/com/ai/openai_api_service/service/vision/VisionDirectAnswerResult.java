package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.model.OpenAIUsage;

/**
 * Free-text vision answer. Does not contain image bytes or Base64.
 */
public record VisionDirectAnswerResult(
        String reply,
        OpenAIUsage usage,
        long elapsedMs
) {
}
