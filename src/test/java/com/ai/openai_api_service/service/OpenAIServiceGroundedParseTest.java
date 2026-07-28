package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.rag.GroundedRagResult;
import com.ai.openai_api_service.model.rag.RagStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceGroundedParseTest {

    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(null, null, null);
    }

    @Test
    void parseGroundedRagResult_parsesFullJson() {
        GroundedRagResult result = openAIService.parseGroundedRagResult(
                "{\"status\":\"FULL\",\"answer\":\"Customer order workflow...\",\"missingTopics\":[]}"
        );
        assertEquals(RagStatus.FULL, result.getStatus());
        assertEquals("Customer order workflow...", result.getAnswer());
        assertTrue(result.getMissingTopics().isEmpty());
    }

    @Test
    void parseGroundedRagResult_stripsFenceAndParsesPartial() {
        GroundedRagResult result = openAIService.parseGroundedRagResult(
                """
                ```json
                {"status":"PARTIAL","answer":"MNS204 docs","missingTopics":["Functional purpose"]}
                ```
                """
        );
        assertEquals(RagStatus.PARTIAL, result.getStatus());
        assertEquals("MNS204 docs", result.getAnswer());
        assertEquals(List.of("Functional purpose"), result.getMissingTopics());
    }

    @Test
    void parseGroundedRagResult_rejectsInvalidStatus() {
        assertThrows(
                OpenAIException.class,
                () -> openAIService.parseGroundedRagResult("{\"status\":\"MAYBE\",\"answer\":\"x\",\"missingTopics\":[]}")
        );
    }
}
