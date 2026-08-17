package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatRequestBuilderTest {

    private static final List<Map<String, String>> MESSAGES = List.of(
            Map.of("role", "user", "content", "hello")
    );

    @Test
    void isGpt56Family_recognizesGpt56TerraAndGpt5Prefix() {
        assertTrue(OpenAiChatRequestBuilder.isGpt56Family("gpt-5.6-terra"));
        assertTrue(OpenAiChatRequestBuilder.isGpt56Family("gpt-5-preview"));
        assertFalse(OpenAiChatRequestBuilder.isGpt56Family("gpt-4.1"));
        assertFalse(OpenAiChatRequestBuilder.isGpt56Family(null));
    }

    @Test
    void gpt41Body_usesLegacyMaxTokensAndTemperature() {
        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                "gpt-4.1",
                "none",
                4096,
                MESSAGES,
                0.3,
                256
        );

        assertEquals("gpt-4.1", body.get("model"));
        assertEquals(MESSAGES, body.get("messages"));
        assertEquals(0.3, body.get("temperature"));
        assertEquals(256, body.get("max_tokens"));
        assertFalse(body.containsKey("max_completion_tokens"));
        assertFalse(body.containsKey("reasoning_effort"));
    }

    @Test
    void gpt41Body_omitsOptionalFieldsWhenNull() {
        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                "gpt-4.1",
                "none",
                4096,
                MESSAGES,
                null,
                null
        );

        assertFalse(body.containsKey("temperature"));
        assertFalse(body.containsKey("max_tokens"));
        assertFalse(body.containsKey("max_completion_tokens"));
        assertFalse(body.containsKey("reasoning_effort"));
    }

    @Test
    void gpt56TerraBody_usesMaxCompletionTokensAndReasoningEffort() {
        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                "gpt-5.6-terra",
                "none",
                4096,
                MESSAGES,
                null,
                null
        );

        assertEquals("gpt-5.6-terra", body.get("model"));
        assertEquals(MESSAGES, body.get("messages"));
        assertEquals("none", body.get("reasoning_effort"));
        assertEquals(4096, body.get("max_completion_tokens"));
        assertFalse(body.containsKey("max_tokens"));
        assertFalse(body.containsKey("temperature"));
    }

    @Test
    void gpt56TerraBody_usesExplicitMaxTokensAsCompletionBudget() {
        Map<String, Object> body = OpenAiChatRequestBuilder.buildChatCompletionBody(
                "gpt-5.6-terra",
                "none",
                4096,
                MESSAGES,
                null,
                256
        );

        assertEquals(256, body.get("max_completion_tokens"));
        assertFalse(body.containsKey("max_tokens"));
    }

    @Test
    void gpt56TerraBody_includesTemperatureOnlyWhenReasoningEffortIsNone() {
        Map<String, Object> withNone = OpenAiChatRequestBuilder.buildChatCompletionBody(
                "gpt-5.6-terra",
                "none",
                4096,
                MESSAGES,
                0.3,
                256
        );
        assertEquals("none", withNone.get("reasoning_effort"));
        assertEquals(0.3, withNone.get("temperature"));

        Map<String, Object> withMedium = OpenAiChatRequestBuilder.buildChatCompletionBody(
                "gpt-5.6-terra",
                "medium",
                4096,
                MESSAGES,
                0.3,
                256
        );
        assertEquals("medium", withMedium.get("reasoning_effort"));
        assertFalse(withMedium.containsKey("temperature"));
    }

    @Test
    void effectiveReasoningEffort_defaultsToNoneForGpt56() {
        assertEquals("none", OpenAiChatRequestBuilder.effectiveReasoningEffort("gpt-5.6-terra", null));
        assertEquals("medium", OpenAiChatRequestBuilder.effectiveReasoningEffort("gpt-5.6-terra", " medium "));
        assertNull(OpenAiChatRequestBuilder.effectiveReasoningEffort("gpt-4.1", "none"));
    }
}
