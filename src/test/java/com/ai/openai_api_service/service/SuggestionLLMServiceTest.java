package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionLLMServiceTest {

    private SuggestionLLMService service;

    @BeforeEach
    void setUp() {
        service = new SuggestionLLMService(5000);
        ReflectionTestUtils.setField(service, "suggestionMaxCompletionTokens", 80);
    }

    @Test
    void prompt_asksForExactlyTwoJsonStrings() {
        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("What is OIS100?");
        context.setAnswer("OIS100 is Customer Order.");

        String system = service.suggestionSystemPrompt();
        String user = service.buildUserPrompt(context);

        assertTrue(system.contains("exactly 2"));
        assertTrue(user.contains("exactly 2"));
        assertTrue(user.contains("JSON array of 2 strings"));
        assertTrue(user.contains("User request: What is OIS100?"));
        assertTrue(user.contains("Assistant answer: OIS100 is Customer Order."));
        assertEquals(80, service.suggestionCompletionTokenCap());
    }

    @Test
    void parseSuggestionContent_readsTwoStringsAndCaps() {
        List<SuggestionItem> items = service.parseSuggestionContent(
                "[\"How to create a customer order\",\"Which APIs relate to OIS100\"]",
                2
        );
        assertEquals(2, items.size());
        assertEquals("How to create a customer order", items.get(0).getText());
        assertEquals("Which APIs relate to OIS100", items.get(1).getText());
    }

    @Test
    void parseSuggestionContent_ignoresExtraStringsBeyondCap() {
        List<SuggestionItem> items = service.parseSuggestionContent(
                "[\"How to create a customer order\",\"Which APIs relate to OIS100\",\"How to print packing slips\"]",
                2
        );
        assertEquals(2, items.size());
    }
}
