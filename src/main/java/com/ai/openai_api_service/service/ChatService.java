package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SuggestionDto;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final OpenAIService openAIService;
    private final PresidioService presidioService;
    private final SuggestionEngineService suggestionEngineService;
    private final TenantQuotaService tenantQuotaService;

    public ChatService(
            OpenAIService openAIService,
            PresidioService presidioService,
            SuggestionEngineService suggestionEngineService,
            TenantQuotaService tenantQuotaService
    ) {
        this.openAIService = openAIService;
        this.presidioService = presidioService;
        this.suggestionEngineService = suggestionEngineService;
        this.tenantQuotaService = tenantQuotaService;
    }

    public ChatResponse chat(ChatRequest request) {
        TenantQuotaService.QuotaCheckResult quotaCheck = tenantQuotaService.checkBeforeChat(request.getTenantCode());
        if (!quotaCheck.allowed()) {
            String reason = quotaCheck.reason();
            String reply = "Token limit reached for this tenant. Please top up to continue.";
            if ("QUOTA_NOT_CONFIGURED".equals(reason)) {
                reply = "Quota is not configured for this tenant. Please contact admin.";
            } else if ("TENANT_BLOCKED".equals(reason)) {
                reply = "This tenant is blocked. Please contact admin.";
            }
            ChatResponse blocked = new ChatResponse(reply, false);
            blocked.setLimitExceeded(true);
            blocked.setUsage(quotaCheck.usage());
            blocked.setBlockReason(reason);
            if ("QUOTA_NOT_CONFIGURED".equals(reason)) {
                blocked.setUpgradeOptions(List.of("Contact admin to assign quota"));
            } else {
                blocked.setUpgradeOptions(List.of("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
            }
            return blocked;
        }

        ChatResponse chatResponse = openAIService.chat(request);
        if (Boolean.TRUE.equals(chatResponse.getLimitExceeded())) {
            return chatResponse;
        }
        chatResponse.setLimitExceeded(false);

        ChatRequest sanitizedRequest = sanitizeRequestForSuggestions(request);
        SuggestionContext context = buildSuggestionContext(sanitizedRequest, chatResponse.getReply());
        SuggestionResult suggestionResult = suggestionEngineService.generateSuggestions(context);
        chatResponse.setSuggestions(suggestionResult.getSuggestions());
        chatResponse.setSuggestionDetails(suggestionResult.getDetails());
        return chatResponse;
    }

    private SuggestionContext buildSuggestionContext(ChatRequest request, String answer) {
        SuggestionContext context = new SuggestionContext();
        context.setTenantCode(request.getTenantCode());
        context.setUserId(request.getUserId());
        context.setSessionId(request.getSessionId());
        context.setUserMessage(request.getUserMessage());
        context.setHistory(request.getHistory());
        context.setAnswer(answer);
        return context;
    }

    private ChatRequest sanitizeRequestForSuggestions(ChatRequest request) {
        if (request == null) {
            return null;
        }
        String sanitizedUserMessage = safeSanitizeText(request.getUserMessage());
        List<MessageDto> history = request.getHistory();
        List<MessageDto> sanitizedHistory = null;
        if (history != null) {
            sanitizedHistory = new ArrayList<>();
            for (MessageDto message : history) {
                if (message == null) {
                    continue;
                }
                String role = message.getRole();
                String content = message.getContent();
                String sanitizedContent = "user".equalsIgnoreCase(role) ? safeSanitizeText(content) : content;
                sanitizedHistory.add(new MessageDto(role, sanitizedContent));
            }
        }
        ChatRequest copy = new ChatRequest();
        copy.setTenantCode(request.getTenantCode());
        copy.setUserId(request.getUserId());
        copy.setSessionId(request.getSessionId());
        copy.setUserMessage(sanitizedUserMessage);
        copy.setHistory(sanitizedHistory);
        return copy;
    }

    private String safeSanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            String sanitized = presidioService.sanitizeText(text);
            return sanitized == null || sanitized.isBlank() ? text : sanitized;
        } catch (Exception e) {
            log.warn("Suggestion sanitization failed, using original text: {}", e.getMessage());
            return text;
        }
    }
}
