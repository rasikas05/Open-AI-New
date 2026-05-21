package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.service.PresidioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private OpenAIService openAIService;

    @Mock
    private PresidioService presidioService;

    @Mock
    private SuggestionRuleService suggestionRuleService;

    @Mock
    private SuggestionLLMService suggestionLLMService;

    @Mock
    private SuggestionCacheService suggestionCacheService;

    @Mock
    private TenantQuotaService tenantQuotaService;

    @InjectMocks
    private ChatService chatService;

    @Test
    void chatShouldReturnBlockedResponseWhenQuotaExceeded() {

        ChatRequest request = new ChatRequest();
        request.setTenantCode("tenant-1");
        request.setUserId("user-1");
        request.setSessionId("session-1");
        request.setUserMessage("hello");

        TokenUsageDto usage = new TokenUsageDto(1000, 1000, 0);

        when(tenantQuotaService.checkBeforeChat("tenant-1"))
                .thenReturn(
                        new TenantQuotaService.QuotaCheckResult(
                                false,
                                usage,
                                "LIMIT_EXCEEDED"
                        )
                );

        ChatResponse response = chatService.chat(request);

        assertTrue(Boolean.TRUE.equals(response.getLimitExceeded()));
        assertEquals(usage.getUsed(), response.getUsage().getUsed());

        verify(openAIService, never()).chat(request);
    }

    @Test
    void chatShouldCallOpenAiWhenQuotaAvailable() {

        ChatRequest request = new ChatRequest();
        request.setTenantCode("tenant-2");
        request.setUserId("user-1");
        request.setSessionId("session-2");
        request.setUserMessage("hello");

        when(tenantQuotaService.checkBeforeChat("tenant-2"))
                .thenReturn(
                        new TenantQuotaService.QuotaCheckResult(
                                true,
                                new TokenUsageDto(10, 1000, 990),
                                null
                        )
                );

        when(openAIService.chat(request))
                .thenReturn(new ChatResponse("ok", false));

        ChatResponse response = chatService.chat(request);

        assertTrue(Boolean.FALSE.equals(response.getLimitExceeded()));

        verify(openAIService).chat(request);
    }

    @Test
    void chatShouldReturnGenericSuggestionsForUnsupportedTopic() {
        ChatRequest request = new ChatRequest();
        request.setTenantCode("tenant-2");
        request.setUserId("user-1");
        request.setSessionId("session-2");
        request.setUserMessage("Want to know about football");

        when(tenantQuotaService.checkBeforeChat("tenant-2"))
                .thenReturn(
                        new TenantQuotaService.QuotaCheckResult(
                                true,
                                new TokenUsageDto(10, 1000, 990),
                                null
                        )
                );

        when(openAIService.chat(request))
                .thenReturn(new ChatResponse("I'm here to help with questions related to Infor M3 ERP. If you have any queries about Infor M3 modules, processes, or troubleshooting, please let me know!", false));

        when(suggestionRuleService.genericSuggestions(anyInt()))
                .thenReturn(List.of(
                        "Infor M3 order management",
                        "M3 inventory management",
                        "Customer order workflow in M3",
                        "Infor M3 API integration",
                        "M3 invoicing process"
                ));

        ChatResponse response = chatService.chat(request);

        assertFalse(Boolean.TRUE.equals(response.getLimitExceeded()));
        assertFalse(response.getSuggestions().isEmpty());
        assertEquals("Infor M3 order management", response.getSuggestions().get(0));

        verify(openAIService).chat(request);
        verify(suggestionLLMService, never()).suggest(any(), anyInt(), anyInt());
        verify(suggestionRuleService, never()).suggest(any(), anyInt());
    }
}
