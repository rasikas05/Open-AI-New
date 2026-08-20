package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.TenantQuotaExceededException;
import com.ai.openai_api_service.exception.AiServiceErrors;
import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.BusinessProtectedEntityDto;
import com.ai.openai_api_service.model.ChatMode;
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
import com.ai.openai_api_service.model.RequestUnderstandResult;
import com.ai.openai_api_service.model.RequestUnderstandType;
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
import com.ai.openai_api_service.service.lex.InMemoryPendingLexSessionService;
import com.ai.openai_api_service.service.protection.BusinessInformationProtectionService;
import com.ai.openai_api_service.service.protection.BusinessPlaceholderRestorer;
import com.ai.openai_api_service.service.protection.PiiProtectionService;
import com.ai.openai_api_service.service.protection.ProtectionAction;
import com.ai.openai_api_service.service.protection.ProtectionContext;
import com.ai.openai_api_service.service.protection.ProtectionPurpose;
import com.ai.openai_api_service.service.protection.ProtectionSession;
import com.ai.openai_api_service.service.query.SearchContextService;
import com.ai.openai_api_service.service.rag.ProgramIdDetector;
import com.ai.openai_api_service.service.rag.SearchQueryAssembler;
import com.ai.openai_api_service.service.timing.ChatRequestSummaryLog;
import com.ai.openai_api_service.service.timing.RoutingCallTracker;
import com.ai.openai_api_service.service.timing.RoutingSummaryLog;
import com.ai.openai_api_service.service.timing.RoutingSummaryState;
import com.ai.openai_api_service.service.timing.ComprehendChatTimingSnapshot;
import com.ai.openai_api_service.service.timing.RequestTimingLog;
import com.ai.openai_api_service.service.validation.SearchCriteriaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
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

    static final String DOCS_PARTIAL_CONTINUATION =
            "For the remaining details, please try another combination or clarify your request, "
                    + "and I'll help you with the available information.";

    static final String DOCS_INSUFFICIENT_MESSAGE =
            "I couldn't find enough information to answer your request. Please try another combination "
                    + "or clarify your request, and I'll help you with the available information.";

    static final String DEFAULT_M3_LIVE_STEER_MESSAGE =
            "I'm your Infor M3 live assistant. I can help retrieve tenant data such as customer and order details. "
                    + "For M3 documentation or how-to questions, switch to Auto or Docs.";

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
    private final InMemoryPendingLexSessionService pendingLexSessionService;
    private final BusinessInformationProtectionService businessInformationProtectionService;
    private final PiiProtectionService piiProtectionService;
    private final BusinessPlaceholderRestorer businessPlaceholderRestorer;

    @Value("${openai.response.include-sanitization-debug:false}")
    private boolean includeSanitizationDebug;

    @Value("${rag.query-rewrite.enabled:false}")
    private boolean queryRewriteEnabled;

    @Value("${chat.request-router.enabled:false}")
    private boolean requestRouterEnabled;

    @Value("${chat.m3.live-steer-message:" + DEFAULT_M3_LIVE_STEER_MESSAGE + "}")
    private String m3LiveSteerMessage;

    @Value("${rag.partial.gap-fill.enabled:true}")
    private boolean ragPartialGapFillEnabled;

    @Value("${openai.api.model:}")
    private String openAiModel;

    public ComprehendChatService(
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
            InMemoryGuidedSearchSessionService guidedSearchSessionService,
            InMemoryPendingLexSessionService pendingLexSessionService,
            BusinessInformationProtectionService businessInformationProtectionService,
            PiiProtectionService piiProtectionService,
            BusinessPlaceholderRestorer businessPlaceholderRestorer
    ) {
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
        this.pendingLexSessionService = pendingLexSessionService;
        this.businessInformationProtectionService = businessInformationProtectionService;
        this.piiProtectionService = piiProtectionService;
        this.businessPlaceholderRestorer = businessPlaceholderRestorer;
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null || request.getUserMessage() == null || request.getUserMessage().isBlank()) {
            throw new OpenAIException("User message cannot be empty", 400);
        }
        Instant serviceStart = Instant.now();
        long requestStartMs = serviceStart.toEpochMilli();
        long piiDetectionMs = 0L;
        long routeDecisionMs = 0L;
        long queryRewriteMs = 0L;
        long retrievalMs = 0L;
        long retrievalPythonMs = 0L;
        long groundedMs = 0L;
        long gapFillMs = 0L;
        long generalGptMs = 0L;
        long restoreMs = 0L;
        long liveHistoryMs = 0L;
        long suggestionsMs = 0L;
        long persistenceMs = 0L;
        long preRetrievalGlueMs = 0L;
        int groundedTokens = 0;
        int gapFillTokens = 0;
        int generalGptTokens = 0;
        int routerPromptTokens = 0;
        int routerCompletionTokens = 0;
        int suggestionPromptTokens = 0;
        int suggestionCompletionTokens = 0;

        ComprehendChatTimingSnapshot timingSnapshot = currentTimingSnapshot();
        if (timingSnapshot != null) {
            timingSnapshot.setServiceStart(serviceStart);
        }

        TenantQuotaService.QuotaCheckResult quotaCheck = tenantQuotaService.checkBeforeChat(request.getTenantCode());
        if (!quotaCheck.allowed()) {
            markServiceEnd(timingSnapshot, Instant.now(), requestStartMs);
            return blockedQuotaResponse(quotaCheck);
        }

        chatPersistenceService.enforceSessionRequestLimit(
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId()
        );

        Long editOfRequestLogId = request.getEditOfRequestLogId();
        Long editSessionPk = null;
        if (editOfRequestLogId != null) {
            editSessionPk = chatPersistenceService.validateLatestActiveEdit(
                    request.getTenantCode(),
                    request.getUserId(),
                    request.getSessionId(),
                    editOfRequestLogId
            );
        }

        ChatMode resolvedMode = resolveMode(request);

        String originalUserText = request.getUserMessage();
        String sanitizedUserText = null;
        ChatRequest workingRequest = request;
        ProtectionSession protectionSession = null;

        log.info(
                "ComprehendChatService.chat tenantCode={}, userId={}, sessionId={}, mode={}, editOfRequestLogId={}, originalLength={}",
                request.getTenantCode(),
                request.getUserId(),
                request.getSessionId(),
                resolvedMode,
                editOfRequestLogId,
                originalUserText != null ? originalUserText.length() : 0
        );

        RoutingSummaryState routingSummary = new RoutingSummaryState();
        routingSummary.setRequestText(originalUserText);
        routingSummary.setMode(resolvedMode);
        ChatResponse chatResponse = null;
        String route = null;
        RoutingCallTracker.begin();
        try {
        boolean guidedHandled = false;
        List<SourceItem> sourcesForSuggestions = null;
        List<SourceItem> responseSources = null;
        String retrievalReason = null;
        Integer retrievalTimeMs = null;
        Float maxScore = null;

        GuidedRouteAttempt guidedAttempt = tryHandleActiveGuidedTurn(request, originalUserText);
        if (guidedAttempt.guidedHandled()) {
            guidedHandled = true;
            chatResponse = guidedAttempt.response();
            route = "guided";
            routingSummary.setRouter("skipped (guided)");
            routingSummary.setRoute("guided");
            routingSummary.setHandler("guided-search");
            long piiStartMs = System.currentTimeMillis();
            sanitizedUserText = anonymizeForPersistence(originalUserText);
            piiDetectionMs = System.currentTimeMillis() - piiStartMs;
        }

        boolean pendingLexHandled = false;
        if (!guidedHandled) {
            String sanitizedForPending = originalUserText;
            String lexSessionId = lexService.buildLexSessionId(request);
            if (lexService.isEnabled() && pendingLexSessionService.get(lexSessionId).isPresent()) {
                long piiStartMs = System.currentTimeMillis();
                sanitizedForPending = anonymizeForPersistence(originalUserText);
                piiDetectionMs = System.currentTimeMillis() - piiStartMs;
                sanitizedUserText = sanitizedForPending;
            }
            PendingLexRouteAttempt pendingLexAttempt =
                    tryHandlePendingLexTurn(request, originalUserText, sanitizedForPending);
            if (pendingLexAttempt.pendingHandled()) {
                pendingLexHandled = true;
                LexLiveRouteResult lexResult = pendingLexAttempt.lexResult();
                chatResponse = lexResult.chatResponse();
                route = "live";
                routingSummary.setRouter("skipped (pending-lex)");
                routingSummary.setRoute("live");
                routingSummary.setHandler("lex-pending");
                if (lexResult.fallbackToDoc()) {
                    sourcesForSuggestions = lexResult.sourcesForSuggestions();
                    responseSources = lexResult.responseSources();
                    retrievalReason = lexResult.retrievalReason();
                    retrievalTimeMs = lexResult.retrievalTimeMs();
                    maxScore = lexResult.maxScore();
                }
            }
        }

        if (!guidedHandled && !pendingLexHandled) {
            ChatMode mode = resolvedMode;
            Instant routeStart = Instant.now();
            RequestUnderstandResult understood = null;
            boolean routerHandled = false;

            if (mode == ChatMode.M3) {
                if (requestRouterEnabled) {
                    UnderstandRun understandRun = runUnderstandRequest(request, originalUserText);
                    protectionSession = understandRun.protectionSession();
                    sanitizedUserText = understandRun.sanitizedUserText();
                    workingRequest = understandRun.workingRequest();
                    piiDetectionMs = understandRun.piiDetectionMs();
                    routeDecisionMs = understandRun.routeDecisionMs();
                    understood = understandRun.understood();
                    recordUnderstandResult(routingSummary, understood);
                    if (understood != null && understood.usage() != null) {
                        routerPromptTokens = nullSafeInt(understood.usage().getPromptTokens());
                        routerCompletionTokens = nullSafeInt(understood.usage().getCompletionTokens());
                    }
                    if (understood != null) {
                        RequestUnderstandType type = understood.type();
                        if (type == RequestUnderstandType.CONVERSATIONAL) {
                            chatResponse = buildRouterUserResponse(
                                    workingRequest, understood, "conversational");
                            route = "conversational";
                            routerHandled = true;
                            routingSummary.setRoute("conversational");
                            routingSummary.setHandler("request-router");
                        } else if (type == RequestUnderstandType.RAG || type == RequestUnderstandType.NON_M3) {
                            log.info("routerType={} overriddenByMode=m3", type);
                            chatResponse = buildM3LiveSteerResponse(workingRequest, understood);
                            route = "m3_live_steer";
                            routerHandled = true;
                            routingSummary.setOverride(type.name() + " -> m3_live_steer");
                            routingSummary.setRoute("m3_live_steer");
                            routingSummary.setHandler("request-router");
                        } else {
                            route = ROUTE_LIVE;
                            routingSummary.setRoute(ROUTE_LIVE);
                            routingSummary.setHandler(lexService.isEnabled() ? "lex" : "live/python-chat");
                        }
                    } else {
                        route = ROUTE_LIVE;
                        routingSummary.setRoute(ROUTE_LIVE);
                        routingSummary.setHandler(lexService.isEnabled() ? "lex" : "live/python-chat");
                    }
                } else {
                    route = ROUTE_LIVE;
                    routingSummary.setRouter("skipped (m3-hard-live)");
                    routingSummary.setRoute(ROUTE_LIVE);
                    routingSummary.setHandler(lexService.isEnabled() ? "lex" : "live/python-chat");
                }
            } else if (requestRouterEnabled) {
                UnderstandRun understandRun = runUnderstandRequest(request, originalUserText);
                protectionSession = understandRun.protectionSession();
                sanitizedUserText = understandRun.sanitizedUserText();
                workingRequest = understandRun.workingRequest();
                piiDetectionMs = understandRun.piiDetectionMs();
                routeDecisionMs = understandRun.routeDecisionMs();
                understood = understandRun.understood();
                recordUnderstandResult(routingSummary, understood);
                if (understood != null && understood.usage() != null) {
                    routerPromptTokens = nullSafeInt(understood.usage().getPromptTokens());
                    routerCompletionTokens = nullSafeInt(understood.usage().getCompletionTokens());
                }

                if (understood != null) {
                    RequestUnderstandType type = understood.type();
                    if (mode == ChatMode.DOCS && type == RequestUnderstandType.LIVE_M3) {
                        log.info("routerType=LIVE_M3 overriddenByMode=docs");
                        routingSummary.setOverride("LIVE_M3 -> RAG");
                        type = RequestUnderstandType.RAG;
                    }
                    switch (type) {
                        case CONVERSATIONAL -> {
                            chatResponse = buildRouterUserResponse(
                                    workingRequest, understood, "conversational");
                            route = "conversational";
                            routerHandled = true;
                            routingSummary.setRoute("conversational");
                            routingSummary.setHandler("request-router");
                        }
                        case NON_M3 -> {
                            if (allowExternalFallback(request)) {
                                chatResponse = buildRouterUserResponse(
                                        workingRequest, understood, "general_redirect");
                                route = "general_redirect";
                                routingSummary.setRoute("general_redirect");
                            } else {
                                chatResponse = buildDocsOnlyInsufficientResponse(
                                        workingRequest, understood.usage());
                                route = "rag";
                                routingSummary.setRoute("rag");
                            }
                            routingSummary.setHandler("request-router");
                            routerHandled = true;
                        }
                        case LIVE_M3 -> {
                            route = ROUTE_LIVE;
                            routingSummary.setRoute(ROUTE_LIVE);
                            routingSummary.setHandler(lexService.isEnabled() ? "lex" : "live/python-chat");
                        }
                        case RAG -> {
                            route = "rag";
                            routingSummary.setRoute("rag");
                            routingSummary.setHandler("documentation/retrieval");
                            DocRouteResult docResult = handleDocumentationRoute(
                                    workingRequest,
                                    originalUserText,
                                    protectionSession,
                                    understood.queries() != null ? understood.queries() : List.of(),
                                    understood.usage()
                            );
                            chatResponse = docResult.chatResponse();
                            sourcesForSuggestions = docResult.sourcesForSuggestions();
                            responseSources = docResult.responseSources();
                            retrievalReason = docResult.retrievalReason();
                            retrievalTimeMs = docResult.retrievalTimeMs();
                            maxScore = docResult.maxScore();
                            queryRewriteMs = nullSafeInt(docResult.queryRewriteTimeMs());
                            retrievalMs = nullSafeInt(docResult.retrievalSpringMs());
                            retrievalPythonMs = nullSafeInt(docResult.retrievalPythonMs());
                            preRetrievalGlueMs = nullSafeInt(docResult.preRetrievalGlueMs());
                            groundedMs = nullSafeInt(docResult.groundedTimeMs());
                            gapFillMs = nullSafeInt(docResult.gapFillTimeMs());
                            generalGptMs = nullSafeInt(docResult.generalGptTimeMs());
                            groundedTokens = docResult.groundedTokens();
                            gapFillTokens = docResult.gapFillTokens();
                            generalGptTokens = docResult.generalGptTokens();
                            routerHandled = true;
                        }
                    }
                } else if (mode == ChatMode.DOCS) {
                    route = "rag";
                    routingSummary.setRoute("rag");
                    routingSummary.setHandler("documentation/retrieval");
                } else {
                    PythonRouteResponse routeResponse = pythonRagService.route(originalUserText);
                    route = routeResponse != null ? routeResponse.getRoute() : "rag";
                    routingSummary.setRoute(route);
                    routingSummary.setHandler(ROUTE_LIVE.equalsIgnoreCase(route)
                            ? (lexService.isEnabled() ? "lex" : "live/python-chat")
                            : "documentation/retrieval");
                }
            } else if (mode == ChatMode.DOCS) {
                route = "rag";
                routingSummary.setRouter("skipped (router-disabled)");
                routingSummary.setRoute("rag");
                routingSummary.setHandler("documentation/retrieval");
            } else {
                PythonRouteResponse routeResponse = pythonRagService.route(originalUserText);
                route = routeResponse != null ? routeResponse.getRoute() : "rag";
                routingSummary.setRouter("skipped (router-disabled)");
                routingSummary.setRoute(route);
                routingSummary.setHandler(ROUTE_LIVE.equalsIgnoreCase(route)
                        ? (lexService.isEnabled() ? "lex" : "live/python-chat")
                        : "documentation/retrieval");
            }
            Instant routeEnd = Instant.now();
            if (routeDecisionMs == 0L) {
                routeDecisionMs = RequestTimingLog.durationMs(routeStart, routeEnd);
            }
            RequestTimingLog.logStage("route", routeStart, routeEnd);
            log.info(
                    "Comprehend route decision: mode='{}', route='{}', handler='{}', originalLength={}",
                    mode,
                    route,
                    ROUTE_LIVE.equalsIgnoreCase(route)
                            ? (lexService.isEnabled() ? "live/lex" : "live/python-chat")
                            : (routerHandled ? "request-router" : "documentation/retrieval"),
                    originalUserText != null ? originalUserText.length() : 0
            );

            if (routerHandled) {
                if (chatResponse != null && protectionSession != null) {
                    Instant restoreStart = Instant.now();
                    String restored = businessPlaceholderRestorer.restoreIntoSession(
                            chatResponse.getReply(),
                            protectionSession
                    );
                    chatResponse.setReply(restored);
                    chatResponse.setReplyBeforeRestore(protectionSession.replyBeforeRestore());
                    applyProtectionSessionToResponse(chatResponse, protectionSession);
                    Instant restoreEnd = Instant.now();
                    restoreMs = RequestTimingLog.durationMs(restoreStart, restoreEnd);
                    RequestTimingLog.logStage("restore", restoreStart, restoreEnd);
                }
            } else if (ROUTE_LIVE.equalsIgnoreCase(route)) {
                if (sanitizedUserText == null) {
                    Instant piiStart = Instant.now();
                    sanitizedUserText = anonymizeForPersistence(originalUserText);
                    Instant piiEnd = Instant.now();
                    piiDetectionMs = RequestTimingLog.durationMs(piiStart, piiEnd);
                    RequestTimingLog.logStage("pii", piiStart, piiEnd);
                }
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
                    chatResponse = handleLiveRoute(request, originalUserText);
                    routingSummary.setHandler("live/python-chat");
                    routingSummary.setRoute(ROUTE_LIVE);
                }
            } else {
                routingSummary.setRoute("rag");
                routingSummary.setHandler("documentation/retrieval");
                Instant piiStart = Instant.now();
                protectionSession = ProtectionSession.fromOriginal(
                        originalUserText,
                        businessInformationProtectionService.isEnabled()
                );
                businessInformationProtectionService.protect(
                        protectionSession,
                        ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true)
                );
                protectPiiSafely(protectionSession);
                Instant piiEnd = Instant.now();
                piiDetectionMs = RequestTimingLog.durationMs(piiStart, piiEnd);
                RequestTimingLog.logStage("pii", piiStart, piiEnd);

                String beforePii = protectionSession.businessProtectedText() != null
                        ? protectionSession.businessProtectedText()
                        : protectionSession.originalText();
                boolean piiChanged = !Objects.equals(beforePii, protectionSession.piiSanitizedText());
                int entityCount = protectionSession.actions() != null
                        ? protectionSession.actions().size()
                        : 0;
                log.info(
                        "Protection stage | businessApplied={} | entityCount={} | piiChanged={}",
                        protectionSession.businessProtectionApplied(),
                        entityCount,
                        piiChanged
                );

                String llmText = protectionSession.textForLlm();
                sanitizedUserText = llmText;
                workingRequest = copyRequestWithUserMessage(request, llmText);
                DocRouteResult docResult = handleDocumentationRoute(
                        workingRequest,
                        originalUserText,
                        protectionSession
                );
                chatResponse = docResult.chatResponse();
                sourcesForSuggestions = docResult.sourcesForSuggestions();
                responseSources = docResult.responseSources();
                retrievalReason = docResult.retrievalReason();
                retrievalTimeMs = docResult.retrievalTimeMs();
                maxScore = docResult.maxScore();
                queryRewriteMs = nullSafeInt(docResult.queryRewriteTimeMs());
                retrievalMs = nullSafeInt(docResult.retrievalSpringMs());
                retrievalPythonMs = nullSafeInt(docResult.retrievalPythonMs());
                preRetrievalGlueMs = nullSafeInt(docResult.preRetrievalGlueMs());
                groundedMs = nullSafeInt(docResult.groundedTimeMs());
                gapFillMs = nullSafeInt(docResult.gapFillTimeMs());
                generalGptMs = nullSafeInt(docResult.generalGptTimeMs());
                groundedTokens = docResult.groundedTokens();
                gapFillTokens = docResult.gapFillTokens();
                generalGptTokens = docResult.generalGptTokens();

                if (chatResponse != null) {
                    Instant restoreStart = Instant.now();
                    String restored = businessPlaceholderRestorer.restoreIntoSession(
                            chatResponse.getReply(),
                            protectionSession
                    );
                    chatResponse.setReply(restored);
                    chatResponse.setReplyBeforeRestore(protectionSession.replyBeforeRestore());
                    applyProtectionSessionToResponse(chatResponse, protectionSession);
                    Instant restoreEnd = Instant.now();
                    restoreMs = RequestTimingLog.durationMs(restoreStart, restoreEnd);
                    RequestTimingLog.logStage("restore", restoreStart, restoreEnd);
                }
            }
        }

        if (sanitizedUserText == null) {
            sanitizedUserText = originalUserText;
        }

        if (Boolean.TRUE.equals(chatResponse.getLimitExceeded())) {
            markServiceEnd(timingSnapshot, Instant.now(), requestStartMs);
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
            markServiceEnd(timingSnapshot, Instant.now(), requestStartMs);
            return blockedQuotaExceptionResponse(e);
        }

        boolean sanitizedFlag = !Objects.equals(originalUserText, sanitizedUserText);
        Instant liveHistoryStart = Instant.now();
        LiveHistoryResult liveHistory = liveHistorySummaryBuilder.build(chatResponse).orElse(null);
        Instant liveHistoryEnd = Instant.now();
        liveHistoryMs = RequestTimingLog.durationMs(liveHistoryStart, liveHistoryEnd);
        RequestTimingLog.logStage("liveHistory", liveHistoryStart, liveHistoryEnd);
        String replyForPersistence = liveHistory != null
                ? liveHistory.summaryText()
                : chatResponse.getReply();
        LiveHistoryAuditMetadata auditMetadata = liveHistory != null
                ? liveHistory.auditMetadata()
                : null;
        Instant persistenceStart = Instant.now();
        Long requestLogId = chatPersistenceService.persistChat(
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
                auditMetadata,
                protectionSession != null
                        ? com.ai.openai_api_service.service.protection.ProtectionAuditSnapshot.fromSession(
                        protectionSession,
                        chatResponse.getReplyBeforeRestore(),
                        replyForPersistence
                )
                        : null,
                resolvedMode
        );
        if (editOfRequestLogId != null && requestLogId != null && editSessionPk != null) {
            boolean superseded = chatPersistenceService.supersedeEditedRequest(
                    editOfRequestLogId,
                    requestLogId,
                    editSessionPk
            );
            if (!superseded) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Request was already edited by a concurrent request"
                );
            }
        }
        chatResponse.setRequestLogId(requestLogId);
        Instant persistenceEnd = Instant.now();
        persistenceMs = RequestTimingLog.durationMs(persistenceStart, persistenceEnd);
        RequestTimingLog.logStage("persistence", persistenceStart, persistenceEnd);

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

        Instant suggestionsStart = Instant.now();
        SuggestionContext context = buildSuggestionContext(workingRequest, chatResponse.getReply(), sourcesForSuggestions);
        SuggestionResult suggestionResult = suggestionEngineService.generateSuggestions(context);
        chatResponse.setSuggestions(suggestionResult.getSuggestions());
        chatResponse.setSuggestionDetails(suggestionResult.getDetails());
        suggestionPromptTokens = suggestionResult.getPromptTokens();
        suggestionCompletionTokens = suggestionResult.getCompletionTokens();
        Instant suggestionsEnd = Instant.now();
        suggestionsMs = RequestTimingLog.durationMs(suggestionsStart, suggestionsEnd);
        RequestTimingLog.logStage("suggestions", suggestionsStart, suggestionsEnd);

        log.debug(
                "Spring chat complete | session={} | route={} | action={} | retrievalReason={} | collecting={} | tokens={}",
                request.getSessionId(),
                route,
                chatResponse.getActionTaken(),
                retrievalReason,
                chatResponse.getCollectingTool(),
                consumedTokens
        );
        log.debug(
                "Request Token Summary | routerPrompt={} | routerCompletion={} | grounded={} | gapFill={} | generalGPT={} | "
                        + "prompt={} | completion={} | total={}",
                routerPromptTokens,
                routerCompletionTokens,
                groundedTokens,
                gapFillTokens,
                generalGptTokens,
                nullSafeInt(openAiUsage != null ? openAiUsage.getPromptTokens() : null),
                nullSafeInt(openAiUsage != null ? openAiUsage.getCompletionTokens() : null),
                nullSafeInt(openAiUsage != null ? openAiUsage.getTotalTokens() : null)
        );

        Instant serviceEnd = Instant.now();
        long totalRequestMs = RequestTimingLog.durationMs(serviceStart, serviceEnd);
        long httpTaxMs = Math.max(0L, retrievalMs - retrievalPythonMs);
        log.debug(
                "Retrieval Clocks | springHttpMs={} | pythonInternalMs={} | httpTaxMs={}",
                retrievalMs,
                retrievalPythonMs,
                httpTaxMs
        );
        log.debug(
                "Request Timing Summary | pii={}ms | route={}ms | rewrite={}ms | retrieval={}ms | grounded={}ms | "
                        + "gapFill={}ms | generalGPT={}ms | persistence={}ms | suggestions={}ms | liveHistory={}ms | "
                        + "restore={}ms | preRetrievalGlue={}ms | total={}ms | totalScope=serviceWall",
                piiDetectionMs,
                routeDecisionMs,
                queryRewriteMs,
                retrievalMs,
                groundedMs,
                gapFillMs,
                generalGptMs,
                persistenceMs,
                suggestionsMs,
                liveHistoryMs,
                restoreMs,
                preRetrievalGlueMs,
                totalRequestMs
        );

        long measuredSumService = piiDetectionMs
                + routeDecisionMs
                + queryRewriteMs
                + retrievalMs
                + groundedMs
                + gapFillMs
                + generalGptMs
                + persistenceMs
                + suggestionsMs
                + liveHistoryMs
                + restoreMs
                + preRetrievalGlueMs;
        RequestTimingLog.logResidual(
                RequestTimingLog.computeResidual(measuredSumService, totalRequestMs),
                "serviceWall"
        );

        if (timingSnapshot != null) {
            timingSnapshot.setPiiMs(piiDetectionMs);
            timingSnapshot.setRouteMs(routeDecisionMs);
            timingSnapshot.setRewriteMs(queryRewriteMs);
            timingSnapshot.setRetrievalSpringMs(retrievalMs);
            timingSnapshot.setRetrievalPythonMs(retrievalPythonMs);
            timingSnapshot.setGroundedMs(groundedMs);
            timingSnapshot.setGapFillMs(gapFillMs);
            timingSnapshot.setGeneralGptMs(generalGptMs);
            timingSnapshot.setPersistenceMs(persistenceMs);
            timingSnapshot.setSuggestionsMs(suggestionsMs);
            timingSnapshot.setLiveHistoryMs(liveHistoryMs);
            timingSnapshot.setRestoreMs(restoreMs);
            timingSnapshot.setPreRetrievalGlueMs(preRetrievalGlueMs);
            timingSnapshot.setServiceTotalMs(totalRequestMs);
            timingSnapshot.setServiceEnd(serviceEnd);
        }

        return chatResponse;
        } finally {
            if (chatResponse != null && chatResponse.getActionTaken() != null && !chatResponse.getActionTaken().isBlank()) {
                routingSummary.setAction(chatResponse.getActionTaken());
            }
            if (chatResponse != null && chatResponse.getLexIntent() != null && !chatResponse.getLexIntent().isBlank()) {
                routingSummary.setIntent(chatResponse.getLexIntent());
            }
            if (route != null && !route.isBlank() && "-".equals(routingSummary.getRoute())) {
                routingSummary.setRoute(route);
            }
            long wallMs = RequestTimingLog.durationMs(serviceStart, Instant.now());
            RoutingSummaryLog.log(routingSummary, wallMs);
            log.info(ChatRequestSummaryLog.formatTiming(
                    piiDetectionMs,
                    routeDecisionMs,
                    0L,
                    retrievalMs,
                    groundedMs,
                    persistenceMs,
                    suggestionsMs,
                    wallMs
            ));
            int suggestionTokens = suggestionPromptTokens + suggestionCompletionTokens;
            int tokenTotal = routerPromptTokens + routerCompletionTokens + groundedTokens + gapFillTokens + suggestionTokens;
            log.info(ChatRequestSummaryLog.formatTokens(
                    routerPromptTokens + routerCompletionTokens,
                    groundedTokens,
                    gapFillTokens,
                    suggestionTokens,
                    tokenTotal
            ));
            RoutingCallTracker.clear();
        }
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

    /**
     * Pending-Lex ownership gate. Runs after Guided Search and before Python route.
     * Continues Lex dialog when a prior turn elicited a slot for this Lex session.
     */
    private PendingLexRouteAttempt tryHandlePendingLexTurn(
            ChatRequest request,
            String originalUserText,
            String sanitizedUserText
    ) {
        if (!lexService.isEnabled()) {
            String lexSessionId = lexService.buildLexSessionId(request);
            if (pendingLexSessionService.get(lexSessionId).isPresent()) {
                pendingLexSessionService.clear(lexSessionId);
                log.debug(
                        "Pending Lex cleared. Reason=LexDisabled sessionId='{}' lexSessionId='{}'",
                        request.getSessionId(),
                        lexSessionId
                );
            }
            return PendingLexRouteAttempt.notHandled();
        }

        String lexSessionId = lexService.buildLexSessionId(request);
        if (pendingLexSessionService.get(lexSessionId).isEmpty()) {
            return PendingLexRouteAttempt.notHandled();
        }

        log.debug(
                "Pending Lex detected. Skipping Python routing. Continuing Lex dialog. "
                        + "sessionId='{}' lexSessionId='{}'",
                request.getSessionId(),
                lexSessionId
        );

        try {
            LexLiveRouteResult lexResult = handleLexLiveRoute(request, originalUserText, sanitizedUserText);
            return PendingLexRouteAttempt.handled(lexResult);
        } catch (RuntimeException e) {
            pendingLexSessionService.clear(lexSessionId);
            log.debug(
                    "Pending Lex cleared. Reason=HandleLexLiveRouteFailed sessionId='{}' lexSessionId='{}'",
                    request.getSessionId(),
                    lexSessionId
            );
            throw e;
        }
    }

    private void updatePendingLexState(String lexSessionId, LexRecognizeResult result) {
        if (result == null || lexSessionId == null || lexSessionId.isBlank()) {
            return;
        }
        if (result.isElicitSlot() || result.isElicitIntent()) {
            pendingLexSessionService.markPending(lexSessionId);
            log.debug(
                    "Pending Lex marked. Reason={} lexSessionId='{}'",
                    result.getDialogActionType(),
                    lexSessionId
            );
            return;
        }
        if (result.isReadyForFulfillment()) {
            pendingLexSessionService.clear(lexSessionId);
            log.debug(
                    "Pending Lex cleared. Reason=ReadyForFulfillment lexSessionId='{}'",
                    lexSessionId
            );
            return;
        }
        if (result.isFallbackIntent()) {
            pendingLexSessionService.clear(lexSessionId);
            log.debug(
                    "Pending Lex cleared. Reason=FallbackIntent lexSessionId='{}'",
                    lexSessionId
            );
        }
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
        LexRecognizeResult lexResult = LexCustomerMasterIntentGuard.apply(
                originalUserText,
                lexService.recognizeText(lexSessionId, originalUserText)
        );
        updatePendingLexState(lexSessionId, lexResult);

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

        if (lexResult.isElicitIntent()) {
            String reply = String.join("\n", lexResult.getMessages()).trim();
            if (reply.isBlank()) {
                reply = "Which option did you mean?";
            }
            ChatResponse chatResponse = new ChatResponse(reply, false);
            chatResponse.setActionTaken("lex_elicit_intent");
            chatResponse.setLexIntent(lexResult.getIntentName());
            chatResponse.setLexDialogAction(lexResult.getDialogActionType());
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
            ProtectionSession session
    ) {
        return handleDocumentationRoute(request, originalUserText, session, null, null);
    }

    private DocRouteResult handleDocumentationRoute(
            ChatRequest request,
            String originalUserText,
            ProtectionSession session,
            List<String> routerQueries,
            OpenAIUsage routerUsage
    ) {
        boolean allowExternal = request.getMode() != ChatMode.DOCS
                || request.getExternalSourceEnabled() == null
                || Boolean.TRUE.equals(request.getExternalSourceEnabled());
        log.info(
                "Docs configuration | mode={} | externalSourceEnabled={} | allowExternal={}",
                request.getMode(),
                request.getExternalSourceEnabled(),
                allowExternal
        );

        long queryRewriteMs = 0L;
        long retrievalStageMs = 0L;
        long retrievalPythonMs = 0L;
        long preRetrievalGlueMs = 0L;
        long groundedMs = 0L;
        long gapFillMs = 0L;
        long generalGptMs = 0L;
        int groundedTokens = 0;
        int gapFillTokens = 0;
        int generalGptTokens = 0;
        String llmText = session.textForLlm();
        PythonQueryRequest ragRequest = new PythonQueryRequest();
        ragRequest.setMessage(llmText);
        ragRequest.setHistory(request.getHistory());

        List<String> rewrittenQueries = List.of();
        OpenAIUsage rewriteUsage = null;
        if (routerQueries != null) {
            rewrittenQueries = routerQueries;
            rewriteUsage = routerUsage;
        } else if (queryRewriteEnabled) {
            Instant rewriteStart = Instant.now();
            QueryRewriteResult rewriteResult = openAIService.rewriteQueries(request, llmText);
            Instant rewriteEnd = Instant.now();
            queryRewriteMs = RequestTimingLog.durationMs(rewriteStart, rewriteEnd);
            RequestTimingLog.logStage("rewrite", rewriteStart, rewriteEnd);
            rewrittenQueries = rewriteResult.queries();
            rewriteUsage = rewriteResult.usage();
        }

        Instant glueStart = Instant.now();
        List<String> searchQueries = SearchQueryAssembler.assemble(llmText, rewrittenQueries, 4);
        List<String> boostProgramIds = ProgramIdDetector.detect(llmText, originalUserText);
        Instant glueEnd = Instant.now();
        preRetrievalGlueMs = RequestTimingLog.durationMs(glueStart, glueEnd);
        RequestTimingLog.logStage("preRetrievalGlue", glueStart, glueEnd);
        log.info(
                "Doc retrieval program boost: detectedProgramIds={}",
                boostProgramIds.isEmpty() ? "none" : boostProgramIds
        );

        PythonRetrievalResponse retrieval;
        try {
            Instant retrievalStart = Instant.now();
            String retrievalRequestId = java.util.UUID.randomUUID().toString();
            String conversationId = request.getSessionId();
            retrieval = pythonRagService.retrieve(
                    llmText,
                    searchQueries,
                    ragRequest,
                    boostProgramIds,
                    retrievalRequestId,
                    conversationId
            );
            Instant retrievalEnd = Instant.now();
            retrievalStageMs = RequestTimingLog.durationMs(retrievalStart, retrievalEnd);
            RequestTimingLog.logStage("retrieval", retrievalStart, retrievalEnd);
            Integer pythonMs = retrieval.getRetrievalTimeMs();
            retrievalPythonMs = pythonMs != null ? pythonMs.longValue() : 0L;
            log.info(
                    "Python retrieval correlation | requestId={} | conversationId={} | stageMs={}",
                    retrievalRequestId,
                    conversationId,
                    retrievalStageMs
            );
            log.debug(
                    "Retrieval Clocks | springHttpMs={} | pythonInternalMs={} | httpTaxMs={}",
                    retrievalStageMs,
                    retrievalPythonMs,
                    Math.max(0L, retrievalStageMs - retrievalPythonMs)
            );
        } catch (OpenAIException e) {
            if (e.isAiServiceUnavailable()) {
                throw e;
            }
            log.warn(
                    "Python retrieval call failed (status={}), falling back to OpenAI: {}",
                    e.getStatusCode(),
                    e.getMessage()
            );
            if (!allowExternal) {
                ChatResponse chatResponse = buildDocsOnlyInsufficientResponse(request, rewriteUsage);
                return new DocRouteResult(
                        chatResponse,
                        List.of(),
                        List.of(),
                        "retrieval_error",
                        null,
                        null,
                        (int) queryRewriteMs,
                        (int) retrievalStageMs,
                        (int) retrievalPythonMs,
                        (int) preRetrievalGlueMs,
                        0,
                        0,
                        0,
                        groundedTokens,
                        gapFillTokens,
                        generalGptTokens
                );
            }
            Instant generalStart = Instant.now();
            ChatResponse chatResponse = openAIService.chatWithoutPersistence(request, session);
            Instant generalEnd = Instant.now();
            generalGptMs = RequestTimingLog.durationMs(generalStart, generalEnd);
            RequestTimingLog.logStage("generalGPT", generalStart, generalEnd);
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
                    (int) retrievalPythonMs,
                    (int) preRetrievalGlueMs,
                    0,
                    0,
                    (int) generalGptMs,
                    groundedTokens,
                    gapFillTokens,
                    generalGptTokens
            );
        }

        abortIfPythonAiUnavailable(retrieval);

        String reason = retrieval.getRetrievalReason();
        log.info(
                "Doc retrieval: originalLength={} llmTextLength={} rewrittenQueries={} reason={} maxScore={} promptChunkCount={} chunkCount={} queryRewriteEnabled={}",
                originalUserText != null ? originalUserText.length() : 0,
                llmText != null ? llmText.length() : 0,
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
                Instant groundedStart = Instant.now();
                GroundedRagCallResult groundedCall = openAIService.chatWithRagContext(request, promptChunks, session);
                Instant groundedEnd = Instant.now();
                groundedMs = RequestTimingLog.durationMs(groundedStart, groundedEnd);
                RequestTimingLog.logStage("grounded", groundedStart, groundedEnd);
                logUsage("Grounded", groundedCall.usage(), groundedMs);
                log.info(
                        "Grounded Stage Timing | promptBuildMs={} | openAiWaitMs={} | responseParseMs={} | "
                                + "chunkCount={} | promptContextChars={} | promptTokens={} | completionTokens={} | totalGroundedMs={}",
                        groundedCall.promptBuildMs(),
                        groundedCall.openAiWaitMs(),
                        groundedCall.responseParseMs(),
                        groundedCall.chunkCount(),
                        groundedCall.promptContextChars(),
                        nullSafeInt(groundedCall.usage() != null ? groundedCall.usage().getPromptTokens() : null),
                        nullSafeInt(groundedCall.usage() != null ? groundedCall.usage().getCompletionTokens() : null),
                        groundedMs
                );
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

                DocAnswerOutcome outcome = orchestrateGroundedStatus(
                        request, grounded, groundedUsage, session, allowExternal
                );
                chatResponse = outcome.chatResponse();
                reason = outcome.retrievalReason();
                gapFillMs = outcome.gapFillTimeMs();
                generalGptMs = outcome.generalGptTimeMs();
                gapFillTokens = outcome.gapFillTokens();
                generalGptTokens = outcome.generalGptTokens();
                if (gapFillMs > 0) {
                    log.info(
                            "Stage Timing | stage=gapFill | start={} | end={} | durationMs={}",
                            groundedEnd,
                            Instant.ofEpochMilli(groundedEnd.toEpochMilli() + gapFillMs),
                            gapFillMs
                    );
                }
                if (generalGptMs > 0) {
                    log.info(
                            "Stage Timing | stage=generalGPT | start={} | end={} | durationMs={}",
                            groundedEnd,
                            Instant.ofEpochMilli(groundedEnd.toEpochMilli() + generalGptMs),
                            generalGptMs
                    );
                }
                logUsage("Merged", chatResponse.getOpenAiUsage(), 0);
            } catch (OpenAIException e) {
                if (e.isAiServiceUnavailable()) {
                    throw e;
                }
                log.warn("Grounded JSON parsing failed, falling back to General GPT: {}", e.getMessage());
                if (!allowExternal) {
                    chatResponse = buildDocsOnlyInsufficientResponse(request, rewriteUsage);
                    reason = RETRIEVAL_RAG_NO_ANSWER;
                    log.info(
                            "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={}",
                            "PARSE_FAILED",
                            reason,
                            true,
                            false,
                            false,
                            chatResponse.getActionTaken()
                    );
                } else {
                Instant generalStart = Instant.now();
                chatResponse = openAIService.chatWithoutPersistence(request, session);
                Instant generalEnd = Instant.now();
                generalGptMs = RequestTimingLog.durationMs(generalStart, generalEnd);
                RequestTimingLog.logStage("generalGPT", generalStart, generalEnd);
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
            }
        } else {
            if (retrieval.getError() != null) {
                log.warn("Retrieval error from Python, falling back to OpenAI: {}", retrieval.getError());
            }
            if (!allowExternal) {
                chatResponse = buildDocsOnlyInsufficientResponse(request, rewriteUsage);
            } else {
            Instant generalStart = Instant.now();
            chatResponse = openAIService.chatWithoutPersistence(request, session);
            Instant generalEnd = Instant.now();
            generalGptMs = RequestTimingLog.durationMs(generalStart, generalEnd);
            RequestTimingLog.logStage("generalGPT", generalStart, generalEnd);
            logUsage("General", chatResponse.getOpenAiUsage(), generalGptMs);
            generalGptTokens = nullSafeInt(chatResponse.getOpenAiUsage() != null ? chatResponse.getOpenAiUsage().getTotalTokens() : null);
            if (rewriteUsage != null) {
                chatResponse.setOpenAiUsage(mergeUsage(rewriteUsage, chatResponse.getOpenAiUsage()));
            }
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
                (int) retrievalStageMs,
                (int) retrievalPythonMs,
                (int) preRetrievalGlueMs,
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
            OpenAIUsage usageSoFar,
            ProtectionSession session,
            boolean allowExternal
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
            if (!allowExternal) {
                ChatResponse response = buildDocsOnlyPartialResponse(request, docAnswer, usageSoFar);
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
            if (ragPartialGapFillEnabled && !missingTopics.isEmpty()) {
                long gapFillStartMs = System.currentTimeMillis();
                ChatResponse gapFill = openAIService.chatGapFill(request, docAnswer, missingTopics, session);
                int gapFillTimeMs = (int) (System.currentTimeMillis() - gapFillStartMs);
                logUsage("GapFill", gapFill.getOpenAiUsage(), gapFillTimeMs);
                int gapFillTokens = nullSafeInt(gapFill.getOpenAiUsage() != null ? gapFill.getOpenAiUsage().getTotalTokens() : null);
                String gapReply = gapFill.getReply() != null ? gapFill.getReply().strip() : "";
                String combined = gapReply.isEmpty()
                        ? docAnswer.strip()
                        : docAnswer.strip() + "\n\n" + gapReply;
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

        if (!allowExternal) {
            ChatResponse response = buildDocsOnlyInsufficientResponse(request, usageSoFar);
            log.info(
                    "RAG Decision | status={} | retrievalReason={} | grounded={} | gapFill={} | generalGPT={} | finalAction={}",
                    "INSUFFICIENT",
                    RETRIEVAL_RAG_NO_ANSWER,
                    true,
                    false,
                    false,
                    response.getActionTaken()
            );
            return new DocAnswerOutcome(response, RETRIEVAL_RAG_NO_ANSWER, 0, 0, 0, 0);
        }

        long generalStartMs = System.currentTimeMillis();
        ChatResponse general = openAIService.chatWithoutPersistence(request, session);
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

    private void recordUnderstandResult(RoutingSummaryState summary, RequestUnderstandResult understood) {
        if (understood != null) {
            String model = openAiModel == null || openAiModel.isBlank() ? "openai" : openAiModel;
            summary.setRouter("OpenAI / " + model);
            summary.setType(understood.type());
            logUsage("Router", understood.usage(), 0L);
        } else {
            summary.setRouter("skipped (router-error)");
            summary.setTypeRaw("-");
        }
    }

    private UnderstandRun runUnderstandRequest(ChatRequest request, String originalUserText) {
        Instant understandStart = Instant.now();
        ProtectionSession session = ProtectionSession.fromOriginal(
                originalUserText,
                businessInformationProtectionService.isEnabled()
        );
        businessInformationProtectionService.protect(
                session,
                ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true)
        );
        protectPiiSafely(session);
        Instant piiEnd = Instant.now();
        long piiMs = RequestTimingLog.durationMs(understandStart, piiEnd);
        RequestTimingLog.logStage("pii", understandStart, piiEnd);

        String llmText = session.textForLlm();
        ChatRequest llmRequest = copyRequestWithUserMessage(request, llmText);
        RequestUnderstandResult understood = null;
        try {
            understood = openAIService.understandRequest(llmRequest, llmText);
        } catch (OpenAIException e) {
            if (e.isAiServiceUnavailable()) {
                throw e;
            }
            log.warn("Request router failed: {}.", e.getMessage());
        } catch (Exception e) {
            if (AiServiceErrors.isQuotaOrCreditExhaustion(e.getMessage())) {
                throw AiServiceErrors.unavailable(e.getMessage());
            }
            log.warn("Request router failed: {}.", e.getMessage());
        }
        Instant understandEnd = Instant.now();
        long understandMs = RequestTimingLog.durationMs(understandStart, understandEnd);
        RequestTimingLog.logStage("understand", understandStart, understandEnd);
        return new UnderstandRun(understood, session, llmRequest, llmText, piiMs, understandMs);
    }

    private ChatResponse buildM3LiveSteerResponse(ChatRequest request, RequestUnderstandResult understood) {
        String steer = m3LiveSteerMessage != null && !m3LiveSteerMessage.isBlank()
                ? m3LiveSteerMessage
                : DEFAULT_M3_LIVE_STEER_MESSAGE;
        ChatResponse chatResponse = new ChatResponse(steer, false);
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken("m3_live_steer");
        if (understood != null) {
            chatResponse.setOpenAiUsage(understood.usage());
        }
        return chatResponse;
    }

    private ChatResponse buildRouterUserResponse(
            ChatRequest request,
            RequestUnderstandResult understood,
            String actionTaken
    ) {
        String reply = understood != null && understood.response() != null ? understood.response() : "";
        ChatResponse chatResponse = new ChatResponse(reply, false);
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken(actionTaken);
        if (understood != null) {
            chatResponse.setOpenAiUsage(understood.usage());
        }
        return chatResponse;
    }

    private boolean allowExternalFallback(ChatRequest request) {
        return request.getMode() != ChatMode.DOCS
                || request.getExternalSourceEnabled() == null
                || Boolean.TRUE.equals(request.getExternalSourceEnabled());
    }

    private ChatResponse buildRagChatResponse(ChatRequest request, String reply, OpenAIUsage usage) {
        ChatResponse chatResponse = new ChatResponse(reply != null ? reply : "", false);
        chatResponse.setHistory(request.getHistory());
        chatResponse.setActionTaken("rag");
        chatResponse.setOpenAiUsage(usage);
        return chatResponse;
    }

    private ChatResponse buildDocsOnlyInsufficientResponse(ChatRequest request, OpenAIUsage usage) {
        return buildRagChatResponse(request, DOCS_INSUFFICIENT_MESSAGE, usage);
    }

    private ChatResponse buildDocsOnlyPartialResponse(
            ChatRequest request,
            String docAnswer,
            OpenAIUsage usage
    ) {
        String trimmed = docAnswer != null ? docAnswer.strip() : "";
        if (trimmed.isEmpty()) {
            return buildDocsOnlyInsufficientResponse(request, usage);
        }
        return buildRagChatResponse(request, trimmed + "\n\n" + DOCS_PARTIAL_CONTINUATION, usage);
    }

    private void abortIfPythonAiUnavailable(PythonRetrievalResponse retrieval) {
        if (retrieval == null) {
            return;
        }
        String errorCode = retrieval.getErrorCode();
        String reason = retrieval.getRetrievalReason();
        String error = retrieval.getError();
        boolean flagged = AiServiceErrors.ERROR_CODE.equals(errorCode)
                || "ai_service_unavailable".equalsIgnoreCase(reason)
                || AiServiceErrors.isQuotaOrCreditExhaustion(error)
                || AiServiceErrors.isQuotaOrCreditExhaustion(errorCode);
        if (flagged) {
            String detail = "python retrieval errorCode=" + errorCode
                    + " reason=" + reason
                    + " error=" + error;
            log.error("Aborting chat: upstream AI service unavailable | {}", detail);
            throw AiServiceErrors.unavailable(detail);
        }
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

    private String anonymizeForPersistence(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            return piiProtectionService.anonymize(text);
        } catch (Exception e) {
            log.warn("PII anonymization failed, using original text: {}", e.getMessage());
            return text;
        }
    }

    private void protectPiiSafely(ProtectionSession session) {
        if (session == null) {
            return;
        }
        try {
            piiProtectionService.protect(session);
        } catch (Exception e) {
            log.warn("PII protection failed, keeping pre-PII text: {}", e.getMessage());
            String fallback = session.businessProtectedText() != null
                    ? session.businessProtectedText()
                    : session.originalText();
            session.applyPiiSanitizedText(fallback);
        }
    }

    private void applyProtectionSessionToResponse(ChatResponse chatResponse, ProtectionSession session) {
        if (chatResponse == null || session == null) {
            return;
        }
        chatResponse.setOriginalUserMessage(session.originalText());
        chatResponse.setBusinessProtectedMessage(session.businessProtectedText());
        chatResponse.setBusinessProtectionApplied(session.businessProtectionApplied());
        List<BusinessProtectedEntityDto> entities = new ArrayList<>();
        if (session.actions() != null) {
            for (ProtectionAction action : session.actions()) {
                if (action == null || action.placeholderToken() == null || action.placeholderToken().isBlank()) {
                    continue;
                }
                String type = action.placeholderType() != null
                        ? action.placeholderType()
                        : action.placeholderToken();
                entities.add(new BusinessProtectedEntityDto(type, action.placeholderToken()));
            }
        }
        chatResponse.setBusinessProtectedEntities(entities);
        String sanitized = session.piiSanitizedText() != null
                ? session.piiSanitizedText()
                : session.textForLlm();
        chatResponse.setSanitizedUserMessage(sanitized);
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

    private static ChatMode resolveMode(ChatRequest request) {
        return request.getMode() == null ? ChatMode.AUTO : request.getMode();
    }

    private ChatRequest copyRequestWithUserMessage(ChatRequest originalRequest, String newUserMessage) {
        ChatRequest copy = new ChatRequest();
        copy.setTenantCode(originalRequest.getTenantCode());
        copy.setUserId(originalRequest.getUserId());
        copy.setSessionId(originalRequest.getSessionId());
        copy.setUserMessage(newUserMessage);
        copy.setMode(originalRequest.getMode());
        copy.setExternalSourceEnabled(originalRequest.getExternalSourceEnabled());
        copy.setEditOfRequestLogId(originalRequest.getEditOfRequestLogId());
        copy.setHistory(originalRequest.getHistory());
        copy.setM3ClientReport(originalRequest.getM3ClientReport());
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
            Integer retrievalSpringMs,
            Integer retrievalPythonMs,
            Integer preRetrievalGlueMs,
            Integer groundedTimeMs,
            Integer gapFillTimeMs,
            Integer generalGptTimeMs,
            Integer groundedTokens,
            Integer gapFillTokens,
            Integer generalGptTokens
    ) {
    }

    private record UnderstandRun(
            RequestUnderstandResult understood,
            ProtectionSession protectionSession,
            ChatRequest workingRequest,
            String sanitizedUserText,
            long piiDetectionMs,
            long routeDecisionMs
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

    private record PendingLexRouteAttempt(
            boolean pendingHandled,
            LexLiveRouteResult lexResult
    ) {
        static PendingLexRouteAttempt handled(LexLiveRouteResult lexResult) {
            return new PendingLexRouteAttempt(true, lexResult);
        }

        static PendingLexRouteAttempt notHandled() {
            return new PendingLexRouteAttempt(false, null);
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

    private ComprehendChatTimingSnapshot currentTimingSnapshot() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
                return null;
            }
            Object raw = servletAttrs.getRequest().getAttribute(RequestTimingLog.REQUEST_ATTR);
            if (raw instanceof ComprehendChatTimingSnapshot snapshot) {
                return snapshot;
            }
        } catch (Exception ignored) {
            // No request context (unit tests)
        }
        return null;
    }

    private void markServiceEnd(ComprehendChatTimingSnapshot snapshot, Instant end, long requestStartMs) {
        if (snapshot == null) {
            return;
        }
        snapshot.setServiceEnd(end);
        snapshot.setServiceTotalMs(Math.max(0L, end.toEpochMilli() - requestStartMs));
    }
}
