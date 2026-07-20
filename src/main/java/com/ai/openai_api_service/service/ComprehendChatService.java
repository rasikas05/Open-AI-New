package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.LiveHistoryAuditMetadata;
import com.ai.openai_api_service.model.LiveHistoryResult;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.QueryRewriteResult;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionResult;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
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
    static final String LEX_FALLBACK_CLARIFICATION_MESSAGE =
            "I couldn't understand your request. Please provide more details or rephrase your request.\n\n"
                    + "Examples:\n"
                    + "• Show customer Y00111\n"
                    + "• Get customer details for Y00111";
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
    private final LexService lexService;
    private final LexFulfillmentService lexFulfillmentService;
    private final LiveHistorySummaryBuilder liveHistorySummaryBuilder;
    private final RequestedInformationResolver requestedInformationResolver;
    private final IntentApiCatalog intentApiCatalog;

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
            OpenAIService openAIService,
            LexService lexService,
            LexFulfillmentService lexFulfillmentService,
            LiveHistorySummaryBuilder liveHistorySummaryBuilder,
            RequestedInformationResolver requestedInformationResolver,
            IntentApiCatalog intentApiCatalog
    ) {
        this.comprehendAnonymizationService = comprehendAnonymizationService;
        this.chatPersistenceService = chatPersistenceService;
        this.tenantQuotaService = tenantQuotaService;
        this.suggestionEngineService = suggestionEngineService;
        this.pythonRagService = pythonRagService;
        this.openAIService = openAIService;
        this.lexService = lexService;
        this.lexFulfillmentService = lexFulfillmentService;
        this.liveHistorySummaryBuilder = liveHistorySummaryBuilder;
        this.requestedInformationResolver = requestedInformationResolver;
        this.intentApiCatalog = intentApiCatalog;
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

        PythonRouteResponse routeResponse = pythonRagService.route(originalUserText);
        String route = routeResponse != null ? routeResponse.getRoute() : "rag";
        log.info(
                "Comprehend route decision: route='{}', handler='{}', original='{}', sanitized='{}'",
                route,
                ROUTE_LIVE.equalsIgnoreCase(route)
                        ? (lexService.isEnabled() ? "live/lex" : "live/python-chat")
                        : "documentation/retrieval",
                originalUserText,
                sanitizedUserText
        );

        ChatResponse chatResponse;
        List<SourceItem> sourcesForSuggestions = null;
        List<SourceItem> responseSources = null;
        String retrievalReason = null;
        Integer retrievalTimeMs = null;
        Float maxScore = null;

        if (ROUTE_LIVE.equalsIgnoreCase(route)) {
            if (lexService.isEnabled()) {
                LexLiveRouteResult lexResult = handleLexLiveRoute(
                        request,
                        originalUserText,
                        sanitizedUserText
                );
                chatResponse = lexResult.chatResponse();
                if (lexResult.fallbackToDoc()) {
                    sourcesForSuggestions = lexResult.sourcesForSuggestions();
                    responseSources = lexResult.responseSources();
                    retrievalReason = lexResult.retrievalReason();
                    retrievalTimeMs = lexResult.retrievalTimeMs();
                    maxScore = lexResult.maxScore();
                }
            } else {
                chatResponse = handleLiveRoute(workingRequest, sanitizedUserText);
            }
        } else {
            DocRouteResult docResult = handleDocumentationRoute(workingRequest, originalUserText, sanitizedUserText);
            chatResponse = docResult.chatResponse();
            sourcesForSuggestions = docResult.sourcesForSuggestions();
            responseSources = docResult.responseSources();
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
        LiveHistoryResult liveHistory = liveHistorySummaryBuilder.build(chatResponse).orElse(null);
        String replyForPersistence = liveHistory != null
                ? liveHistory.summaryText()
                : chatResponse.getReply();
        LiveHistoryAuditMetadata auditMetadata = liveHistory != null
                ? liveHistory.auditMetadata()
                : null;
        chatPersistenceService.persistChat(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                originalUserText,
                sanitizedUserText,
                replyForPersistence,
                openAiUsage,
                chatResponse.getActionTaken(),
                sanitizedFlag,
                retrievalReason,
                retrievalTimeMs,
                auditMetadata
        );

        chatResponse.setRetrievalReason(retrievalReason);
        chatResponse.setRetrievalTimeMs(retrievalTimeMs);
        chatResponse.setMaxScore(maxScore);
        if (responseSources != null) {
            chatResponse.setSources(responseSources);
        }
        chatResponse.setSanitizationApplied(sanitizedFlag);
        if (includeSanitizationDebug) {
            chatResponse.setSanitizedUserMessage(sanitizedUserText);
        }

        SuggestionContext context = buildSuggestionContext(workingRequest, chatResponse.getReply(), sourcesForSuggestions);
        SuggestionResult suggestionResult = suggestionEngineService.generateSuggestions(context);
        chatResponse.setSuggestions(suggestionResult.getSuggestions());
        chatResponse.setSuggestionDetails(suggestionResult.getDetails());

        log.info(
                "Spring chat complete | session={} | route={} | action={} | retrievalReason={} | collecting={} | tokens={}",
                request.getSessionId(),
                route,
                chatResponse.getActionTaken(),
                retrievalReason,
                chatResponse.getCollectingTool(),
                consumedTokens
        );

        return chatResponse;
    }

    private LexLiveRouteResult handleLexLiveRoute(
            ChatRequest request,
            String originalUserText,
            String sanitizedUserText
    ) {
        String lexSessionId = lexService.buildLexSessionId(request);
        LexRecognizeResult lexResult = lexService.recognizeText(lexSessionId, originalUserText);

        if (lexResult.isFallbackIntent()) {
            return lexFallbackRouteResult(request, lexResult);
        }

        if (lexResult.isElicitSlot()) {
            List<String> requestedInformation = requestedInformationResolver.resolve(
                    originalUserText,
                    lexResult.getIntentName(),
                    lexResult.getSessionAttributes()
            );
            persistRequestedInformationIfNeeded(lexSessionId, requestedInformation, lexResult);
            ChatResponse chatResponse = new ChatResponse(lexResult.firstMessage(), false);
            chatResponse.setActionTaken("lex_elicit_slot");
            chatResponse.setLexIntent(lexResult.getIntentName());
            chatResponse.setLexDialogAction(lexResult.getDialogActionType());
            chatResponse.setLexSlotToElicit(lexResult.getSlotToElicit());
            return LexLiveRouteResult.lex(chatResponse);
        }

        if (lexResult.isReadyForFulfillment()) {
            var fulfillmentOutcome = lexFulfillmentService.fulfillOutcome(lexResult, originalUserText);
            ChatResponse chatResponse = fulfillmentOutcome.response();
            chatResponse.setLexIntent(lexResult.getIntentName());
            chatResponse.setLexDialogAction(lexResult.getDialogActionType());

            List<String> requestedInformation = resolveRequestedInformationForFulfillment(
                    originalUserText,
                    lexResult,
                    fulfillmentOutcome
            );
            chatResponse.setRequestedInformation(requestedInformation);
            return LexLiveRouteResult.lex(chatResponse);
        }

        return lexFallbackRouteResult(request, lexResult);
    }

    private List<String> resolveRequestedInformationForFulfillment(
            String originalUserText,
            LexRecognizeResult lexResult,
            LexFulfillmentOutcome fulfillmentOutcome
    ) {
        if ("lex_invalid_slot".equals(fulfillmentOutcome.response().getActionTaken())) {
            return List.of();
        }

        boolean isSearch = intentApiCatalog.find(lexResult.getIntentName())
                .map(IntentDefinition::requestType)
                .filter(type -> type == RequestType.SEARCH)
                .isPresent();

        if (isSearch) {
            return requestedInformationResolver.resolveForSearch(
                    originalUserText,
                    fulfillmentOutcome.searchCriteria()
            );
        }

        return requestedInformationResolver.resolve(
                originalUserText,
                lexResult.getIntentName(),
                lexResult.getSessionAttributes()
        );
    }

    private void persistRequestedInformationIfNeeded(
            String lexSessionId,
            List<String> requestedInformation,
            LexRecognizeResult lexResult
    ) {
        if (!requestedInformationResolver.differsFromSession(
                requestedInformation,
                lexResult.getSessionAttributes()
        )) {
            return;
        }
        Map<String, String> attrs = new LinkedHashMap<>(lexResult.getSessionAttributes());
        attrs.put(
                LexRecognizeResult.ATTR_REQUESTED_INFORMATION,
                requestedInformationResolver.encode(requestedInformation)
        );
        lexService.putSessionAttributes(lexSessionId, attrs);
    }

    private LexLiveRouteResult lexFallbackRouteResult(ChatRequest request, LexRecognizeResult lexResult) {
        log.info(
                "Lex could not resolve a supported live intent for session={} intent='{}' state='{}' dialogAction='{}'",
                request.getSessionId(),
                lexResult.getIntentName(),
                lexResult.getIntentState(),
                lexResult.getDialogActionType()
        );
        return LexLiveRouteResult.lex(buildLexFallbackResponse());
    }

    private ChatResponse buildLexFallbackResponse() {
        ChatResponse chatResponse = new ChatResponse(LEX_FALLBACK_CLARIFICATION_MESSAGE, false);
        chatResponse.setActionTaken("lex_fallback");
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
        if (pythonResponse.getSources() != null && !pythonResponse.getSources().isEmpty()) {
            chatResponse.setSources(pythonResponse.getSources());
        }
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
            return new DocRouteResult(chatResponse, List.of(), List.of(), "retrieval_error", null, null);
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

        List<ChunkItem> promptChunks = retrieval.getPromptChunks() != null
                ? retrieval.getPromptChunks()
                : List.of();
        List<SourceItem> responseSources = toChunkSources(promptChunks);

        ChatResponse chatResponse;
        if (RETRIEVAL_READY.equals(reason)) {
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
                toSourceItems(promptChunks),
                responseSources,
                reason,
                retrieval.getRetrievalTimeMs(),
                retrieval.getMaxScore()
        );
    }

    private List<SourceItem> toChunkSources(List<ChunkItem> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<SourceItem> sources = new ArrayList<>();
        for (ChunkItem chunk : chunks) {
            String url = chunk.getSource() != null ? chunk.getSource() : "";
            if (url.isBlank()) {
                continue;
            }
            sources.add(new SourceItem(url, chunk.getTitle(), chunk.getScore()));
        }
        return sources;
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
            List<SourceItem> sourcesForSuggestions,
            List<SourceItem> responseSources,
            String retrievalReason,
            Integer retrievalTimeMs,
            Float maxScore
    ) {
    }

    private record LexLiveRouteResult(
            ChatResponse chatResponse,
            boolean fallbackToDoc,
            List<SourceItem> sourcesForSuggestions,
            List<SourceItem> responseSources,
            String retrievalReason,
            Integer retrievalTimeMs,
            Float maxScore
    ) {
        static LexLiveRouteResult lex(ChatResponse chatResponse) {
            return new LexLiveRouteResult(chatResponse, false, null, null, null, null, null);
        }

        static LexLiveRouteResult fallback(DocRouteResult docResult) {
            return new LexLiveRouteResult(
                    docResult.chatResponse(),
                    true,
                    docResult.sourcesForSuggestions(),
                    docResult.responseSources(),
                    docResult.retrievalReason(),
                    docResult.retrievalTimeMs(),
                    docResult.maxScore()
            );
        }
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
