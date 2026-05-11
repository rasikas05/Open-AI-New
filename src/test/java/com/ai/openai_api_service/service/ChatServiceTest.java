package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.TokenUsageDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private OpenAIService openAIService;

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
}