package com.ai.openai_api_service.service;

import com.ai.openai_api_service.config.RestTemplateFactory;
import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;

@Service
public class OpenAIService {
    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private static final String RAG_SYSTEM_PROMPT = """
            You are an Infor M3 expert assistant. Your role is to provide accurate, helpful answers about M3 ERP system based ONLY on the provided documentation context.

            STRICT RULES:
            1. Use ONLY the provided context to answer questions. Do not use prior knowledge about M3.
            2. If the answer is not present in the context, respond with: "This information is not available in the current documentation. Please refer to the official Infor M3 documentation or contact your M3 administrator."
            3. When referencing M3 programs, always include the program ID (e.g., CRS610, OIS100).
            4. Provide step-by-step instructions when the context includes procedural information.
            5. Cite the source document when possible.
            6. Be concise but thorough. Do not add information beyond what the context provides.""";

    private RestTemplate restTemplate;
    private final PresidioService presidioService;
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

    @Value("${openai.api.timeout-ms:120000}")
    private int openAiTimeoutMs;

    public OpenAIService(
            PresidioService presidioService,
            ChatPersistenceService chatPersistenceService,
            TenantQuotaService tenantQuotaService
    ) {
        this.presidioService = presidioService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
    }

    @PostConstruct
    void initRestTemplate() {
        this.restTemplate = RestTemplateFactory.create(openAiTimeoutMs);
    }

    /**
     * Direct OpenAI chat with persistence and quota (used by /api/chat).
     */
    public ChatResponse chat(ChatRequest request) {
        validateApiKey();
        String sanitizedUserText = presidioService.sanitizeText(request.getUserMessage());
        String modelReadyUserText = prepareUserContentForOpenAi(sanitizedUserText);
        List<Map<String, String>> messages = buildMessages(request, systemPromptForFallback(), modelReadyUserText);
        OpenAiCallResult result = callOpenAi(messages);

        int consumedTokens = result.usage().getTotalTokens() != null ? result.usage().getTotalTokens() : 0;
        String usageReferenceId = request.getSessionId() + ":" + System.currentTimeMillis();
        try {
            tenantQuotaService.recordUsage(request.getTenantCode(), consumedTokens, usageReferenceId);
        } catch (TenantQuotaExceededException e) {
            return blockedResponse(e);
        }

        boolean sanitizedFlag = !Objects.equals(request.getUserMessage(), modelReadyUserText);
        chatPersistenceService.persistChat(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                request.getUserMessage(),
                modelReadyUserText,
                result.content(),
                result.usage(),
                "gpt_infor",
                sanitizedFlag,
                null,
                null
        );

        return toChatResponse(request, result, "gpt_infor", request.getUserMessage(), modelReadyUserText);
    }

    /**
     * Fallback OpenAI chat without persistence (Comprehend orchestrates persist/quota).
     */
    public ChatResponse chatWithoutPersistence(ChatRequest request) {
        validateApiKey();
        String modelReadyUserText = prepareUserContentForOpenAi(request.getUserMessage());
        List<Map<String, String>> messages = buildMessages(request, systemPromptForFallback(), modelReadyUserText, true);
        OpenAiCallResult result = callOpenAi(messages);
        return toChatResponse(request, result, "gpt_infor", request.getUserMessage(), modelReadyUserText);
    }

    /**
     * Grounded OpenAI chat using pre-filtered RAG chunks from Python.
     */
    public ChatResponse chatWithRagContext(ChatRequest request, List<ChunkItem> promptChunks) {
        validateApiKey();
        if (promptChunks == null || promptChunks.isEmpty()) {
            throw new OpenAIException("promptChunks cannot be empty for grounded chat", 400);
        }

        String modelReadyUserText = prepareUserContentForOpenAi(request.getUserMessage());
        String context = formatRagContext(promptChunks);
        String userPrompt = buildRagUserPrompt(context, request.getUserMessage());

        List<Map<String, String>> messages = buildMessages(request, RAG_SYSTEM_PROMPT, userPrompt, true);
        OpenAiCallResult result = callOpenAi(messages);
        return toChatResponse(request, result, "rag", request.getUserMessage(), modelReadyUserText);
    }

