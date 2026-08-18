package com.ai.openai_api_service.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds OpenAI Chat Completions request bodies with model-family-specific parameters.
 * GPT-4.x uses {@code max_tokens}; GPT-5.6 family uses {@code max_completion_tokens}
 * and {@code reasoning_effort}.
 */
final class OpenAiChatRequestBuilder {

    private OpenAiChatRequestBuilder() {
    }

    static boolean isGpt56Family(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalized = model.toLowerCase(Locale.ROOT);
        return normalized.startsWith("gpt-5.6") || normalized.startsWith("gpt-5");
    }

    static String effectiveReasoningEffort(String model, String configuredEffort) {
        if (!isGpt56Family(model)) {
            return null;
        }
        if (configuredEffort == null || configuredEffort.isBlank()) {
            return "none";
        }
        return configuredEffort.trim().toLowerCase(Locale.ROOT);
    }

    static Map<String, Object> buildChatCompletionBody(
            String model,
            String configuredReasoningEffort,
            int defaultMaxCompletionTokens,
            List<Map<String, String>> messages,
            Double temperature,
            Integer maxTokens
    ) {
        return buildChatCompletionBody(
                model,
                configuredReasoningEffort,
                defaultMaxCompletionTokens,
                messages,
                temperature,
                maxTokens,
                false
        );
    }

    static Map<String, Object> buildChatCompletionBody(
            String model,
            String configuredReasoningEffort,
            int defaultMaxCompletionTokens,
            List<Map<String, String>> messages,
            Double temperature,
            Integer maxTokens,
            boolean jsonObjectResponse
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        if (isGpt56Family(model)) {
            String reasoningEffort = effectiveReasoningEffort(model, configuredReasoningEffort);
            body.put("reasoning_effort", reasoningEffort);
            int tokenBudget = maxTokens != null ? maxTokens : defaultMaxCompletionTokens;
            body.put("max_completion_tokens", tokenBudget);
            if (temperature != null && "none".equals(reasoningEffort)) {
                body.put("temperature", temperature);
            }
        } else {
            if (temperature != null) {
                body.put("temperature", temperature);
            }
            if (maxTokens != null) {
                body.put("max_tokens", maxTokens);
            }
        }
        if (jsonObjectResponse) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }
}
