package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.MessageDto;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionResult;
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
    private final SuggestionEngineService suggestionEngineService;
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
            SuggestionEngineService suggestionEngineService,
            PythonRagService pythonRagService
    ) {
        this.restTemplate = new RestTemplate();
        this.comprehendAnonymizationService = comprehendAnonymizationService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
        this.suggestionEngineService = suggestionEngineService;
        this.pythonRagService = pythonRagService;
    }

    public ChatResponse chat(ChatRequest request) {
        // Validate required fields
        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new OpenAIException("User message cannot be empty", 400);
        }

        log.info("ComprehendChatService.chat called for tenantCode={}, userId={}, sessionId={}",
                request.getTenantCode(), request.getUserId(), request.getSessionId());

        // Step 1: Sanitize user input with Comprehend
        String originalUserText = request.getUserMessage();
        String sanitizedUserText = sanitizeTextWithComprehend(originalUserText);
        log.info(
                "Processing chat request with Python RAG API. tenantCode={}, userId={}, sessionId={}, sanitized='{}'",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                sanitizedUserText
        );

        // Step 2: Call Python RAG API with sanitized question
        PythonQueryResponse pythonResponse = callPythonRagApi(sanitizedUserText, request);

        // Step 3: Extract answer and token usage from Python RAG response
        String replyText = pythonResponse.getReply() != null ? pythonResponse.getReply() : pythonResponse.getAnswer();
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
        boolean sanitizedFlag = !Objects.equals(originalUserText, sanitizedUserText);
        log.info("ComprehendChatService: About to call persistChat() with tenantCode={}, userId={}, sessionId={}", 
                request.getTenantCode(), request.getUserId(), request.getSessionId());
        chatPersistenceService.persistChat(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                originalUserText,
                sanitizedUserText,
                replyText,
                consumedTokens,
                pythonResponse.getActionTaken(),
                sanitizedFlag
        );
        log.info("ComprehendChatService: persistChat() completed successfully for sessionId={}", request.getSessionId());

        // Step 6: Build chat response
        ChatResponse chatResponse = new ChatResponse(replyText, false);
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken(pythonResponse.getActionTaken());
        chatResponse.setPendingTool(pythonResponse.getPendingTool());
        chatResponse.setPendingArgs(pythonResponse.getPendingArgs());
        chatResponse.setCollectingTool(pythonResponse.getCollectingTool());
        chatResponse.setCollectedArgs(pythonResponse.getCollectedArgs());
        chatResponse.setNextField(pythonResponse.getNextField());
        chatResponse.setNextFieldOptional(pythonResponse.getNextFieldOptional());
        chatResponse.setM3Data(pythonResponse.getM3Data());
        chatResponse.setSanitizationApplied(!Objects.equals(originalUserText, sanitizedUserText));
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(sanitizedUserText);
        }

        // Step 7: Generate follow-up suggestions based on sanitized question and answer
        ChatRequest suggestionRequest = request;
        if (!Objects.equals(originalUserText, sanitizedUserText)) {
            suggestionRequest = copyRequestWithUserMessage(request, sanitizedUserText);
        }
        SuggestionContext context = buildSuggestionContext(suggestionRequest, replyText, pythonResponse.getSources());
        SuggestionResult suggestionResult = suggestionEngineService.generateSuggestions(context);
        chatResponse.setSuggestions(suggestionResult.getSuggestions());
        chatResponse.setSuggestionDetails(suggestionResult.getDetails());

        log.info(
                "Chat response prepared successfully. tenantCode={}, userId={}, sessionId={}, answer_length={}, sources_count={}, tokens_consumed={}, retrieved_chunks={}",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                replyText != null ? replyText.length() : 0,
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
    private PythonQueryResponse callPythonRagApi(String sanitizedQuestion, ChatRequest originalRequest) {
        PythonQueryRequest ragRequest = new PythonQueryRequest();
        ragRequest.setMessage(sanitizedQuestion);
        ragRequest.setHistory(originalRequest.getHistory());
        // Use default values from configuration for other parameters

        return pythonRagService.query(ragRequest);
    }

    /**
     * Sanitize text using Comprehend detection + Presidio anonymization
     */
    private String sanitizeTextWithComprehend(String text) {
        if (text == null || text.isBlank()) {
            log.debug("sanitizeTextWithComprehend: input text is null or blank");
            return text;
        }

        try {
            log.info("Sanitizing text with Comprehend-based anonymization");
            log.debug("Calling ComprehendAnonymizationService.detectAndAnonymize with text='{}'", text);
            Map<String, Object> result = comprehendAnonymizationService.detectAndAnonymize(text);
            Object sanitizedText = result.get("sanitizedText");
            String sanitized = sanitizedText != null ? sanitizedText.toString() : text;
            log.debug("Comprehend anonymization returned sanitizedText='{}'", sanitized);
            return sanitized;
        } catch (Exception e) {
            log.warn("Comprehend anonymization failed, using original text: {}", e.getMessage());
            return text;
        }
    }

    private String prepareUserContentForOpenAi(String sanitizedText) {
        if (sanitizedText == null) {
            return null;
        }
        return sanitizedText;
    }

    private SuggestionContext buildSuggestionContext(ChatRequest request, String answer, List<?> sources) {
        SuggestionContext context = new SuggestionContext();
        context.setTenantCode(request.getTenantCode());
        context.setUserId(request.getUserId());
        context.setSessionId(request.getSessionId());
        context.setUserMessage(request.getUserMessage());
        context.setHistory(request.getHistory());
        context.setAnswer(answer);
        if (sources != null) {
            context.setSources(sources.stream().map(Object::toString).toList());
        }
        return context;
    }

    private ChatRequest copyRequestWithUserMessage(ChatRequest originalRequest, String newUserMessage) {
        if (originalRequest == null) {
            return null;
        }
        ChatRequest copy = new ChatRequest();
        copy.setTenantCode(originalRequest.getTenantCode());
        copy.setUserId(originalRequest.getUserId());
        copy.setSessionId(originalRequest.getSessionId());
        copy.setUserMessage(newUserMessage);
        copy.setHistory(originalRequest.getHistory());
        return copy;
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
