package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionLLMServiceTest {

    private SuggestionLLMService service;

    @BeforeEach
    void setUp() {
        service = new SuggestionLLMService(5000);
        ReflectionTestUtils.setField(service, "suggestionMaxCompletionTokens", 80);
    }

    @Test
    void prompt_asksForSendableNextUserRequests() {
        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("What is OIS100?");
        context.setAnswer("OIS100 is Customer Order.");

        String system = service.suggestionSystemPrompt();
        String user = service.buildUserPrompt(context);

        assertTrue(system.contains("exactly 2"));
        assertTrue(system.contains("sendable"));
        assertTrue(user.contains("exactly 2 next user requests"));
        assertTrue(user.contains("hard max 10"));
        assertTrue(user.contains("JSON array of 2 strings"));
        assertTrue(user.contains("User request: What is OIS100?"));
        assertTrue(user.contains("Assistant answer: OIS100 is Customer Order."));
        assertEquals(80, service.suggestionCompletionTokenCap());
    }

    @Test
    void prompt_truncatesLongAssistantAnswer() {
        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("want details of adhoc reporting");
        context.setAnswer("x".repeat(500));

        String user = service.buildUserPrompt(context);
        assertTrue(user.contains("Assistant answer: " + "x".repeat(400)));
        assertFalse(user.contains("x".repeat(401)));
    }

    @Test
    void parseSuggestionContent_keepsSendableChip() {
        List<SuggestionItem> items = service.parseSuggestionContent(
                "[\"What does AHS112 configure?\",\"How is CMS100 used?\"]",
                2
        );
        assertEquals(2, items.size());
        assertEquals("What does AHS112 configure?", items.get(0).getText());
        assertEquals("How is CMS100 used?", items.get(1).getText());
    }

    @Test
    void parseSuggestionContent_rejectsAssistantVoiceAndGluedPrefix() {
        List<SuggestionItem> items = service.parseSuggestionContent(
                "[\"Do you need help creating or running reports\",\"How can I Which M3 version is used\"]",
                2
        );
        assertTrue(items.isEmpty());
    }

    @Test
    void parseSuggestionContent_ignoresExtraStringsBeyondCap() {
        List<SuggestionItem> items = service.parseSuggestionContent(
                "[\"What does AHS112 configure?\",\"How is CMS100 used?\",\"Show virtual fields in AHS110\"]",
                2
        );
        assertEquals(2, items.size());
    }
}
