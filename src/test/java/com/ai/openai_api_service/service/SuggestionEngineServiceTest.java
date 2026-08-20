package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SuggestionCategory;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionItem;
import com.ai.openai_api_service.model.SuggestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionEngineServiceTest {

    @Mock
    private SuggestionRuleService suggestionRuleService;

    @Mock
    private SuggestionLLMService suggestionLLMService;

    @Mock
    private SuggestionCacheService suggestionCacheService;

    private SuggestionEngineService suggestionEngineService;

    @BeforeEach
    void setUp() {
        suggestionEngineService = new SuggestionEngineService(suggestionRuleService, suggestionLLMService, suggestionCacheService);
        ReflectionTestUtils.setField(suggestionEngineService, "ruleEnabled", true);
        ReflectionTestUtils.setField(suggestionEngineService, "llmEnabled", true);
        ReflectionTestUtils.setField(suggestionEngineService, "minSuggestionCount", 2);
        ReflectionTestUtils.setField(suggestionEngineService, "maxSuggestionCount", 2);
        ReflectionTestUtils.setField(suggestionEngineService, "openaiModel", "gpt-test");
    }

    @Test
    void shouldReturnBlankSuggestionsForNonM3Query() {
        when(suggestionRuleService.isSupportedM3Topic("what is football")).thenReturn(false);

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("what is football");
        context.setAnswer("This question is unrelated to M3.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 2, 2);

        assertTrue(result.getSuggestions().isEmpty());
        verify(suggestionLLMService, never()).suggestWithUsage(any(), anyInt(), anyInt());
    }

    @Test
    void usesExactlyTwoLlmSuggestionsWithoutMergingRules() {
        when(suggestionRuleService.isSupportedM3Topic("what is OIS100")).thenReturn(true);
        when(suggestionCacheService.get(anyString())).thenReturn(List.of());
        when(suggestionLLMService.suggestWithUsage(any(), anyInt(), anyInt())).thenReturn(
                new SuggestionLLMService.SuggestionLlmOutcome(List.of(
                        new SuggestionItem("What does AHS112 configure?", SuggestionCategory.FOLLOW_UP, 0.9d, "LLM"),
                        new SuggestionItem("How is CMS100 used?", SuggestionCategory.FOLLOW_UP, 0.9d, "LLM"),
                        new SuggestionItem("Show virtual fields in AHS110", SuggestionCategory.FOLLOW_UP, 0.9d, "LLM")
                ), 80, 20)
        );

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("what is OIS100");
        context.setAnswer("OIS100 is Customer Order.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 2, 2);

        assertEquals(2, result.getSuggestions().size());
        assertEquals("What does AHS112 configure?", result.getSuggestions().get(0));
        assertFalse(result.getSuggestions().get(0).startsWith("How can I"));
        assertEquals(100, result.getTotalTokens());
        verify(suggestionLLMService).suggestWithUsage(any(), anyInt(), anyInt());
        verify(suggestionRuleService, never()).genericSuggestions(anyInt());
    }

    @Test
    void returnsEmptyWhenLlmReturnsEmpty() {
        when(suggestionRuleService.isSupportedM3Topic("want to know about ad hoc report")).thenReturn(true);
        when(suggestionCacheService.get(anyString())).thenReturn(List.of());
        when(suggestionLLMService.suggestWithUsage(any(), anyInt(), anyInt()))
                .thenReturn(new SuggestionLLMService.SuggestionLlmOutcome(List.of(), 12, 0));

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("want to know about ad hoc report");
        context.setAnswer("The report uses AHS110, AHS112 and CMS100 and supports Virtual Fields.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 2, 2);

        assertTrue(result.getSuggestions().isEmpty());
        assertEquals(12, result.getTotalTokens());
        verify(suggestionLLMService).suggestWithUsage(any(), anyInt(), anyInt());
        verify(suggestionRuleService, never()).genericSuggestions(anyInt());
    }
}
