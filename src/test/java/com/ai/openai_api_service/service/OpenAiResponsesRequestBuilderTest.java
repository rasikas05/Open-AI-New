package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponsesRequestBuilderTest {

    @Test
    void buildResponsesBody_includesPreviousResponseIdWhenSet() {
        Map<String, Object> body = OpenAiResponsesRequestBuilder.buildResponsesBody(
                "gpt-5.6-terra",
                "none",
                4096,
                "You are M3 assistant.",
                "What is OIS300?",
                "resp_prev_1"
        );

        assertEquals("gpt-5.6-terra", body.get("model"));
        assertEquals("resp_prev_1", body.get("previous_response_id"));
        assertEquals(4096, body.get("max_output_tokens"));
        assertTrue(body.containsKey("reasoning"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> input = (List<Map<String, String>>) body.get("input");
        assertEquals(2, input.size());
        assertEquals("system", input.get(0).get("role"));
        assertEquals("user", input.get(1).get("role"));
    }

    @Test
    void buildResponsesBody_omitsPreviousResponseIdWhenBlank() {
        Map<String, Object> body = OpenAiResponsesRequestBuilder.buildResponsesBody(
                "gpt-4.1",
                "none",
                1024,
                "System",
                "Question",
                "  "
        );
        assertFalse(body.containsKey("previous_response_id"));
        assertFalse(body.containsKey("reasoning"));
    }
}
