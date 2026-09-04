package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.OpenAIUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponsesParserTest {

    @Test
    void extractOutputText_readsMessageOutputText() {
        Map<String, Object> response = Map.of(
                "id", "resp_abc123",
                "output", List.of(
                        Map.of(
                                "type", "message",
                                "content", List.of(
                                        Map.of("type", "output_text", "text", "Hello from Responses API")
                                )
                        )
                ),
                "usage", Map.of(
                        "input_tokens", 42,
                        "output_tokens", 7,
                        "total_tokens", 49
                )
        );

        assertEquals("Hello from Responses API", OpenAiResponsesParser.extractOutputText(response));
        assertEquals("resp_abc123", OpenAiResponsesParser.extractResponseId(response));
        assertFalse(OpenAiResponsesParser.isTruncated(response));

        OpenAIUsage usage = OpenAiResponsesParser.extractUsage(response, "gpt-5.6-terra");
        assertEquals(42, usage.getPromptTokens());
        assertEquals(7, usage.getCompletionTokens());
        assertEquals(49, usage.getTotalTokens());
        assertEquals("gpt-5.6-terra", usage.getModel());
    }

    @Test
    void extractUsage_fallsBackToPromptCompletionFieldNames() {
        Map<String, Object> response = Map.of(
                "usage", Map.of(
                        "prompt_tokens", 10,
                        "completion_tokens", 5
                )
        );
        OpenAIUsage usage = OpenAiResponsesParser.extractUsage(response, "gpt-4.1");
        assertEquals(10, usage.getPromptTokens());
        assertEquals(5, usage.getCompletionTokens());
        assertEquals(15, usage.getTotalTokens());
    }

    @Test
    void isTruncated_detectsIncompleteMaxOutputTokens() {
        Map<String, Object> response = Map.of(
                "status", "incomplete",
                "incomplete_details", Map.of("reason", "max_output_tokens")
        );
        assertTrue(OpenAiResponsesParser.isTruncated(response));
    }
}
