package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
        ReflectionTestUtils.setField(suggestionEngineService, "minSuggestionCount", 3);
        ReflectionTestUtils.setField(suggestionEngineService, "maxSuggestionCount", 5);
    }

    @Test
    void shouldReturnBlankSuggestionsForNonM3Query() {
        when(suggestionRuleService.isSupportedM3Topic("what is football")).thenReturn(false);

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("what is football");
        context.setAnswer("This question is unrelated to M3.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 3, 5);

        assertTrue(result.getSuggestions().isEmpty());
    }

    @Test
    void shouldGenerateTopicAwareSuggestionsFromReply() {
        when(suggestionRuleService.isSupportedM3Topic("want to know about ad hoc report")).thenReturn(true);
        when(suggestionCacheService.get(anyString())).thenReturn(List.of());
        when(suggestionLLMService.suggest(any(), anyInt(), anyInt())).thenReturn(List.of());

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("want to know about ad hoc report");
        context.setAnswer("The report uses AHS110, AHS112 and CMS100 and supports Virtual Fields.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 3, 5);

        assertFalse(result.getSuggestions().isEmpty());
        assertEquals(5, result.getSuggestions().size());
        assertTrue(result.getSuggestions().stream().anyMatch(text -> text.contains("Ad Hoc report") || text.contains("Ad Hoc Report")));
        assertTrue(result.getSuggestions().stream().anyMatch(text -> text.contains("AHS110") || text.contains("AHS112") || text.contains("CMS100") || text.contains("virtual fields") || text.contains("Virtual fields")));
    }

    @Test
    void shouldGenerateTopicAwareSuggestionsWhenReplyContainsM3TopicsEvenIfQueryRuleUnsupported() {
        when(suggestionRuleService.isSupportedM3Topic("want to know about ad hoc report")).thenReturn(false);
        when(suggestionCacheService.get(anyString())).thenReturn(List.of());
        when(suggestionLLMService.suggest(any(), anyInt(), anyInt())).thenReturn(List.of());

        SuggestionContext context = new SuggestionContext();
        context.setUserMessage("want to know about ad hoc report");
        context.setAnswer("An Ad Hoc Report in the M3 ERP system uses AHS110 and AHS112, and it can be configured via CMS100.");

        SuggestionResult result = suggestionEngineService.generateSuggestions(context, 3, 5);

        assertFalse(result.getSuggestions().isEmpty());
        assertEquals(5, result.getSuggestions().size());
        assertTrue(result.getSuggestions().stream().anyMatch(text -> text.contains("Ad Hoc report") || text.contains("AHS110") || text.contains("AHS112") || text.contains("CMS100") || text.contains("Virtual Fields") || text.contains("Metadata") || text.contains("users and roles")));
    }
}
