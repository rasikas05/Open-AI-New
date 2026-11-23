package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.model.OpenAIUsage;

import java.util.List;

/**
 * Structured result of Bedrock/OpenAI vision-based documentation search query generation.
 * Does not contain image bytes or Base64.
 */
public record VisionQueryGenerationResult(
        String imageSummary,
        List<String> visibleTextHints,
        List<String> searchQueries,
        OpenAIUsage usage,
        long elapsedMs
) {
}