    private ChatResponse toChatResponse(
            ChatRequest request,
            OpenAiCallResult result,
            String actionTaken,
            String originalUserText,
            String modelReadyUserText
    ) {
        ChatResponse chatResponse = new ChatResponse(result.content(), result.truncated());
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken(actionTaken);
        chatResponse.setOpenAiUsage(result.usage());
        chatResponse.setSanitizationApplied(!Objects.equals(originalUserText, modelReadyUserText));
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(modelReadyUserText);
        }
        return chatResponse;
    }

    private String systemPromptForFallback() {
        if (systemPromptEnabled && systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt.trim();
        }
        return "";
    }

    private List<Map<String, String>> buildMessages(ChatRequest request, String systemContent, String userContent) {
        return buildMessages(request, systemContent, userContent, false);
    }

    private List<Map<String, String>> buildMessages(
            ChatRequest request,
            String systemContent,
            String userContent,
            boolean skipHistoryPresidio
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemContent != null && !systemContent.isBlank()) {
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemContent);
            messages.add(systemMessage);
        }

        List<MessageDto> sourceHistory = resolveHistory(request);
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
                        ? prepareUserContentForOpenAi(
                                skipHistoryPresidio ? content : presidioService.sanitizeText(content))
                        : content);
                messages.add(map);
            }
        }

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);
        return messages;
    }

    private List<MessageDto> resolveHistory(ChatRequest request) {
        List<MessageDto> clientHistory = request.getHistory() != null ? request.getHistory() : List.of();
        boolean hasClientHistory = allowClientHistory && !clientHistory.isEmpty();

        List<MessageDto> sourceHistory;
        if (hasClientHistory) {
            sourceHistory = clientHistory;
        } else if (loadHistoryFromDb) {
            sourceHistory = chatPersistenceService.loadHistoryForPrompt(
                    request.getTenantCode(),
                    request.getUserId(),
                    request.getSessionId(),
                    maxHistoryExchanges
            );
        } else {
            sourceHistory = List.of();
        }

        if (sourceHistory != null && sourceHistory.size() > maxHistoryExchanges) {
            int fromIndex = Math.max(0, sourceHistory.size() - maxHistoryExchanges);
            sourceHistory = sourceHistory.subList(fromIndex, sourceHistory.size());
        }
        return sourceHistory;
    }

    private OpenAiCallResult callOpenAi(List<Map<String, String>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("Calling OpenAI chat completions. model={}, messageCount={}", model, messages.size());
        long start = System.currentTimeMillis();

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
            handleOpenAiError(e);
            throw new OpenAIException("OpenAI call failed", e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("OpenAI request failed after {}ms: {}", elapsed, e.getMessage());
            throw new OpenAIException(
                    "OpenAI request timed out or failed after " + elapsed + "ms: " + e.getMessage(),
                    504
            );
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("OpenAI chat completions completed in {}ms", elapsed);

        if (response == null) {
            throw new OpenAIException("No response from OpenAI.", 502);
        }

        String content = extractContent(response);
        boolean truncated = isTruncated(response);
        OpenAIUsage usage = extractUsage(response, model);
        return new OpenAiCallResult(content, truncated, usage);
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAIException(
                    "OpenAI API key is missing. Set OPENAI_API_KEY env var or openai.api.key property.",
                    401
            );
        }
    }

    private void handleOpenAiError(HttpClientErrorException e) {
        int code = e.getStatusCode().value();
        HttpHeaders h = e.getResponseHeaders();
        if (h != null) {
            log.warn("OpenAI x-ratelimit-limit-requests={}", h.getFirst("x-ratelimit-limit-requests"));
            log.warn("OpenAI x-ratelimit-remaining-requests={}", h.getFirst("x-ratelimit-remaining-requests"));
            log.warn("OpenAI x-ratelimit-reset-requests={}", h.getFirst("x-ratelimit-reset-requests"));
            log.warn("OpenAI x-ratelimit-limit-tokens={}", h.getFirst("x-ratelimit-limit-tokens"));
            log.warn("OpenAI x-ratelimit-remaining-tokens={}", h.getFirst("x-ratelimit-remaining-tokens"));
            log.warn("OpenAI x-ratelimit-reset-tokens={}", h.getFirst("x-ratelimit-reset-tokens"));
        }
        log.warn("OpenAI error status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
        String msg = code == 401
                ? "OpenAI API key is invalid or missing. Check openai.api.key in application.properties (no quotes)."
                : "OpenAI API error: " + code + " " + e.getStatusText();
        throw new OpenAIException(msg, code);
    }

    private ChatResponse blockedResponse(TenantQuotaExceededException e) {
        ChatResponse blocked = new ChatResponse("Token limit reached for this tenant. Please top up to continue.", false);
        blocked.setLimitExceeded(true);
        blocked.setUsage(e.getUsage());
        blocked.setBlockReason("LIMIT_EXCEEDED");
        blocked.setUpgradeOptions(Arrays.asList("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
        return blocked;
    }

    private String extractContent(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            throw new OpenAIException("No choices returned from OpenAI.", 502);
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new OpenAIException("Unexpected response format from OpenAI.", 502);
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new OpenAIException("Unexpected message format from OpenAI.", 502);
        }
        Object contentObj = messageMap.get("content");
        return contentObj != null ? contentObj.toString() : "";
    }

    private boolean isTruncated(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choicesList) || choicesList.isEmpty()) {
            return false;
        }
        Object firstChoice = choicesList.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return false;
        }
        Object finishReason = choiceMap.get("finish_reason");
        return finishReason != null && "length".equals(finishReason.toString());
    }

    OpenAIUsage extractUsage(Map<String, Object> response, String modelUsed) {
        OpenAIUsage usage = new OpenAIUsage();
        usage.setModel(modelUsed);
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usageMap)) {
            usage.setPromptTokens(0);
            usage.setCompletionTokens(0);
            usage.setTotalTokens(0);
            return usage;
        }
        usage.setPromptTokens(toInt(usageMap.get("prompt_tokens")));
        usage.setCompletionTokens(toInt(usageMap.get("completion_tokens")));
        usage.setTotalTokens(toInt(usageMap.get("total_tokens")));
        return usage;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    String formatRagContext(List<ChunkItem> chunks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkItem chunk = chunks.get(i);
            float score = chunk.getScore() != null ? chunk.getScore() : 0f;
            String scorePct = String.format(Locale.ROOT, "%.1f%%", score * 100);
            builder.append("--- Document ").append(i + 1).append(" (Relevance: ").append(scorePct).append(") ---");
            if (chunk.getSource() != null && !chunk.getSource().isBlank()) {
                builder.append("\nSource: ").append(chunk.getSource());
            }
            if (chunk.getProgramIds() != null && !chunk.getProgramIds().isEmpty()) {
                builder.append("\nPrograms: ").append(String.join(", ", chunk.getProgramIds()));
            }
            builder.append("\n\n").append(chunk.getChunk() != null ? chunk.getChunk() : "").append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildRagUserPrompt(String context, String question) {
        return "Context from M3 Documentation:\n" + context + "\n\n---\n\n"
                + "Question: " + question + "\n\n"
                + "Provide a clear, accurate answer based ONLY on the context above.";
    }

    private boolean isValidRole(String role) {
        return "system".equals(role) || "user".equals(role) || "assistant".equals(role);
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

    private record OpenAiCallResult(String content, boolean truncated, OpenAIUsage usage) {
    }
}
