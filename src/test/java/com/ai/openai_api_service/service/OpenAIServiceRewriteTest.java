package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAIServiceRewriteTest {

    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(null, null, null);
    }

    @Test
    void parseQueriesFromLlm_parsesJsonArray() {
        List<String> queries = openAIService.parseQueriesFromLlm(
                "[\"customer pricing configuration\", \"price list setup\"]"
        );
        assertEquals(List.of("customer pricing configuration", "price list setup"), queries);
    }

    @Test
    void parseQueriesFromLlm_stripsMarkdownFence() {
        List<String> queries = openAIService.parseQueriesFromLlm(
                "```json\n[\"OIS100 panel G\", \"customer order panel setup\"]\n```"
        );
        assertEquals(List.of("OIS100 panel G", "customer order panel setup"), queries);
    }

    @Test
    void parseQueriesFromLlm_rejectsEmptyArray() {
        assertThrows(OpenAIException.class, () -> openAIService.parseQueriesFromLlm("[]"));
    }
}
