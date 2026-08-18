package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void rewritePrompts_areConciseAndExampleFree() {
        String system = openAIService.rewriteSystemPrompt();
        String userTemplate = openAIService.rewriteUserPromptTemplate();

        assertFalse(system.contains("CLEAR"));
        assertTrue(system.contains("Never answer the user"));
        assertTrue(system.contains("JSON array"));

        assertTrue(userTemplate.contains("CLEAR:"));
        assertTrue(userTemplate.contains("%s"));
        assertTrue(userTemplate.contains("1-3"));
        assertTrue(userTemplate.contains("Never invent identifiers"));

        assertFalse(userTemplate.contains("OIS101"));
        assertFalse(userTemplate.contains("PPS095"));
        assertFalse(userTemplate.contains("Input:"));
        assertFalse(userTemplate.contains("Output:"));
    }
}
