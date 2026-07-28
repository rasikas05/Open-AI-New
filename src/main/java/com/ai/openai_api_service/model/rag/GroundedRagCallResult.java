package com.ai.openai_api_service.model.rag;

import com.ai.openai_api_service.model.OpenAIUsage;

public record GroundedRagCallResult(GroundedRagResult grounded, OpenAIUsage usage, String rawContent) {
}
