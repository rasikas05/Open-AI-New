package com.ai.openai_api_service.model;

import java.util.List;

public record RequestUnderstandResult(
        RequestUnderstandType type,
        String response,
        List<String> queries,
        OpenAIUsage usage
) {
}
