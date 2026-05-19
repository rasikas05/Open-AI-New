package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SuggestionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String GENERIC_MESSAGE = "I'm here to help with questions related to Infor M3 ERP. If you have any queries about Infor M3 modules, processes, or troubleshooting, please let me know!";

    private final OpenAIService openAIService;
    private final PresidioService presidioService;
    private final SuggestionRuleService suggestionRuleService;
    private final SuggestionLLMService suggestionLLMService;
    private final SuggestionCacheService suggestionCacheService;
    private final TenantQuotaService tenantQuotaService;

    @Value("${suggestion.min-count:3}")
    private int minSuggestionCount;

    @Value("${suggestion.max-count:5}")
    private int maxSuggestionCount;

    @Value("${suggestion.rule.enabled:true}")
    private boolean ruleEnabled;

    @Value("${suggestion.llm.enabled:true}")
    private boolean llmEnabled;

    public ChatService(
            OpenAIService openAIService,
            PresidioService presidioService,
            SuggestionRuleService suggestionRuleService,
            SuggestionLLMService suggestionLLMService,
            SuggestionCacheService suggestionCacheService,
            TenantQuotaService tenantQuotaService
    ) {
        this.openAIService = openAIService;
        this.presidioService = presidioService;
        this.suggestionRuleService = suggestionRuleService;
        this.suggestionLLMService = suggestionLLMService;
        this.suggestionCacheService = suggestionCacheService;
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
                blocked.setUpgradeOptions(Arrays.asList("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
            }
            return blocked;
        }

        ChatResponse chatResponse = openAIService.chat(request);
        if (Boolean.TRUE.equals(chatResponse.getLimitExceeded())) {
            return chatResponse;
        }
        chatResponse.setLimitExceeded(false);

        ChatRequest suggestionRequest = sanitizeRequestForSuggestions(request);
        SuggestionResult suggestionResult = buildSuggestions(suggestionRequest);
        chatResponse.setSuggestions(suggestionResult.suggestions());
        chatResponse.setSuggestionDetails(suggestionResult.details());
        return chatResponse;
    }

    private SuggestionResult buildSuggestions(ChatRequest request) {
        int maxCount = Math.min(3, Math.max(1, maxSuggestionCount));

        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            return new SuggestionResult(List.of(), List.of());
        }

        List<String> suggestions = new ArrayList<>();
        List<SuggestionDto> details = new ArrayList<>();
        boolean ruleMatch = ruleEnabled && suggestionRuleService.isSupportedM3Topic(request.getUserMessage());
        List<String> ruleSuggestions = ruleMatch
                ? suggestionRuleService.suggest(request.getUserMessage(), maxCount)
                : List.of();

        if (ruleMatch && !ruleSuggestions.isEmpty()) {
            String firstRule = normalizeSuggestionText(ruleSuggestions.get(0));
            if (firstRule != null && !firstRule.isBlank()) {
                suggestions.add(firstRule);
                details.add(new SuggestionDto(firstRule, "RULE"));
            }

            int llmRequired = maxCount - suggestions.size();
            if (llmRequired > 0 && llmEnabled) {
                String cacheKey = buildCacheKey(request);
                List<String> cached = suggestionCacheService.get(cacheKey);
                List<String> llmSuggestions = cached.isEmpty()
                        ? suggestionLLMService.suggest(request, llmRequired, llmRequired)
                        : cached;

                if (cached.isEmpty() && !llmSuggestions.isEmpty()) {
                    suggestionCacheService.put(cacheKey, llmSuggestions);
                }

                for (String suggestion : llmSuggestions) {
                    if (suggestions.size() >= maxCount) {
                        break;
                    }
                    String clean = normalizeSuggestionText(suggestion);
                    if (clean == null || clean.isBlank() || suggestions.contains(clean)) {
                        continue;
                    }
                    suggestions.add(clean);
                    details.add(new SuggestionDto(clean, "LLM"));
                }
            }

            for (int i = 1; i < ruleSuggestions.size() && suggestions.size() < maxCount; i++) {
                String clean = normalizeSuggestionText(ruleSuggestions.get(i));
                if (clean == null || clean.isBlank() || suggestions.contains(clean)) {
                    continue;
                }
                suggestions.add(clean);
                details.add(new SuggestionDto(clean, "RULE"));
            }
        } else {
            if (llmEnabled) {
                List<String> llmSuggestions = suggestionLLMService.suggest(request, maxCount, maxCount);
                for (String suggestion : llmSuggestions) {
                    if (suggestions.size() >= maxCount) {
                        break;
                    }
                    String clean = normalizeSuggestionText(suggestion);
                    if (clean == null || clean.isBlank() || suggestions.contains(clean)) {
                        continue;
                    }
                    suggestions.add(clean);
                    details.add(new SuggestionDto(clean, "LLM"));
                }
            }
        }

        if (suggestions.isEmpty()) {
            log.info("No suggestions generated for tenantCode={}, userId={}, sessionId={}",
                    request.getTenantCode(), request.getUserId(), request.getSessionId());
        }
        return new SuggestionResult(suggestions, details);
    }

    private String buildCacheKey(ChatRequest request) {
        String tenant = normalizeToken(request.getTenantCode());
        String user = normalizeToken(request.getUserId());
        String session = normalizeToken(request.getSessionId());
        String msg = normalizeToken(request.getUserMessage());
        return tenant + "|" + user + "|" + session + "|" + msg;
    }

    private ChatRequest sanitizeRequestForSuggestions(ChatRequest request) {
        if (request == null) {
            return request;
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
                String sanitizedContent = "user".equalsIgnoreCase(role)
                        ? safeSanitizeText(content)
                        : content;
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

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private List<SuggestionDto> cleanSuggestionDetails(Map<String, String> sourceWithType, int maxCount) {
        List<SuggestionDto> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : sourceWithType.entrySet()) {
            String suggestion = entry.getKey();
            String sourceType = entry.getValue();
            String clean = normalizeSuggestionText(suggestion);
            if (clean == null || clean.isBlank()) {
                continue;
            }
            boolean alreadyPresent = false;
            for (SuggestionDto item : result) {
                if (clean.equals(item.getText())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                result.add(new SuggestionDto(clean, sourceType));
            }
            if (result.size() >= maxCount) {
                break;
            }
        }
        return result;
    }

    private String normalizeSuggestionText(String suggestion) {
        if (suggestion == null) {
            return null;
        }
        String clean = suggestion.trim().replaceAll("\\s{2,}", " ");
        clean = clean.replaceAll("(?i)^(view|check|track|see|open|go to|search|find)\\s+", "");
        clean = clean.replaceAll("(?i)^(how to|how does|how do i|describe|explain|show|review|validate|fix|set up|configure|analyze|troubleshoot)\\s+", "");
        clean = clean.replaceAll("[?.!]+$", "");
        clean = clean.replaceAll("(?i)\\b(inquiry|processing|management)\\b", "");
        clean = clean.trim();
        if (clean.length() > 80) {
            clean = clean.substring(0, 80).trim();
        }
        return clean.replaceAll("\\s{2,}", " ");
    }

    private record SuggestionResult(List<String> suggestions, List<SuggestionDto> details) {
    }
}
