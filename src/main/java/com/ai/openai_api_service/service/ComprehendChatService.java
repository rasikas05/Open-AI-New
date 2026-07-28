package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.IntentDefinition;
import com.ai.openai_api_service.model.LexFulfillmentSession;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.LiveHistoryAuditMetadata;
import com.ai.openai_api_service.model.LiveHistoryResult;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.QueryRewriteResult;
import com.ai.openai_api_service.model.RequestType;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.SuggestionContext;
import com.ai.openai_api_service.model.SuggestionResult;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
import com.ai.openai_api_service.model.python_rag.PythonQueryRequest;
import com.ai.openai_api_service.model.python_rag.PythonQueryResponse;
import com.ai.openai_api_service.model.python_rag.PythonRetrievalResponse;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
import com.ai.openai_api_service.model.python_rag.SourceItem;
import com.ai.openai_api_service.model.rag.GroundedRagCallResult;
import com.ai.openai_api_service.model.rag.GroundedRagResult;
import com.ai.openai_api_service.model.rag.RagStatus;
import com.ai.openai_api_service.service.guided.GuidedSearchService;
import com.ai.openai_api_service.service.guided.InMemoryGuidedSearchSessionService;
import com.ai.openai_api_service.service.query.SearchContextService;
import com.ai.openai_api_service.service.rag.ProgramIdDetector;
import com.ai.openai_api_service.service.rag.SearchQueryAssembler;
import com.ai.openai_api_service.service.validation.SearchCriteriaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private final SearchContextService searchContextService;
    private final GuidedSearchService guidedSearchService;
    private final InMemoryGuidedSearchSessionService guidedSearchSessionService;

    @Value("${openai.response.include-sanitization-debug:false}")
    private boolean includeSanitizationDebug;

    @Value("${rag.query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    @Value("${rag.partial.gap-fill.enabled:true}")
    private boolean ragPartialGapFillEnabled;

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
            IntentApiCatalog intentApiCatalog,
            SearchContextService searchContextService,
            GuidedSearchService guidedSearchService,
            InMemoryGuidedSearchSessionService guidedSearchSessionService
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
        this.searchContextService = searchContextService;
        this.guidedSearchService = guidedSearchService;
        this.guidedSearchSessionService = guidedSearchSessionService;
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new OpenAIException("User message cannot be empty", 400);
        }
        long requestStartMs = System.currentTimeMillis();
        long piiDetectionMs = 0L;
        long routeDecisionMs = 0L;
        long queryRewriteMs = 0L;
        long retrievalMs = 0L;
        long groundedMs = 0L;
        long gapFillMs = 0L;
        long generalGptMs = 0L;
        int groundedTokens = 0;
        int gapFillTokens = 0;
        int generalGptTokens = 0;

        TenantQuotaService.QuotaCheckResult quotaCheck = tenantQuotaService.checkBeforeChat(request.getTenantCode());
        if (!quotaCheck.allowed()) {
            return blockedQuotaResponse(quotaCheck);
        }

        String originalUserText = request.getUserMessage();
        long piiStartMs = System.currentTimeMillis();
        String sanitizedUserText = sanitizeTextWithComprehend(originalUserText);
        piiDetectionMs = System.currentTimeMillis() - piiStartMs;
        ChatRequest workingRequest = copyRequestWithUserMessage(request, sanitizedUserText);

        log.info(
                "ComprehendChatService.chat tenantCode={}, userId={}, sessionId={}, sanitized='{}'",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                sanitizedUserText
        );

        boolean guidedHandled = false;
        ChatResponse chatResponse = null;
        List<SourceItem> sourcesForSuggestions = null;
        List<SourceItem> responseSources = null;
        String retrievalReason = null;
        Integer retrievalTimeMs = null;
        Float maxScore = null;
        String route = null;

        GuidedRouteAttempt guidedAttempt = tryHandleActiveGuidedTurn(request, originalUserText);
        if (guidedAttempt.guidedHandled()) {
            guidedHandled = true;
            chatResponse = guidedAttempt.response();
            route = "guided";
        }

        if (!guidedHandled) {
            long routeStartMs = System.currentTimeMillis();
            PythonRouteResponse routeResponse = pythonRagService.route(originalUserText);
            routeDecisionMs = System.currentTimeMillis() - routeStartMs;
            route = routeResponse != null ? routeResponse.getRoute() : "rag";
            log.info(
                    "Comprehend route decision: route='{}', handler='{}', original='{}', sanitized='{}'",
                    route,
                    ROUTE_LIVE.equalsIgnoreCase(route)
                            ? (lexService.isEnabled() ? "live/lex" : "live/python-chat")
                            : "documentation/retrieval",
                    originalUserText,
                    sanitizedUserText
            );

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
                queryRewriteMs = docResult.queryRewriteTimeMs();
                retrievalMs = docResult.retrievalStageTimeMs();
                groundedMs = docResult.groundedTimeMs();
                gapFillMs = docResult.gapFillTimeMs();
                generalGptMs = docResult.generalGptTimeMs();
                groundedTokens = docResult.groundedTokens();
                gapFillTokens = docResult.gapFillTokens();
                generalGptTokens = docResult.generalGptTokens();
            }
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
        long persistenceStartMs = System.currentTimeMillis();
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
        long persistenceMs = System.currentTimeMillis() - persistenceStartMs;

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
        log.info(
                "Request Token Summary | grounded={} | gapFill={} | generalGPT={} | prompt={} | completion={} | total={}",
                groundedTokens,
                gapFillTokens,
                generalGptTokens,
                nullSafeInt(openAiUsage != null ? openAiUsage.getPromptTokens() : null),
                nullSafeInt(openAiUsage != null ? openAiUsage.getCompletionTokens() : null),
                nullSafeInt(openAiUsage != null ? openAiUsage.getTotalTokens() : null)
        );

        long totalRequestMs = System.currentTimeMillis() - requestStartMs;
        log.info(
                "Request Timing Summary | pii={}ms | route={}ms | rewrite={}ms | retrieval={}ms | grounded={}ms | gapFill={}ms | generalGPT={}ms | persistence={}ms | total={}ms",
                piiDetectionMs,
                routeDecisionMs,
                queryRewriteMs,
                retrievalMs,
                groundedMs,
                gapFillMs,
                generalGptMs,
                persistenceMs,
                totalRequestMs
        );

        return chatResponse;
    }

    /**
     * Primary Guided Search ownership gate. Must run before Python route / Lex / RAG.
     * Returns guidedHandled=true only when handleTurn owns the reply (including cancel).
     */
    private GuidedRouteAttempt tryHandleActiveGuidedTurn(ChatRequest request, String originalUserText) {
        LexFulfillmentSession fulfillmentSession = LexFulfillmentSession.of(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId()
        );
        if (request.getM3ClientReport() != null) {
            searchContextService.applyClientReport(fulfillmentSession, request.getM3ClientReport());
        }

        Optional<GuidedSearchState> guidedState = guidedSearchSessionService.find(fulfillmentSession);
        if (guidedState.isEmpty()) {
            log.debug(
                    "Guided routing gate: guidedSessionFound=false sessionId='{}'",
                    request.getSessionId()
            );
            return GuidedRouteAttempt.notHandled();
        }

        GuidedSearchState state = guidedState.get();
        log.info(
                "Guided routing gate: guidedSessionFound=true intent='{}' phase='{}' sessionId='{}'",
                state.intentName(),
                state.phase(),
                request.getSessionId()
        );
        log.info(
                "Guided routing gate: entering GuidedSearchService.handleTurn intent='{}' sessionId='{}' userTextLength={}",
                state.intentName(),
                request.getSessionId(),
                originalUserText != null ? originalUserText.length() : 0
        );

        GuidedSearchService.GuidedTurnResult turn =
                guidedSearchService.handleTurn(fulfillmentSession, state, originalUserText);
        if (turn.abandonToLex()) {
            log.info(
                    "Guided routing gate: guided requested abandonToLex=true intent='{}' sessionId='{}'",
                    state.intentName(),
                    request.getSessionId()
            );
            return GuidedRouteAttempt.notHandled();
        }

        ChatResponse response = turn.response();
        log.info(
                "Guided routing gate: guided handled request actionTaken='{}' intent='{}' sessionId='{}'",
                response != null ? response.getActionTaken() : null,
                state.intentName(),
                request.getSessionId()
        );
        return GuidedRouteAttempt.handled(response);
    }

    private LexLiveRouteResult handleLexLiveRoute(
            ChatRequest request,
            String originalUserText,
            String sanitizedUserText
    ) {
        LexFulfillmentSession fulfillmentSession = LexFulfillmentSession.of(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId()
        );
        if (request.getM3ClientReport() != null) {
            searchContextService.applyClientReport(fulfillmentSession, request.getM3ClientReport());
        }

        Optional<GuidedSearchState> guidedState = guidedSearchSessionService.find(fulfillmentSession);
        boolean guidedActive = guidedState.isPresent();
        String guidedIntent = guidedState.map(GuidedSearchState::intentName).orElse(null);
        if (guidedActive) {
            GuidedSearchService.GuidedTurnResult turn =
                    guidedSearchService.handleTurn(fulfillmentSession, guidedState.get(), originalUserText);
            if (!turn.abandonToLex()) {
                return LexLiveRouteResult.lex(turn.response());
            }
            guidedActive = false;
            guidedIntent = null;
        }

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
            LexFulfillmentOutcome fulfillmentOutcome = lexFulfillmentService.fulfillOutcome(
                    lexResult,
                    originalUserText,
                    fulfillmentSession
            );
            ChatResponse chatResponse = fulfillmentOutcome.response();
            logGuidedSearchCriteriaDecision(
                    guidedActive,
                    guidedIntent,
                    lexResult.getIntentName(),
                    fulfillmentOutcome
            );

            if (shouldClearGuidedAfterFulfillment(fulfillmentOutcome)) {
                if (guidedActive) {
                    guidedSearchSessionService.clear(fulfillmentSession);
                    log.info("Guided search: cleared after searchable criteria detected for intent='{}'", guidedIntent);
                }
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

            if (SearchCriteriaValidator.ACTION_SEARCH_CRITERIA_MISSING.equals(chatResponse.getActionTaken())
                    && isSearchIntent(lexResult.getIntentName())
                    && !SearchCriteriaValidator.hasSearchableCriteria(fulfillmentOutcome.searchCriteria())) {
                log.info(
                        "Guided search: starting zero-criteria fallback for intent='{}'",
                        lexResult.getIntentName()
                );
                ChatResponse guided = guidedSearchService.start(lexResult.getIntentName(), fulfillmentSession);
                return LexLiveRouteResult.lex(guided);
            }

            if (SearchCriteriaValidator.ACTION_SEARCH_CRITERIA_MISSING.equals(chatResponse.getActionTaken())
                    && SearchCriteriaValidator.hasSearchableCriteria(fulfillmentOutcome.searchCriteria())) {
                log.warn(
                        "Guided search: search_criteria_missing but searchable criteria present; intent='{}' criteria={}",
                        lexResult.getIntentName(),
                        fulfillmentOutcome.searchCriteria()
                );
            }

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

    private static boolean shouldClearGuidedAfterFulfillment(LexFulfillmentOutcome fulfillmentOutcome) {
        if (SearchCriteriaValidator.hasSearchableCriteria(fulfillmentOutcome.searchCriteria())) {
            return true;
        }
        ChatResponse response = fulfillmentOutcome.response();
        return response != null
                && "search".equals(response.getActionTaken())
                && response.getM3Request() != null;
    }

    private void logGuidedSearchCriteriaDecision(
            boolean guidedActive,
            String guidedIntent,
            String lexIntent,
            LexFulfillmentOutcome fulfillmentOutcome
    ) {
        List<SearchCriterion> criteria = fulfillmentOutcome.searchCriteria();
        int count = SearchCriteriaValidator.searchableCriteriaCount(criteria);
        boolean hasCriteria = SearchCriteriaValidator.hasSearchableCriteria(criteria);
        String criteriaSummary = formatCriteriaForLog(criteria);
        if (hasCriteria) {
            log.info(
                    "Guided search decision: searchableCriteriaCount={} criteria=[{}] guidedActive={} "
                            + "guidedIntent='{}' lexIntent='{}' guidedSearchSkipped=true reason=has_criteria",
                    count,
                    criteriaSummary,
                    guidedActive,
                    guidedIntent,
                    lexIntent
            );
        } else if (guidedActive) {
            log.info(
                    "Guided search decision: searchableCriteriaCount=0 guidedActive=true guidedIntent='{}' "
                            + "lexIntent='{}' guidedSearchSkipped=false reason=awaiting_criteria",
                    guidedIntent,
                    lexIntent
            );
        } else {
            log.info(
                    "Guided search decision: searchableCriteriaCount=0 lexIntent='{}' "
                            + "guidedSearchSkipped=false reason=zero_criteria_fallback",
                    lexIntent
            );
        }
    }

    private static String formatCriteriaForLog(List<SearchCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchCriterion c : criteria) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(c.field()).append('=').append(c.value());
        }
        return sb.toString();
    }

    private boolean isSearchIntent(String intentName) {
        return intentApiCatalog.find(intentName)
                .filter(def -> def.requestType() == RequestType.SEARCH)
                .isPresent();
    }

    private List<String> resolveRequestedInformationForFulfillment(
            String originalUserText,
            LexRecognizeResult lexResult,
            LexFulfillmentOutcome fulfillmentOutcome
    ) {
        if ("lex_invalid_slot".equals(fulfillmentOutcome.response().getActionTaken())) {
            return List.of();
        }

        if (fulfillmentOutcome.queryContext() != null
                && !fulfillmentOutcome.queryContext().requestedInformation().isEmpty()) {
            return fulfillmentOutcome.queryContext().requestedInformation();
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
        long queryRewriteMs = 0L;
        long retrievalStageMs = 0L;
        long groundedMs = 0L;
        long gapFillMs = 0L;
        long generalGptMs = 0L;
        int groundedTokens = 0;
        int gapFillTokens = 0;
        int generalGptTokens = 0;
        PythonQueryRequest ragRequest = new PythonQueryRequest();
        ragRequest.setMessage(sanitizedUserText);
        ragRequest.setHistory(request.getHistory());

        List<String> rewrittenQueries = List.of();
        OpenAIUsage rewriteUsage = null;
        if (queryRewriteEnabled) {
            long rewriteStartMs = System.currentTimeMillis();
            QueryRewriteResult rewriteResult = openAIService.rewriteQueries(sanitizedUserText);
            queryRewriteMs = System.currentTimeMillis() - rewriteStartMs;
            rewrittenQueries = rewriteResult.queries();
            rewriteUsage = rewriteResult.usage();
        }
        List<String> searchQueries = SearchQueryAssembler.assemble(sanitizedUserText, rewrittenQueries, 4);

        List<String> boostProgramIds = ProgramIdDetector.detect(sanitizedUserText, originalUserText);
        log.info(
                "Doc retrieval program boost: detectedProgramIds={}",
                boostProgramIds.isEmpty() ? "none" : boostProgramIds
        );

        PythonRetrievalResponse retrieval;
        try {
            long retrievalStartMs = System.currentTimeMillis();
            retrieval = pythonRagService.retrieve(
                    sanitizedUserText,
                    searchQueries,
                    ragRequest,
                    boostProgramIds
            );
            retrievalStageMs = System.currentTimeMillis() - retrievalStartMs;
        } catch (OpenAIException e) {
            log.warn(
                    "Python retrieval call failed (status={}), falling back to OpenAI: {}",
                    e.getStatusCode(),
                    e.getMessage()
            );
            long generalStartMs = System.currentTimeMillis();
            ChatResponse chatResponse = openAIService.chatWithoutPersistence(request);
            generalGptMs = System.currentTimeMillis() - generalStartMs;
            logUsage("General", chatResponse.getOpenAiUsage(), generalGptMs);
            generalGptTokens = nullSafeInt(chatResponse.getOpenAiUsage() != null ? chatResponse.getOpenAiUsage().getTotalTokens() : null);
            if (rewriteUsage != null) {
                chatResponse.setOpenAiUsage(mergeUsage(rewriteUsage, chatResponse.getOpenAiUsage()));
            }
            return new DocRouteResult(
                    chatResponse,
                    List.of(),
                    List.of(),
                    "retrieval_error",
                    null,
                    null,
                    (int) queryRewriteMs,
                    (int) retrievalStageMs,
                    0,
                    0,
                    (int) generalGptMs,
                    groundedTokens,
                    gapFillTokens,
                    generalGptTokens
            );
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
        log.info(
                "Retrieval Summary | chunksRetrieved={} | chunksSentToPrompt={} | maxScore={} | retrievalReason={}",
                retrieval.getTotal() != null ? retrieval.getTotal() : 0,
                retrieval.getPromptChunkCount() != null ? retrieval.getPromptChunkCount() : promptChunks.size(),
                retrieval.getMaxScore(),
                reason
        );
        List<SourceItem> responseSources = toChunkSources(promptChunks);

        ChatResponse chatResponse;
        if (RETRIEVAL_READY.equals(reason)) {
            try {
                long groundedStartMs = System.currentTimeMillis();
                GroundedRagCallResult groundedCall = openAIService.chatWithRagContext(request, promptChunks);
                groundedMs = System.currentTimeMillis() - groundedStartMs;
                logUsage("Grounded", groundedCall.usage(), groundedMs);
                groundedTokens = nullSafeInt(groundedCall.usage() != null ? groundedCall.usage().getTotalTokens() : null);
                OpenAIUsage groundedUsage = groundedCall.usage();
                if (rewriteUsage != null) {
                    groundedUsage = mergeUsage(rewriteUsage, groundedUsage);
                }

                GroundedRagResult grounded = groundedCall.grounded();
                log.info(
                        "Grounded Response Parsed | status={} | missingTopics={}",
                        grounded.getStatus(),
                        grounded.getMissingTopics() != null ? grounded.getMissingTopics().size() : 0
                );
                log.debug("Grounded Response (raw): {}", groundedCall.rawContent());

                DocAnswerOutcome outcome = orchestrateGroundedStatus(request, grounded, groundedUsage);
                chatResponse = outcome.chatResponse();
                reason = outcome.retrievalReason();
                gapFillMs = outcome.gapFillTimeMs();
                generalGptMs = outcome.generalGptTimeMs();
                gapFillTokens = outcome.gapFillTokens();
                generalGptTokens = outcome.generalGptTokens();
                logUsage("Merged", chatResponse.getOpenAiUsage(), 0);
            } catch (OpenAIException e) {
                log.warn("Grounded JSON parsing failed, falling back to General GPT: {}", e.getMessage());
                long generalStartMs = System.currentTimeMillis();
                chatResponse = openAIService.chatWithoutPersistence(request);
                generalGptMs = System.currentTimeMillis() - generalStartMs;
                logUsage("General", chatResponse.getOpenAiUsage(), generalGptMs);
                generalGptTokens = nullSafeInt(chatResponse.getOpenAiUsage() != null ? chatResponse.getOpenAiUsage().getTotalTokens() : null);
                if (rewriteUsage != null) {
                    chatResponse.setOpenAiUsage(mergeUsage(rewriteUsage, chatResponse.getOpenAiUsage()));
                }
                reason = RETRIEVAL_RAG_NO_ANSWER;
                log.info(
                        "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={}",
                        "PARSE_FAILED",
                        reason,
                        true,
                        false,
                        true,
                        chatResponse.getActionTaken()
                );
            }
        } else {
            if (retrieval.getError() != null) {
                log.warn("Retrieval error from Python, falling back to OpenAI: {}", retrieval.getError());
            }
            long generalStartMs = System.currentTimeMillis();
            chatResponse = openAIService.chatWithoutPersistence(request);
            generalGptMs = System.currentTimeMillis() - generalStartMs;
            logUsage("General", chatResponse.getOpenAiUsage(), generalGptMs);
            generalGptTokens = nullSafeInt(chatResponse.getOpenAiUsage() != null ? chatResponse.getOpenAiUsage().getTotalTokens() : null);
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
                retrieval.getMaxScore(),
                (int) queryRewriteMs,
                retrieval.getRetrievalTimeMs() != null ? retrieval.getRetrievalTimeMs() : (int) retrievalStageMs,
                (int) groundedMs,
                (int) gapFillMs,
                (int) generalGptMs,
                groundedTokens,
                gapFillTokens,
                generalGptTokens
        );
    }

    private DocAnswerOutcome orchestrateGroundedStatus(
            ChatRequest request,
            GroundedRagResult grounded,
            OpenAIUsage usageSoFar
    ) {
        RagStatus status = grounded.getStatus() != null ? grounded.getStatus() : RagStatus.INSUFFICIENT;
        String docAnswer = grounded.getAnswer() != null ? grounded.getAnswer() : "";

        if (status == RagStatus.FULL) {
            ChatResponse response = buildRagChatResponse(request, docAnswer, usageSoFar);
            log.info(
                    "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={}",
                    "FULL",
                    RETRIEVAL_READY,
                    true,
                    false,
                    false,
                    response.getActionTaken()
            );
            return new DocAnswerOutcome(response, RETRIEVAL_READY, 0, 0, 0, 0);
        }

        if (status == RagStatus.PARTIAL) {
            List<String> missingTopics = grounded.getMissingTopics() != null
                    ? grounded.getMissingTopics()
                    : List.of();
            if (ragPartialGapFillEnabled && !missingTopics.isEmpty()) {
                long gapFillStartMs = System.currentTimeMillis();
                ChatResponse gapFill = openAIService.chatGapFill(request, docAnswer, missingTopics);
                int gapFillTimeMs = (int) (System.currentTimeMillis() - gapFillStartMs);
                logUsage("GapFill", gapFill.getOpenAiUsage(), gapFillTimeMs);
                int gapFillTokens = nullSafeInt(gapFill.getOpenAiUsage() != null ? gapFill.getOpenAiUsage().getTotalTokens() : null);
                String combined = "## Documentation\n\n"
                        + docAnswer.strip()
                        + "\n\n## Additional AI Information\n\n"
                        + (gapFill.getReply() != null ? gapFill.getReply().strip() : "");
                ChatResponse response = buildRagChatResponse(request, combined, mergeUsage(usageSoFar, gapFill.getOpenAiUsage()));
                log.info(
                        "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={} | missingTopics={}",
                        "PARTIAL",
                        RETRIEVAL_READY,
                        true,
                        true,
                        false,
                        response.getActionTaken(),
                        missingTopics.size()
                );
                return new DocAnswerOutcome(
                        response,
                        RETRIEVAL_READY,
                        gapFillTimeMs,
                        0,
                        gapFillTokens,
                        0
                );
            }
            ChatResponse response = buildRagChatResponse(request, docAnswer, usageSoFar);
            log.info(
                    "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={} | missingTopics={}",
                    "PARTIAL",
                    RETRIEVAL_READY,
                    true,
                    false,
                    false,
                    response.getActionTaken(),
                    missingTopics.size()
            );
            return new DocAnswerOutcome(response, RETRIEVAL_READY, 0, 0, 0, 0);
        }

        long generalStartMs = System.currentTimeMillis();
        ChatResponse general = openAIService.chatWithoutPersistence(request);
        int generalTimeMs = (int) (System.currentTimeMillis() - generalStartMs);
        logUsage("General", general.getOpenAiUsage(), generalTimeMs);
        general.setOpenAiUsage(mergeUsage(usageSoFar, general.getOpenAiUsage()));
        int generalTokens = nullSafeInt(general.getOpenAiUsage() != null ? general.getOpenAiUsage().getTotalTokens() : null);
        log.info(
                "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={}",
                "INSUFFICIENT",
                RETRIEVAL_RAG_NO_ANSWER,
                true,
                false,
                true,
                general.getActionTaken()
        );
        return new DocAnswerOutcome(general, RETRIEVAL_RAG_NO_ANSWER, 0, generalTimeMs, 0, generalTokens);
    }

    private ChatResponse buildRagChatResponse(ChatRequest request, String reply, OpenAIUsage usage) {
        ChatResponse chatResponse = new ChatResponse(reply != null ? reply : "", false);
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken("rag");
        chatResponse.setOpenAiUsage(usage);
        return chatResponse;
    }

    private void logUsage(String stage, OpenAIUsage usage, long latencyMs) {
        log.info(
                "OpenAI Usage | stage={} | model={} | promptTokens={} | completionTokens={} | totalTokens={} | latency={}ms",
                stage,
                usage != null ? usage.getModel() : "unknown",
                nullSafeInt(usage != null ? usage.getPromptTokens() : null),
                nullSafeInt(usage != null ? usage.getCompletionTokens() : null),
                nullSafeInt(usage != null ? usage.getTotalTokens() : null),
                latencyMs
        );
    }

    private record DocAnswerOutcome(
            ChatResponse chatResponse,
            String retrievalReason,
            int gapFillTimeMs,
            int generalGptTimeMs,
            int gapFillTokens,
            int generalGptTokens
    ) {
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
            Float maxScore,
            Integer queryRewriteTimeMs,
            Integer retrievalStageTimeMs,
            Integer groundedTimeMs,
            Integer gapFillTimeMs,
            Integer generalGptTimeMs,
            Integer groundedTokens,
            Integer gapFillTokens,
            Integer generalGptTokens
    ) {
    }

    private record GuidedRouteAttempt(
            boolean guidedHandled,
            ChatResponse response
    ) {
        static GuidedRouteAttempt handled(ChatResponse response) {
            return new GuidedRouteAttempt(true, response);
        }

        static GuidedRouteAttempt notHandled() {
            return new GuidedRouteAttempt(false, null);
        }
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
