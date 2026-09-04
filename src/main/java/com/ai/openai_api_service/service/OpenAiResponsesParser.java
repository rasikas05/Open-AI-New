package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;

import java.util.List;
import java.util.Map;

/**
 * Parses OpenAI Responses API JSON into text, usage, and response id.
 */
final class OpenAiResponsesParser {

    private OpenAiResponsesParser() {
    }

    static String extractResponseId(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object id = response.get("id");
        return id != null ? id.toString() : null;
    }

    static String extractOutputText(Map<String, Object> response) {
        if (response == null) {
            throw new OpenAIException("No response from OpenAI Responses API.", 502);
        }
        Object outputObj = response.get("output");
        if (!(outputObj instanceof List<?> outputList)) {
            throw new OpenAIException("Responses API missing output array.", 502);
        }
        StringBuilder text = new StringBuilder();
        for (Object item : outputList) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object type = itemMap.get("type");
            if (type != null && !"message".equals(type.toString())) {
                continue;
            }
            Object contentObj = itemMap.get("content");
            if (!(contentObj instanceof List<?> contentList)) {
                continue;
            }
            for (Object part : contentList) {
                if (!(part instanceof Map<?, ?> partMap)) {
                    continue;
                }
                Object partType = partMap.get("type");
                if (partType != null && "output_text".equals(partType.toString())) {
                    Object textObj = partMap.get("text");
                    if (textObj != null) {
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append(textObj.toString());
                    }
                }
            }
        }
        if (text.isEmpty()) {
            throw new OpenAIException("Responses API returned no output_text.", 502);
        }
        return text.toString();
    }

    static boolean isTruncated(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object status = response.get("status");
        if (status != null && "incomplete".equals(status.toString())) {
            return true;
        }
        Object incomplete = response.get("incomplete_details");
        if (incomplete instanceof Map<?, ?> details) {
            Object reason = details.get("reason");
            return reason != null && "max_output_tokens".equals(reason.toString());
        }
        return false;
    }

    static OpenAIUsage extractUsage(Map<String, Object> response, String modelUsed) {
        OpenAIUsage usage = new OpenAIUsage();
        usage.setModel(modelUsed);
        if (response == null) {
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
            return usage;
        }
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
            return usage;
        }
        Integer input = firstInt(usageMap.get("input_tokens"), usageMap.get("prompt_tokens"));
        Integer output = firstInt(usageMap.get("output_tokens"), usageMap.get("completion_tokens"));
        Integer total = firstInt(usageMap.get("total_tokens"), null);
        usage.setPromptTokens(input != null ? input : 0);
        usage.setCompletionTokens(output != null ? output : 0);
        if (total != null) {
            usage.setTotalTokens(total);
        } else {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
        return usage;
    }

    private static Integer firstInt(Object primary, Object fallback) {
        Integer value = toInt(primary);
        if (value != null) {
            return value;
        }
        return toInt(fallback);
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
