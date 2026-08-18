package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.MessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceHistoryTest {

    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(null, null, null);
        ReflectionTestUtils.setField(openAIService, "allowClientHistory", true);
        ReflectionTestUtils.setField(openAIService, "loadHistoryFromDb", false);
        ReflectionTestUtils.setField(openAIService, "maxUserQuestions", 5);
        ReflectionTestUtils.setField(openAIService, "maxHistoryExchanges", 10);
        ReflectionTestUtils.setField(openAIService, "removeAnonymizationPlaceholders", false);
    }

    @Test
    void toOpenAiUserHistory_dropsAssistantAndCapsAtFive() {
        List<MessageDto> mixed = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            mixed.add(new MessageDto("user", "q" + i));
            mixed.add(new MessageDto("assistant", "a" + i));
        }

        List<MessageDto> result = openAIService.toOpenAiUserHistory(mixed, "current");

        assertEquals(5, result.size());
        assertEquals(List.of("q3", "q4", "q5", "q6", "q7"), result.stream().map(MessageDto::getContent).toList());
        assertTrue(result.stream().allMatch(m -> "user".equals(m.getRole())));
    }

    @Test
    void toOpenAiUserHistory_dropsOnlyTrailingCurrentQuestion() {
        List<MessageDto> mixed = List.of(
                new MessageDto("user", "same"),
                new MessageDto("assistant", "a1"),
                new MessageDto("user", "other"),
                new MessageDto("assistant", "a2"),
                new MessageDto("user", "same")
        );

        List<MessageDto> result = openAIService.toOpenAiUserHistory(mixed, "same");

        assertEquals(List.of("same", "other"), result.stream().map(MessageDto::getContent).toList());
    }

    @Test
    void resolveHistory_usesClientHistoryUserOnly() {
        ChatRequest request = new ChatRequest();
        request.setUserMessage("now");
        request.setHistory(List.of(
                new MessageDto("user", "prev"),
                new MessageDto("assistant", "should not appear"),
                new MessageDto("user", "now")
        ));

        List<MessageDto> result = openAIService.resolveHistory(request);

        assertEquals(List.of("prev"), result.stream().map(MessageDto::getContent).toList());
    }

    @Test
    void buildRewriteUserContent_prefixesPreviousQuestionsWithoutChangingTemplate() {
        String template = openAIService.rewriteUserPromptTemplate();
        String formatted = template.formatted("How do I configure that?");
        List<MessageDto> history = List.of(
                new MessageDto("user", "How do I create a customer order?"),
                new MessageDto("user", "What about allocation?")
        );

        String content = openAIService.buildRewriteUserContent("How do I configure that?", history);

        assertTrue(content.startsWith("PREVIOUS USER QUESTIONS:\n"));
        assertTrue(content.contains("- How do I create a customer order?\n"));
        assertTrue(content.contains("- What about allocation?\n"));
        assertTrue(content.contains("CURRENT QUESTION:\n"));
        assertTrue(content.endsWith(formatted) || content.contains(formatted));
        assertFalse(content.contains("should not appear"));
        assertFalse(content.contains("assistant"));
        assertEquals(template, openAIService.rewriteUserPromptTemplate());
    }

    @Test
    void buildRewriteUserContent_withoutHistory_isUnchangedTemplate() {
        String expected = openAIService.rewriteUserPromptTemplate().formatted("pricing issue");
        assertEquals(expected, openAIService.buildRewriteUserContent("pricing issue", List.of()));
    }

    @Test
    void buildMessages_historyIsUserOnlyThenCurrentPayload() {
        ChatRequest request = new ChatRequest();
        request.setUserMessage("now");
        request.setHistory(List.of(
                new MessageDto("user", "prev"),
                new MessageDto("assistant", "old answer")
        ));
        String ragPayload = openAIService.buildRagUserPrompt("docs", "now");

        List<Map<String, String>> messages = openAIService.buildMessages(
                request,
                "system",
                ragPayload,
                true
        );

        assertEquals("system", messages.get(0).get("role"));
        assertEquals("user", messages.get(1).get("role"));
        assertEquals("prev", messages.get(1).get("content"));
        assertEquals("user", messages.get(2).get("role"));
        assertEquals(ragPayload, messages.get(2).get("content"));
        assertEquals(3, messages.size());
        assertFalse(messages.stream().anyMatch(m -> "old answer".equals(m.get("content"))));
        assertFalse(messages.stream().anyMatch(m -> "assistant".equals(m.get("role"))));
    }
}
