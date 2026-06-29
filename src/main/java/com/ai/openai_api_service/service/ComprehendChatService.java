package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.QueryRewriteResult;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionResult;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
import com.ai.openai_api_service.model.python_rag.PythonQueryRequest;
import com.ai.openai_api_service.model.python_rag.PythonQueryResponse;
import com.ai.openai_api_service.model.python_rag.PythonRetrievalResponse;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
import com.ai.openai_api_service.model.python_rag.SourceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ComprehendChatService {
    private static final Logger log = LoggerFactory.getLogger(ComprehendChatService.class);
    private static final String RETRIEVAL_READY = "ready_for_grounding";
    private static final String RETRIEVAL_RAG_NO_ANSWER = "rag_no_answer_fallback";
    private static final String ROUTE_LIVE = "live";
    private static final List<String> RAG_INSUFFICIENT_SIGNALS = List.of(
            "not available in the current documentation",
            "not available in the context",
            "##insufficient##",
            "context does not",
            "context provided does not",
            "not covered in the",
            "no information",
            "does not contain",
            "cannot provide",
            "not include specific",
            "does not provide"
    );

    private final ComprehendAnonymizationService comprehendAnonymizationService;
    private final ChatPersistenceService chatPersistenceService;
    private final TenantQuotaService tenantQuotaService;
    private final SuggestionEngineService suggestionEngineService;
    private final PythonRagService pythonRagService;
    private final OpenAIService openAIService;

    @Value("${openai.response.include-sanitization-debug:false}")
    private boolean includeSanitizationDebug;

    @Value("${rag.query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    @Value("${rag.fallback-on-no-answer:true}")
    private boolean ragFallbackOnNoAnswer;

    public ComprehendChatService(
            ComprehendAnonymizationService comprehendAnonymizationService,
            ChatPersistenceService chatPersistenceService,
            TenantQuotaService tenantQuotaService,
            SuggestionEngineService suggestionEngineService,
            PythonRagService pythonRagService,
            OpenAIService openAIService
    ) {
        this.comprehendAnonymizationService = comprehendAnonymizationService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
        this.suggestionEngineService = suggestionEngineService;
        this.pythonRagService = pythonRagService;
        this.openAIService = openAIService;
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new OpenAIException("User message cannot be empty", 400);
        }

        TenantQuotaService.QuotaCheckResult quotaCheck = tenantQuotaService.checkBeforeChat(request.getTenantCode());
        if (!quotaCheck.allowed()) {
            return blockedQuotaResponse(quotaCheck);
        }

        String originalUserText = request.getUserMessage();
        String sanitizedUserText = sanitizeTextWithComprehend(originalUserText);
        ChatRequest workingRequest = copyRequestWithUserMessage(request, sanitizedUserText);

        log.info(
                "ComprehendChatService.chat tenantCode={}, userId={}, sessionId={}, sanitized='{}'",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                sanitizedUserText
        );

        PythonRouteResponse routeResponse = pythonRagService.route(sanitizedUserText);
        String route = routeResponse != null ? routeResponse.getRoute() : "rag";

        ChatResponse chatResponse;
        List<SourceItem> sourcesForSuggestions = null;
        String retrievalReason = null;
        Integer retrievalTimeMs = null;
        Float maxScore = null;

        if (ROUTE_LIVE.equalsIgnoreCase(route)) {
            chatResponse = handleLiveRoute(workingRequest, sanitizedUserText);
        } else {
            DocRouteResult docResult = handleDocumentationRoute(workingRequest, originalUserText, sanitizedUserText);
            chatResponse = docResult.chatResponse();
            sourcesForSuggestions = docResult.sources();
            retrievalReason = docResult.retrievalReason();
            retrievalTimeMs = docResult.retrievalTimeMs();
            maxScore = docResult.maxScore();
        }

        if (Boolean.TRUE.equals(chatResponse.getLimitExceeded())) {
            return chatResponse;
        }

        OpenAIUsage openAiUsage = chatResponse.getOpenAiUsage();
        int consumedTokens = openAiUsage != null && openAiUsage.getTotalTokens() != null
                ? openAiUsage.getTotalTokens()
                : 0;

        String usageReferenceId = request.getSessionId() + ":" + System.currentTimeMillis();
        try {
            if (consumedTokens > 0) {
                tenantQuotaService.recordUsage(request.getTenantCode(), consumedTokens, usageReferenceId);
            }
        } catch (TenantQuotaExceededException e) {
            return blockedQuotaExceptionResponse(e);
        }

        boolean sanitizedFlag = !Objects.equals(originalUserText, sanitizedUserText);
        chatPersistenceService.persistChat(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                originalUserText,
                sanitizedUserText,
                chatResponse.getReply(),
                openAiUsage,
                chatResponse.getActionTaken(),
                sanitizedFlag,
                retrievalReason,
                retrievalTimeMs
        );

        chatResponse.setRetrievalReason(retrievalReason);
        chatResponse.setRetrievalTimeMs(retrievalTimeMs);
        chatResponse.setMaxScore(maxScore);
        chatResponse.setSanitizationApplied(sanitizedFlag);
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(sanitizedUserText);
        }

        SuggestionContext context = buildSuggestionContext(workingRequest, chatResponse.getReply(), sourcesForSuggestions);
        SuggestionResult suggestionResult = suggestionEngineService.generateSuggestions(context);
        chatResponse.setSuggestions(suggestionResult.getSuggestions());
        chatResponse.setSuggestionDetails(suggestionResult.getDetails());

        return chatResponse;
    }

    private ChatResponse handleLiveRoute(ChatRequest request, String sanitizedUserText) {
        PythonQueryRequest ragRequest = new PythonQueryRequest();
        ragRequest.setMessage(sanitizedUserText);
        ragRequest.setHistory(request.getHistory());

        PythonQueryResponse pythonResponse = pythonRagService.query(ragRequest);
        String replyText = pythonResponse.getReply() != null ? pythonResponse.getReply() : pythonResponse.getAnswer();

        ChatResponse chatResponse = new ChatResponse(replyText != null ? replyText : "", false);
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken(pythonResponse.getActionTaken());
        chatResponse.setPendingTool(pythonResponse.getPendingTool());
        chatResponse.setPendingArgs(pythonResponse.getPendingArgs());
        chatResponse.setCollectingTool(pythonResponse.getCollectingTool());
        chatResponse.setCollectedArgs(pythonResponse.getCollectedArgs());
        chatResponse.setNextField(pythonResponse.getNextField());
        chatResponse.setNextFieldOptional(pythonResponse.getNextFieldOptional());
        chatResponse.setM3Data(pythonResponse.getM3Data());
        return chatResponse;
    }

    private DocRouteResult handleDocumentationRoute(
            ChatRequest request,
            String originalUserText,
            String sanitizedUserText
    ) {
        PythonQueryRequest ragRequest = new PythonQueryRequest();
        ragRequest.setMessage(sanitizedUserText);
        ragRequest.setHistory(request.getHistory());

        List<String> searchQueries;
        OpenAIUsage rewriteUsage = null;
        if (queryRewriteEnabled) {
            QueryRewriteResult rewriteResult = openAIService.rewriteQueries(sanitizedUserText);
            searchQueries = rewriteResult.queries();
            rewriteUsage = rewriteResult.usage();
        } else {
            searchQueries = List.of(sanitizedUserText);
        }

        PythonRetrievalResponse retrieval;
        try {
            retrieval = pythonRagService.retrieve(sanitizedUserText, searchQueries, ragRequest);
        } catch (OpenAIException e) {
            log.warn(
                    "Python retrieval call failed (status={}), falling back to OpenAI: {}",
                    e.getStatusCode(),
                    e.getMessage()
            );
            ChatResponse chatResponse = openAIService.chatWithoutPersistence(request);
            if (rewriteUsage != null) {
                chatResponse.setOpenAiUsage(mergeUsage(rewriteUsage, chatResponse.getOpenAiUsage()));
            }
            return new DocRouteResult(chatResponse, List.of(), "retrieval_error", null, null);
        }

        String reason = retrieval.getRetrievalReason();
        log.info(
                "Doc retrieval: original=\"{}\" sanitized=\"{}\" rewrittenQueries={} reason={} maxScore={} promptChunkCount={} chunkCount={} queryRewriteEnabled={}",
                originalUserText,
                sanitizedUserText,
                searchQueries,
                reason,
                retrieval.getMaxScore(),
                retrieval.getPromptChunkCount(),
                retrieval.getTotal(),
                queryRewriteEnabled
        );

        ChatResponse chatResponse;
        if (RETRIEVAL_READY.equals(reason)) {
            List<ChunkItem> promptChunks = retrieval.getPromptChunks() != null
                    ? retrieval.getPromptChunks()
                    : List.of();
            chatResponse = openAIService.chatWithRagContext(request, promptChunks);
            if (rewriteUsage != null) {
                chatResponse.setOpenAiUsage(mergeUsage(rewriteUsage, chatResponse.getOpenAiUsage()));
            }
            if (ragFallbackOnNoAnswer && isRagInsufficientAnswer(chatResponse.getReply())) {
                log.info("RAG grounded answer insufficient, falling back to OpenAI general knowledge");
                OpenAIUsage ragUsage = chatResponse.getOpenAiUsage();
                chatResponse = openAIService.chatWithoutPersistence(request);
                chatResponse.setOpenAiUsage(mergeUsage(ragUsage, chatResponse.getOpenAiUsage()));
                reason = RETRIEVAL_RAG_NO_ANSWER;
            }
        } else {
            if (retrieval.getError() != null) {
                log.warn("Retrieval error from Python, falling back to OpenAI: {}", retrieval.getError());
            }
            chatResponse = openAIService.chatWithoutPersistence(request);
            if (rewriteUsage != null) {
                chatResponse.setOpenAiUsage(mergeUsage(rewriteUsage, chatResponse.getOpenAiUsage()));
            }
        }

        return new DocRouteResult(
                chatResponse,
                toSourceItems(retrieval.getPromptChunks()),
                reason,
                retrieval.getRetrievalTimeMs(),
                retrieval.getMaxScore()
        );
    }

    private List<SourceItem> toSourceItems(List<ChunkItem> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Map<String, SourceItem> deduped = new LinkedHashMap<>();
        for (ChunkItem chunk : chunks) {
            String url = chunk.getSource() != null ? chunk.getSource() : "";
            if (url.isBlank() || deduped.containsKey(url)) {
                continue;
            }
            deduped.put(url, new SourceItem(url, chunk.getTitle(), chunk.getScore()));
        }
        return new ArrayList<>(deduped.values());
    }

    private ChatResponse blockedQuotaResponse(TenantQuotaService.QuotaCheckResult quotaCheck) {
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
        return blocked;
    }

    private ChatResponse blockedQuotaExceptionResponse(TenantQuotaExceededException e) {
        ChatResponse blocked = new ChatResponse("Token limit reached for this tenant. Please top up to continue.", false);
        blocked.setLimitExceeded(true);
        blocked.setUsage(e.getUsage());
        blocked.setBlockReason("LIMIT_EXCEEDED");
        blocked.setUpgradeOptions(Arrays.asList("Buy 100 tokens", "Buy 500 tokens", "Buy 5000 tokens"));
        return blocked;
    }

    private String sanitizeTextWithComprehend(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            Map<String, Object> result = comprehendAnonymizationService.detectAndAnonymize(text);
            Object sanitizedText = result.get("sanitizedText");
            return sanitizedText != null ? sanitizedText.toString() : text;
        } catch (Exception e) {
            log.warn("Comprehend anonymization failed, using original text: {}", e.getMessage());
            return text;
        }
    }

    private SuggestionContext buildSuggestionContext(ChatRequest request, String answer, List<SourceItem> sources) {
        SuggestionContext context = new SuggestionContext();
        context.setTenantCode(request.getTenantCode());
        context.setUserId(request.getUserId());
        context.setSessionId(request.getSessionId());
        context.setUserMessage(request.getUserMessage());
        context.setHistory(request.getHistory());
        context.setAnswer(answer);
        if (sources != null) {
            context.setSources(sources.stream().map(SourceItem::getUrl).toList());
        }
        return context;
    }

    private ChatRequest copyRequestWithUserMessage(ChatRequest originalRequest, String newUserMessage) {
        ChatRequest copy = new ChatRequest();
        copy.setTenantCode(originalRequest.getTenantCode());
        copy.setUserId(originalRequest.getUserId());
        copy.setSessionId(originalRequest.getSessionId());
        copy.setUserMessage(newUserMessage);
        copy.setHistory(originalRequest.getHistory());
        return copy;
    }

    private record DocRouteResult(
            ChatResponse chatResponse,
            List<SourceItem> sources,
            String retrievalReason,
            Integer retrievalTimeMs,
            Float maxScore
    ) {
    }

    private boolean isRagInsufficientAnswer(String reply) {
        if (reply == null || reply.isBlank()) {
            return true;
        }
        String lower = reply.toLowerCase(Locale.ROOT);
        return RAG_INSUFFICIENT_SIGNALS.stream().anyMatch(lower::contains);
    }

    private OpenAIUsage mergeUsage(OpenAIUsage first, OpenAIUsage second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        int prompt = nullSafeInt(first.getPromptTokens()) + nullSafeInt(second.getPromptTokens());
        int completion = nullSafeInt(first.getCompletionTokens()) + nullSafeInt(second.getCompletionTokens());
        int total = nullSafeInt(first.getTotalTokens()) + nullSafeInt(second.getTotalTokens());
        String model = second.getModel() != null ? second.getModel() : first.getModel();
        return new OpenAIUsage(prompt, completion, total, model);
    }

    private int nullSafeInt(Integer value) {
        return value != null ? value : 0;
    }
}
