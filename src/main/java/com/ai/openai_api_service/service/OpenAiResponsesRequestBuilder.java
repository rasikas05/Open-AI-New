package com.ai.openai_api_service.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds OpenAI Responses API request bodies for the chatWithoutPersistence POC.
 */
final class OpenAiResponsesRequestBuilder {

    private OpenAiResponsesRequestBuilder() {
    }

    static Map<String, Object> buildResponsesBody(
            String model,
            String configuredReasoningEffort,
            int defaultMaxCompletionTokens,
            String systemContent,
            String userContent,
            String previousResponseId
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", buildInput(systemContent, userContent));

        if (previousResponseId != null && !previousResponseId.isBlank()) {
            body.put("previous_response_id", previousResponseId.trim());
        }

        if (OpenAiChatRequestBuilder.isGpt56Family(model)) {
            String reasoningEffort = OpenAiChatRequestBuilder.effectiveReasoningEffort(model, configuredReasoningEffort);
            if (reasoningEffort != null) {
                body.put("reasoning", Map.of("effort", reasoningEffort));
            }
            body.put("max_output_tokens", defaultMaxCompletionTokens);
        } else {
            body.put("max_output_tokens", defaultMaxCompletionTokens);
        }

        return body;
    }

    static List<Map<String, String>> buildInput(String systemContent, String userContent) {
        List<Map<String, String>> input = new ArrayList<>();
        if (systemContent != null && !systemContent.isBlank()) {
            input.add(Map.of("role", "system", "content", systemContent));
        }
        if (userContent != null && !userContent.isBlank()) {
            input.add(Map.of("role", "user", "content", userContent));
        }
        return input;
    }
}
