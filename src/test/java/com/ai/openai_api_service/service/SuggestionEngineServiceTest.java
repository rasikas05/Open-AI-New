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
        verify(suggestionLLMService, never()).suggest(any(), anyInt(), anyInt());
    }

    @Test
    void usesExactlyTwoLlmSuggestionsWithoutMergingRules() {
        when(suggestionRuleService.isSupportedM3Topic("what is OIS100")).thenReturn(true);
        when(suggestionCacheService.get(anyString())).thenReturn(List.of());
        when(suggestionLLMService.suggest(any(), anyInt(), anyInt())).thenReturn(List.of(
                new SuggestionItem("How to create a customer order", SuggestionCategory.FOLLOW_UP, 0.9d, "LLM"),
                new SuggestionItem("Which APIs relate to OIS100", SuggestionCategory.FOLLOW_UP, 0.9d, "LLM"),
                new SuggestionItem("How to print packing slips now", SuggestionCategory.FOLLOW_UP, 0.9d, "LLM")
        ));

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("what is OIS100");
        context.setAnswer("OIS100 is Customer Order.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 2, 2);

        assertEquals(2, result.getSuggestions().size());
        assertTrue(result.getSuggestions().get(0).contains("customer order"));
        assertTrue(result.getSuggestions().stream().anyMatch(text -> text.contains("OIS100") || text.contains("APIs")));
        verify(suggestionLLMService).suggest(any(), anyInt(), anyInt());
        verify(suggestionRuleService, never()).genericSuggestions(anyInt());
    }

    @Test
    void fallsBackToTopicWhenLlmReturnsEmpty() {
        when(suggestionRuleService.isSupportedM3Topic("want to know about ad hoc report")).thenReturn(true);
        when(suggestionCacheService.get(anyString())).thenReturn(List.of());
        when(suggestionLLMService.suggest(any(), anyInt(), anyInt())).thenReturn(List.of());

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("want to know about ad hoc report");
        context.setAnswer("The report uses AHS110, AHS112 and CMS100 and supports Virtual Fields.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 2, 2);

        assertEquals(2, result.getSuggestions().size());
        verify(suggestionLLMService).suggest(any(), anyInt(), anyInt());
    }
}
