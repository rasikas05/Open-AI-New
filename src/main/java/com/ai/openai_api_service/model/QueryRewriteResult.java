package com.ai.openai_api_service.model;

import java.util.List;

public record QueryRewriteResult(List<String> queries, OpenAIUsage usage) {
}
