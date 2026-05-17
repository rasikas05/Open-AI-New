package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SuggestionDto;
import com.ai.openai_api_service.model.python_rag.PythonQueryRequest;
import com.ai.openai_api_service.model.python_rag.PythonQueryResponse;
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

    private static final String GENERIC_MESSAGE = "I'm here to help with questions related to Infor M3 ERP. If you have any queries about Infor M3 modules, processes, or troubleshooting, please let me know!";

    private final RestTemplate restTemplate;
    private final ComprehendAnonymizationService comprehendAnonymizationService;
    private final ChatPersistenceService chatPersistenceService;
    private final TenantQuotaService tenantQuotaService;
    private final SuggestionRuleService suggestionRuleService;
    private final SuggestionLLMService suggestionLLMService;
    private final SuggestionCacheService suggestionCacheService;
    private final PythonRagService pythonRagService;

    @Value("${openai.response.include-sanitization-debug:false}")
    private boolean includeSanitizationDebug;

    @Value("${suggestion.min-count:3}")
    private int minSuggestionCount;

    @Value("${suggestion.max-count:5}")
    private int maxSuggestionCount;

    @Value("${suggestion.rule.enabled:true}")
    private boolean ruleEnabled;

    @Value("${suggestion.llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${chat.history.load-from-db:true}")
    private boolean loadHistoryFromDb;

    @Value("${chat.history.max-exchanges:10}")
    private int maxHistoryExchanges;

    @Value("${chat.history.allow-client-history:false}")
    private boolean allowClientHistory;

    public ComprehendChatService(
            ComprehendAnonymizationService comprehendAnonymizationService,
            ChatPersistenceService chatPersistenceService,
            TenantQuotaService tenantQuotaService,
            SuggestionRuleService suggestionRuleService,
            SuggestionLLMService suggestionLLMService,
            SuggestionCacheService suggestionCacheService,
            PythonRagService pythonRagService
    ) {
        this.restTemplate = new RestTemplate();
        this.comprehendAnonymizationService = comprehendAnonymizationService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
        this.suggestionRuleService = suggestionRuleService;
        this.suggestionLLMService = suggestionLLMService;
        this.suggestionCacheService = suggestionCacheService;
        this.pythonRagService = pythonRagService;
    }

    public ChatResponse chat(ChatRequest request) {
        // Validate required fields
        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new OpenAIException("User message cannot be empty", 400);
        }

        // Step 1: Sanitize user input with Comprehend
        String originalUserText = request.getUserMessage();
        String sanitizedUserText = sanitizeTextWithComprehend(originalUserText);
        log.info(
                "Processing chat request with Python RAG API. tenantCode={}, userId={}, sessionId={}, original='{}', sanitized='{}'",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                originalUserText,
                sanitizedUserText
        );

        // Step 2: Call Python RAG API with sanitized question
        PythonQueryResponse pythonResponse = callPythonRagApi(sanitizedUserText);

        // Step 3: Extract answer and token usage from Python RAG response
        String answer = pythonResponse.getAnswer();
        Integer retrievedChunks = pythonResponse.getRetrievedChunks();
        Integer totalTokens = pythonResponse.getUsage() != null ? pythonResponse.getUsage().getTotalTokens() : null;
        int consumedTokens = totalTokens != null ? totalTokens : 0;

        // Step 4: Check and record token quota usage
        String usageReferenceId = request.getSessionId() + ":" + System.currentTimeMillis();
        try {
            tenantQuotaService.recordUsage(request.getTenantCode(), consumedTokens, usageReferenceId);
        } catch (TenantQuotaExceededException e) {
            ChatResponse blocked = new ChatResponse("Token limit reached for this tenant. Please top up to continue.", false);
            blocked.setLimitExceeded(true);
            blocked.setUsage(e.getUsage());
            blocked.setBlockReason("LIMIT_EXCEEDED");
            blocked.setUpgradeOptions(Arrays.asList("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
            return blocked;
        }

        // Step 5: Persist the chat message
        log.info("ComprehendChatService: About to call persistChat() with tenantCode={}, userId={}, sessionId={}", 
                request.getTenantCode(), request.getUserId(), request.getSessionId());
        chatPersistenceService.persistChat(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                originalUserText,
                sanitizedUserText,
                answer,
                consumedTokens
        );
        log.info("ComprehendChatService: persistChat() completed successfully for sessionId={}", request.getSessionId());

        // Step 6: Build chat response
        ChatResponse chatResponse = new ChatResponse(answer, false);
        chatResponse.setSanitizationApplied(!Objects.equals(originalUserText, sanitizedUserText));
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(sanitizedUserText);
        }

        // Step 7: Generate suggestions based on the sanitized question
        ChatRequest suggestionRequest = request;
        if (!Objects.equals(originalUserText, sanitizedUserText)) {
            suggestionRequest = copyRequestWithUserMessage(request, sanitizedUserText);
        }

        SuggestionResult suggestionResult = buildSuggestions(suggestionRequest);
        chatResponse.setSuggestions(suggestionResult.suggestions());
        chatResponse.setSuggestionDetails(suggestionResult.details());

        log.info(
                "Chat response prepared successfully. tenantCode={}, userId={}, sessionId={}, answer_length={}, sources_count={}, tokens_consumed={}, retrieved_chunks={}",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                answer != null ? answer.length() : 0,
                pythonResponse.getSources() != null ? pythonResponse.getSources().size() : 0,
                consumedTokens,
                retrievedChunks
        );

        return chatResponse;
    }

    /**
     * Call the Python RAG API with the sanitized question.
     * The Python API handles all retrieval, prompt building, and OpenAI calls internally.
     */
    private PythonQueryResponse callPythonRagApi(String sanitizedQuestion) {
        PythonQueryRequest ragRequest = new PythonQueryRequest();
        ragRequest.setQuestion(sanitizedQuestion);
        // Use default values from configuration for other parameters

        return pythonRagService.query(ragRequest);
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
        // For Comprehend path, keep the anonymization placeholders instead of removing them
        return sanitizedText;
    }

    private SuggestionResult buildSuggestions(ChatRequest request) {
        int minCount = Math.max(1, minSuggestionCount);
        int maxCount = Math.max(minCount, maxSuggestionCount);

        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            return new SuggestionResult(List.of(), List.of());
        }

        if (!suggestionRuleService.isSupportedM3Topic(request.getUserMessage())) {
            List<String> generic = suggestionRuleService.genericSuggestions(maxCount);
            List<SuggestionDto> details = generic.stream()
                    .map(text -> new SuggestionDto(text, "GENERIC"))
                    .toList();
            return new SuggestionResult(generic, details);
        }

        Map<String, String> merged = new java.util.LinkedHashMap<>();
        if (llmEnabled) {
            String cacheKey = buildCacheKey(request);
            List<String> cached = suggestionCacheService.get(cacheKey);
            if (!cached.isEmpty()) {
                for (String suggestion : cached) {
                    merged.putIfAbsent(suggestion, "LLM");
                }
            } else {
                List<String> llmSuggestions = suggestionLLMService.suggest(request, minCount, maxCount);
                if (!llmSuggestions.isEmpty()) {
                    suggestionCacheService.put(cacheKey, llmSuggestions);
                    for (String suggestion : llmSuggestions) {
                        merged.putIfAbsent(suggestion, "LLM");
                    }
                }
            }
        }

        if (ruleEnabled && merged.size() < maxCount) {
            List<String> ruleSuggestions = suggestionRuleService.suggest(request.getUserMessage(), maxCount - merged.size());
            for (String suggestion : ruleSuggestions) {
                merged.putIfAbsent(suggestion, "RULE");
            }
        }

        List<SuggestionDto> details = cleanSuggestionDetails(merged, maxCount);
        List<String> result = details.stream().map(SuggestionDto::getText).toList();
        if (details.isEmpty()) {
            log.info("No suggestions generated for tenantCode={}, userId={}, sessionId={}",
                    request.getTenantCode(), request.getUserId(), request.getSessionId());
        }
        return new SuggestionResult(result, details);
    }

    private String buildCacheKey(ChatRequest request) {
        String tenant = normalizeToken(request.getTenantCode());
        String user = normalizeToken(request.getUserId());
        String session = normalizeToken(request.getSessionId());
        String msg = normalizeToken(request.getUserMessage());
        return tenant + "|" + user + "|" + session + "|" + msg;
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private ChatRequest copyRequestWithUserMessage(ChatRequest original, String userMessage) {
        ChatRequest copy = new ChatRequest();
        copy.setTenantCode(original.getTenantCode());
        copy.setUserId(original.getUserId());
        copy.setSessionId(original.getSessionId());
        copy.setUserMessage(userMessage);
        copy.setHistory(original.getHistory());
        return copy;
    }

    private List<SuggestionDto> cleanSuggestionDetails(Map<String, String> sourceWithType, int maxCount) {
        List<SuggestionDto> result = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : sourceWithType.entrySet()) {
            String suggestion = entry.getKey();
            String sourceType = entry.getValue();
            if (suggestion == null) {
                continue;
            }
            String clean = suggestion.trim().replaceAll("\\s{2,}", " ");
            if (clean.isBlank()) {
                continue;
            }
            if (clean.length() > 140) {
                clean = clean.substring(0, 140).trim();
            }
            if (!clean.endsWith("?")) {
                clean = clean + "?";
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

    private record SuggestionResult(List<String> suggestions, List<SuggestionDto> details) {
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
