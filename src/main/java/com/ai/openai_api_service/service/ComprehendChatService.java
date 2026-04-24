package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;

@Service
public class ComprehendChatService {
    private static final Logger log = LoggerFactory.getLogger(ComprehendChatService.class);

    private final RestTemplate restTemplate;
    private final ComprehendAnonymizationService comprehendAnonymizationService;
    private final ChatPersistenceService chatPersistenceService;
    private final TenantQuotaService tenantQuotaService;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.model}")
    private String model;

    @Value("${openai.api.url}")
    private String openaiUrl;

    @Value("${openai.assistant.system-prompt.enabled:true}")
    private boolean systemPromptEnabled;

    @Value("${openai.assistant.system-prompt:}")
    private String systemPrompt;

    @Value("${openai.input.remove-anonymization-placeholders:true}")
    private boolean removeAnonymizationPlaceholders;

    @Value("${openai.response.include-sanitization-debug:false}")
    private boolean includeSanitizationDebug;

    @Value("${chat.history.load-from-db:true}")
    private boolean loadHistoryFromDb;

    @Value("${chat.history.max-exchanges:10}")
    private int maxHistoryExchanges;

    @Value("${chat.history.allow-client-history:false}")
    private boolean allowClientHistory;

    public ComprehendChatService(
            ComprehendAnonymizationService comprehendAnonymizationService,
            ChatPersistenceService chatPersistenceService,
            TenantQuotaService tenantQuotaService
    ) {
        this.restTemplate = new RestTemplate();
        this.comprehendAnonymizationService = comprehendAnonymizationService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
    }

    public ChatResponse chat(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAIException(
                    "OpenAI API key is missing. Set OPENAI_API_KEY env var or openai.api.key property.",
                    401
            );
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPromptEnabled && systemPrompt != null && !systemPrompt.isBlank()) {
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt.trim());
            messages.add(systemMessage);
        }

        // Choose history source:
        // 1) If client sends non-empty `history`, prefer it (so the model uses the context you pass in).
        // 2) Otherwise fall back to DB history (when enabled).
        List<MessageDto> clientHistory = request.getHistory() != null ? request.getHistory() : List.of();
        boolean hasClientHistory = allowClientHistory && !clientHistory.isEmpty();

        List<MessageDto> sourceHistory;
        if (hasClientHistory) {
            sourceHistory = clientHistory;
        } else if (loadHistoryFromDb) {
            sourceHistory = chatPersistenceService.loadHistoryForPrompt(
                    request.getTenantId(),
                    request.getUserId(),
                    request.getSessionId(),
                    maxHistoryExchanges
            );
        } else {
            sourceHistory = List.of();
        }

        // Safety cap to avoid sending very large histories into the model.
        if (sourceHistory != null && sourceHistory.size() > maxHistoryExchanges) {
            int fromIndex = Math.max(0, sourceHistory.size() - maxHistoryExchanges);
            sourceHistory = sourceHistory.subList(fromIndex, sourceHistory.size());
        }
        int historyCount = sourceHistory != null ? sourceHistory.size() : 0;

        if (sourceHistory != null) {
            for (MessageDto message : sourceHistory) {
                String role = message.getRole() == null ? null : message.getRole().trim().toLowerCase(Locale.ROOT);
                String content = message.getContent() == null ? null : message.getContent().trim();
                if (!isValidRole(role) || content == null || content.isBlank()) {
                    throw new OpenAIException(
                            "Invalid history item. role must be system/user/assistant and content must be non-empty.",
                            400
                    );
                }
                Map<String, String> map = new HashMap<>();
                map.put("role", role);
                map.put("content", "user".equals(role)
                        ? prepareUserContentForOpenAi(sanitizeTextWithComprehend(content))
                        : content);
                messages.add(map);
            }
        }

        String originalUserText = request.getUserMessage();
        String sanitizedUserText = sanitizeTextWithComprehend(originalUserText);
        String modelReadyUserText = prepareUserContentForOpenAi(sanitizedUserText);
        log.info(
                "Preparing OpenAI call with Comprehend sanitization. tenantId={}, userId={}, sessionId={}, historyCount={}, original='{}', modelReady='{}'",
                request.getTenantId(),
                request.getUserId(),
                request.getSessionId(),
                historyCount,
                originalUserText,
                modelReadyUserText
        );

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", modelReadyUserText);
        messages.add(userMessage);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, Object> response;
        try {
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    openaiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            response = responseEntity.getBody();
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            HttpHeaders h = e.getResponseHeaders();
            if (h != null) {
                log.warn("OpenAI x-ratelimit-limit-requests={}", h.getFirst("x-ratelimit-limit-requests"));
                log.warn("OpenAI x-ratelimit-remaining-requests={}", h.getFirst("x-ratelimit-remaining-requests"));
                log.warn("OpenAI x-ratelimit-reset-requests={}", h.getFirst("x-ratelimit-reset-requests"));
                log.warn("OpenAI x-ratelimit-limit-tokens={}", h.getFirst("x-ratelimit-limit-tokens"));
                log.warn("OpenAI x-ratelimit-remaining-tokens={}", h.getFirst("x-ratelimit-remaining-tokens"));
                log.warn("OpenAI x-ratelimit-reset-tokens={}", h.getFirst("x-ratelimit-reset-tokens"));
            } else {
                log.warn("OpenAI error response headers are empty.");
            }

            String errorBody = e.getResponseBodyAsString();
            log.warn("OpenAI error status={} body={}", e.getStatusCode(), errorBody);

            String msg = code == 401
                    ? "OpenAI API key is invalid or missing. Check openai.api.key in application.properties (no quotes)."
                    : "OpenAI API error: " + code + " " + e.getStatusText();
            throw new OpenAIException(msg, code);
        }

        if (response == null) {
            return new ChatResponse("No response from OpenAI.", true);
        }

        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            return new ChatResponse("No choices returned from OpenAI.", true);
        }

        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return new ChatResponse("Unexpected response format from OpenAI.", true);
        }

        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return new ChatResponse("Unexpected message format from OpenAI.", true);
        }

        Object contentObj = messageMap.get("content");
        String content = contentObj != null ? contentObj.toString() : "";

        boolean truncated = false;
        Object finishReason = choiceMap.get("finish_reason");
        if (finishReason != null && "length".equals(finishReason.toString())) {
            truncated = true;
        }

        Integer totalTokens = extractTotalTokens(response);
        int consumedTokens = totalTokens != null ? totalTokens : 0;
        String usageReferenceId = request.getSessionId() + ":" + System.currentTimeMillis();
        try {
            tenantQuotaService.recordUsage(request.getTenantId(), consumedTokens, usageReferenceId);
        } catch (TenantQuotaExceededException e) {
            ChatResponse blocked = new ChatResponse("Token limit reached for this tenant. Please top up to continue.", false);
            blocked.setLimitExceeded(true);
            blocked.setUsage(e.getUsage());
            blocked.setBlockReason("LIMIT_EXCEEDED");
            blocked.setUpgradeOptions(Arrays.asList("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
            return blocked;
        }
        chatPersistenceService.persistChat(
                request.getTenantId(),
                request.getUserId(),
                request.getSessionId(),
                originalUserText,
                modelReadyUserText,
                content,
                consumedTokens
        );

        ChatResponse chatResponse = new ChatResponse(content, false);
        chatResponse.setSanitizationApplied(!Objects.equals(originalUserText, modelReadyUserText));
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(modelReadyUserText);
        }
        return chatResponse;
    }

    /**
     * Sanitize text using Comprehend detection + Presidio anonymization
     */
    private String sanitizeTextWithComprehend(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        try {
            log.info("Sanitizing text with Comprehend-based anonymization");
            Map<String, Object> result = comprehendAnonymizationService.detectAndAnonymize(text);
            Object sanitizedText = result.get("sanitizedText");
            return sanitizedText != null ? sanitizedText.toString() : text;
        } catch (Exception e) {
            log.warn("Comprehend anonymization failed, using original text: {}", e.getMessage());
            return text;
        }
    }

    private String prepareUserContentForOpenAi(String sanitizedText) {
        if (sanitizedText == null) {
            return null;
        }
        if (!removeAnonymizationPlaceholders) {
            return sanitizedText;
        }
        String withoutTags = sanitizedText
                .replaceAll("<[A-Z_]+>", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return withoutTags.isBlank() ? sanitizedText : withoutTags;
    }

    private boolean isValidRole(String role) {
        return "system".equals(role) || "user".equals(role) || "assistant".equals(role);
    }

    private Integer extractTotalTokens(Map<String, Object> response) {
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Object totalTokens = usageMap.get("total_tokens");
        if (totalTokens instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
