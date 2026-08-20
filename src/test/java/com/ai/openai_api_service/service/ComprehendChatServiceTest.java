package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.LiveHistoryAuditMetadata;
import com.ai.openai_api_service.model.GuidedSearchState;
import com.ai.openai_api_service.model.LexFulfillmentOutcome;
import com.ai.openai_api_service.model.M3RequestDto;
import com.ai.openai_api_service.model.SearchCriterion;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.SuggestionResult;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.lex.LexRecognizeResult;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
import com.ai.openai_api_service.model.QueryRewriteResult;
import com.ai.openai_api_service.model.RequestUnderstandResult;
import com.ai.openai_api_service.model.RequestUnderstandType;
import com.ai.openai_api_service.model.python_rag.PythonRetrievalResponse;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
import com.ai.openai_api_service.model.rag.GroundedRagCallResult;
import com.ai.openai_api_service.model.rag.GroundedRagResult;
import com.ai.openai_api_service.model.rag.RagStatus;
import com.ai.openai_api_service.service.protection.BusinessInformationProtectionService;
import com.ai.openai_api_service.service.protection.BusinessPlaceholderRestorer;
import com.ai.openai_api_service.service.protection.PiiProtectionService;
import com.ai.openai_api_service.service.protection.ProtectionSession;
import com.ai.openai_api_service.service.query.SearchContextService;
import com.ai.openai_api_service.service.guided.GuidedSearchService;
import com.ai.openai_api_service.service.guided.InMemoryGuidedSearchSessionService;
import com.ai.openai_api_service.service.lex.InMemoryPendingLexSessionService;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import com.ai.openai_api_service.service.TenantQuotaService.QuotaCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComprehendChatServiceTest {

    @Mock
    private ChatPersistenceService chatPersistenceService;
    @Mock
    private TenantQuotaService tenantQuotaService;
    @Mock
    private SuggestionEngineService suggestionEngineService;
    @Mock
    private PythonRagService pythonRagService;
    @Mock
    private OpenAIService openAIService;
    @Mock
    private LexService lexService;
    @Mock
    private LexFulfillmentService lexFulfillmentService;
    @Mock
    private SearchContextService searchContextService;
    @Mock
    private GuidedSearchService guidedSearchService;
    @Mock
    private InMemoryGuidedSearchSessionService guidedSearchSessionService;
    @Mock
    private BusinessInformationProtectionService businessInformationProtectionService;
    @Mock
    private PiiProtectionService piiProtectionService;
    @Mock
    private BusinessPlaceholderRestorer businessPlaceholderRestorer;
    @Spy
    private InMemoryPendingLexSessionService pendingLexSessionService = new InMemoryPendingLexSessionService(3600);
    @Spy
    private LiveHistorySummaryBuilder liveHistorySummaryBuilder = new LiveHistorySummaryBuilder();
    @Spy
    private RequestedInformationResolver requestedInformationResolver =
            new RequestedInformationResolver(new SearchFieldCatalog(), new InformationRequestCatalog());
    @Spy
    private IntentApiCatalog intentApiCatalog = new IntentApiCatalog();

    @InjectMocks
    private ComprehendChatService comprehendChatService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(comprehendChatService, "ragPartialGapFillEnabled", true);
        ReflectionTestUtils.setField(comprehendChatService, "queryRewriteEnabled", false);
        ReflectionTestUtils.setField(
                comprehendChatService,
                "m3LiveSteerMessage",
                ComprehendChatService.DEFAULT_M3_LIVE_STEER_MESSAGE
        );
        ReflectionTestUtils.setField(
                comprehendChatService,
                "m3DocsSteerMessage",
                ComprehendChatService.DEFAULT_M3_DOCS_STEER_MESSAGE
        );
        ReflectionTestUtils.setField(
                comprehendChatService,
                "m3NonM3Message",
                ComprehendChatService.DEFAULT_M3_NON_M3_MESSAGE
        );
        ReflectionTestUtils.setField(
                comprehendChatService,
                "m3ClassifierErrorMessage",
                ComprehendChatService.DEFAULT_M3_CLASSIFIER_ERROR_MESSAGE
        );
        ReflectionTestUtils.setField(
                comprehendChatService,
                "docsLiveSteerMessage",
                ComprehendChatService.DEFAULT_DOCS_LIVE_STEER_MESSAGE
        );
        ReflectionTestUtils.setField(comprehendChatService, "openAiModel", "gpt-5.6-terra");
        pendingLexSessionService = new InMemoryPendingLexSessionService(3600);
        ReflectionTestUtils.setField(comprehendChatService, "pendingLexSessionService", pendingLexSessionService);
        lenient().when(guidedSearchSessionService.find(any())).thenReturn(Optional.empty());
        lenient().when(businessInformationProtectionService.isEnabled()).thenReturn(false);
        lenient().when(businessInformationProtectionService.protect(any(ProtectionSession.class), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(piiProtectionService.anonymize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(piiProtectionService.protect(any())).thenAnswer(inv -> {
            ProtectionSession session = inv.getArgument(0);
            if (session == null) {
                return null;
            }
            String input = session.businessProtectedText() != null
                    ? session.businessProtectedText()
                    : session.originalText();
            session.applyPiiSanitizedText(input);
            return session;
        });
        lenient().when(businessPlaceholderRestorer.restoreIntoSession(anyString(), any())).thenAnswer(inv -> {
            String reply = inv.getArgument(0);
            ProtectionSession session = inv.getArgument(1);
            if (session != null) {
                session.applyRestoredReply(reply, reply);
            }
            return reply;
        });
    }

    @Test
    void documentationRoute_readyForGrounding_usesRagContext() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(42);
        retrieval.setMaxScore(0.62f);
        ChunkItem chunk = new ChunkItem("chunk text", 0.62f, "Title", "http://example.com", List.of("CRS610"), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(10, 20, 30, "gpt-4.1");
        GroundedRagResult grounded = new GroundedRagResult(RagStatus.FULL, "grounded answer", List.of());
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenReturn(new GroundedRagCallResult(grounded, usage, "{\"status\":\"FULL\"}"));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("how to create customer");
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("grounded answer", response.getReply());
        assertEquals("rag", response.getActionTaken());
        assertNull(response.getM3Request());
        assertEquals("ready_for_grounding", response.getRetrievalReason());
        assertNotNull(response.getOpenAiUsage());
        assertNotNull(response.getSources());
        assertEquals(1, response.getSources().size());
        assertEquals("http://example.com", response.getSources().get(0).getUrl());
        assertEquals("Title", response.getSources().get(0).getTitle());
        assertEquals(0.62f, response.getSources().get(0).getScore());
        verify(openAIService).chatWithRagContext(any(), eq(List.of(chunk)), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
        verify(pythonRagService, never()).query(any());
        verify(tenantQuotaService).recordUsage(eq("tenant1"), eq(30), anyString());
    }

    @Test
    void documentationRoute_belowThreshold_usesFallback() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("weak docs")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("weak docs"));

        assertEquals("fallback answer", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        assertNotNull(response.getSources());
        assertTrue(response.getSources().isEmpty());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), any(), any());
    }

    @Test
    void documentationRoute_readyForGrounding_returnsPerChunkSourcesWithoutDedup() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("pricing issue")).thenReturn(new PythonRouteResponse("rag"));

        ChunkItem chunk1 = new ChunkItem("chunk one", 0.72f, "Title A", "http://example.com/doc", List.of("CRS610"), null, null, null, null);
        ChunkItem chunk2 = new ChunkItem("chunk two", 0.55f, "Title B", "http://example.com/doc", List.of("CRS610"), null, null, null, null);
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setMaxScore(0.72f);
        retrieval.setPromptChunks(List.of(chunk1, chunk2));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("grounded answer", false);
        openAiResponse.setActionTaken("rag");
        when(openAIService.chatWithRagContext(any(), anyList(), any())).thenReturn(
                new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "grounded answer", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt-4.1"),
                        "{}"
                )
        );
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("pricing issue"));

        assertEquals(2, response.getSources().size());
        assertEquals("http://example.com/doc", response.getSources().get(0).getUrl());
        assertEquals(0.72f, response.getSources().get(0).getScore());
        assertEquals("http://example.com/doc", response.getSources().get(1).getUrl());
        assertEquals(0.55f, response.getSources().get(1).getScore());
    }

    @Test
    void liveRoute_bypassesBusinessProtection_usesOriginalIds() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(false);
        String original = "show customer 45678";
        when(pythonRagService.route(original)).thenReturn(new PythonRouteResponse("live"));
        when(pythonRagService.query(any())).thenAnswer(invocation -> {
            com.ai.openai_api_service.model.python_rag.PythonQueryResponse response =
                    new com.ai.openai_api_service.model.python_rag.PythonQueryResponse();
            response.setReply("live answer for 45678");
            response.setActionTaken("read");
            return response;
        });
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertEquals("live answer for 45678", response.getReply());
        verify(pythonRagService).route(eq(original));
        verify(businessInformationProtectionService, never()).protect(any(ProtectionSession.class), any());
        verify(businessPlaceholderRestorer, never()).restoreIntoSession(anyString(), any());
        assertNull(response.getBusinessProtectionApplied());
    }

    @Test
    void ragRoute_restoresPlaceholdersAndExposesAuditFields() {
        stubQuotaAllowed();
        when(businessInformationProtectionService.isEnabled()).thenReturn(true);
        when(businessInformationProtectionService.protect(any(ProtectionSession.class), any())).thenAnswer(inv -> {
            ProtectionSession session = inv.getArgument(0);
            session.applyBusinessResult(new com.ai.openai_api_service.service.protection.ProtectedText(
                    "How can I register customer <CUSTOMER_NUMBER>?",
                    List.of(new com.ai.openai_api_service.service.protection.ProtectionAction(
                            null,
                            com.ai.openai_api_service.service.protection.LlmExposurePolicy.REPLACE,
                            "CUSTOMER_NUMBER",
                            "<CUSTOMER_NUMBER>",
                            "45678"
                    )),
                    Map.of("<CUSTOMER_NUMBER>", "45678")
            ));
            return session;
        });
        when(businessPlaceholderRestorer.restoreIntoSession(anyString(), any())).thenAnswer(inv -> {
            String reply = inv.getArgument(0);
            ProtectionSession session = inv.getArgument(1);
            String restored = reply != null ? reply.replace("<CUSTOMER_NUMBER>", "45678") : null;
            if (session != null) {
                session.applyRestoredReply(reply, restored);
            }
            return restored;
        });

        String original = "How can I register customer 45678?";
        when(pythonRagService.route(original)).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse(
                "Register customer <CUSTOMER_NUMBER> in CRS610.",
                false
        );
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(new OpenAIUsage(1, 1, 2, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertEquals("Register customer 45678 in CRS610.", response.getReply());
        assertEquals("Register customer <CUSTOMER_NUMBER> in CRS610.", response.getReplyBeforeRestore());
        assertEquals(original, response.getOriginalUserMessage());
        assertEquals("How can I register customer <CUSTOMER_NUMBER>?", response.getBusinessProtectedMessage());
        assertTrue(response.getBusinessProtectionApplied());
        assertNotNull(response.getBusinessProtectedEntities());
        assertFalse(response.getBusinessProtectedEntities().isEmpty());
        verify(pythonRagService).route(eq(original));
        verify(pythonRagService).retrieve(
                eq("How can I register customer <CUSTOMER_NUMBER>?"),
                eq(List.of("How can I register customer <CUSTOMER_NUMBER>?")),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void liveRoute_usesPythonChat() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("show customer C001")).thenReturn(new PythonRouteResponse("live"));
        when(pythonRagService.query(any())).thenAnswer(invocation -> {
            com.ai.openai_api_service.model.python_rag.PythonQueryResponse response =
                    new com.ai.openai_api_service.model.python_rag.PythonQueryResponse();
            response.setReply("live answer");
            response.setActionTaken("read");
            return response;
        });
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer C001"));

        assertEquals("live answer", response.getReply());
        assertEquals("read", response.getActionTaken());
        verify(pythonRagService).query(any());
        verify(openAIService, never()).chatWithRagContext(any(), any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
        verify(tenantQuotaService, never()).recordUsage(anyString(), anyInt(), anyString());
    }

    @Test
    void documentationRoute_insufficientStatus_fallsBackToOpenAi() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to add KIT")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(100);
        retrieval.setMaxScore(0.64f);
        ChunkItem chunk = new ChunkItem("chunk text", 0.64f, "Title", "http://example.com", List.of("OIS100"), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage ragUsage = new OpenAIUsage(3000, 20, 3020, "gpt-4.1");
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any())).thenReturn(
                new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.INSUFFICIENT, "", List.of()),
                        ragUsage,
                        "{\"status\":\"INSUFFICIENT\"}"
                )
        );

        OpenAIUsage fallbackUsage = new OpenAIUsage(50, 100, 150, "gpt-4.1");
        ChatResponse fallbackResponse = new ChatResponse("To add a KIT on a customer order line, open OIS100...", false);
        fallbackResponse.setActionTaken("gpt_infor");
        fallbackResponse.setOpenAiUsage(fallbackUsage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallbackResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to add KIT"));

        assertEquals("To add a KIT on a customer order line, open OIS100...", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        assertEquals("rag_no_answer_fallback", response.getRetrievalReason());
        assertEquals(3170, response.getOpenAiUsage().getTotalTokens());
        verify(openAIService).chatWithRagContext(any(), eq(List.of(chunk)), any());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
    }

    @Test
    void documentationRoute_partialStatus_preservesDocsAndGapFills() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("What is MNS204 used for?")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setMaxScore(0.66f);
        ChunkItem chunk = new ChunkItem("chunk", 0.66f, "MNS204", "http://docs/mns204", List.of("MNS204"), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage ragUsage = new OpenAIUsage(100, 50, 150, "gpt-4.1");
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any())).thenReturn(
                new GroundedRagCallResult(
                        new GroundedRagResult(
                                RagStatus.PARTIAL,
                                "MNS204 appears in user settings documentation.",
                                List.of("Functional purpose", "Business usage")
                        ),
                        ragUsage,
                        "{\"status\":\"PARTIAL\"}"
                )
        );

        OpenAIUsage gapUsage = new OpenAIUsage(20, 30, 50, "gpt-4.1");
        ChatResponse gapResponse = new ChatResponse("Functional purpose: ...", false);
        gapResponse.setOpenAiUsage(gapUsage);
        when(openAIService.chatGapFill(any(), anyString(), anyList(), any())).thenReturn(gapResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("What is MNS204 used for?"));

        assertTrue(response.getReply().contains("MNS204 appears in user settings documentation."));
        assertTrue(response.getReply().contains("Functional purpose: ..."));
        assertFalse(response.getReply().contains("## Documentation"));
        assertFalse(response.getReply().contains("## Additional AI Information"));
        assertEquals(
                "MNS204 appears in user settings documentation.\n\nFunctional purpose: ...",
                response.getReply()
        );
        assertEquals("rag", response.getActionTaken());
        assertEquals("ready_for_grounding", response.getRetrievalReason());
        assertEquals(200, response.getOpenAiUsage().getTotalTokens());
        verify(openAIService).chatGapFill(
                any(),
                eq("MNS204 appears in user settings documentation."),
                eq(List.of("Functional purpose", "Business usage")),
                any()
        );
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void documentationRoute_partialStatus_gapFillDisabled_returnsDocsOnly() {
        ReflectionTestUtils.setField(comprehendChatService, "ragPartialGapFillEnabled", false);
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("What is MNS204 used for?")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        ChunkItem chunk = new ChunkItem("chunk", 0.66f, "MNS204", "http://docs/mns204", List.of("MNS204"), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any())).thenReturn(
                new GroundedRagCallResult(
                        new GroundedRagResult(
                                RagStatus.PARTIAL,
                                "Docs only answer",
                                List.of("Missing topic")
                        ),
                        new OpenAIUsage(10, 10, 20, "gpt-4.1"),
                        "{}"
                )
        );
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("What is MNS204 used for?"));

        assertEquals("Docs only answer", response.getReply());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void documentationRoute_groundedParseFailure_fallsBackToOpenAi() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        ChunkItem chunk = new ChunkItem("chunk", 0.7f, "T", "http://x", List.of(), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), anyList(), any()))
                .thenThrow(new OpenAIException("Failed to parse grounded RAG JSON", 502));

        ChatResponse fallback = new ChatResponse("general answer", false);
        fallback.setActionTaken("gpt_infor");
        fallback.setOpenAiUsage(new OpenAIUsage(5, 5, 10, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to create customer"));

        assertEquals("general answer", response.getReply());
        assertEquals("rag_no_answer_fallback", response.getRetrievalReason());
        verify(openAIService).chatWithoutPersistence(any(), any());
    }

    @Test
    void documentationRoute_retrievalTimeout_fallsBackToOpenAi() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenThrow(
                new OpenAIException("Python RAG API timeout after 180000ms", 504)
        );

        OpenAIUsage usage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback after timeout", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to create customer"));

        assertEquals("fallback after timeout", response.getReply());
        assertEquals("retrieval_error", response.getRetrievalReason());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), any(), any());
    }

    @Test
    void documentationRoute_noMatches_usesFallback() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("unknown topic")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("no_matches");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(8, 12, 20, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("general m3 answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("unknown topic"));

        assertEquals("general m3 answer", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        assertEquals("no_matches", response.getRetrievalReason());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), any(), any());
        verify(pythonRagService, never()).query(any());
    }

    @Test
    void documentationRoute_retrievalErrorInBody_usesFallback() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to configure CRS900")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("retrieval_error");
        retrieval.setError("Qdrant down");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(6, 4, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback after qdrant error", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to configure CRS900"));

        assertEquals("fallback after qdrant error", response.getReply());
        assertEquals("retrieval_error", response.getRetrievalReason());
        assertEquals("gpt_infor", response.getActionTaken());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(tenantQuotaService).recordUsage(eq("tenant1"), eq(10), anyString());
    }

    @Test
    void documentationRoute_pythonUnreachable_fallsBackAndRecordsTokens() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenThrow(
                new OpenAIException("Python RAG API connection refused: WinError 10061", 503)
        );

        OpenAIUsage usage = new OpenAIUsage(15, 25, 40, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback after connection error", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to create customer"));

        assertEquals("fallback after connection error", response.getReply());
        assertEquals("retrieval_error", response.getRetrievalReason());
        assertEquals(40, response.getOpenAiUsage().getTotalTokens());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(tenantQuotaService).recordUsage(eq("tenant1"), eq(40), anyString());
    }

    @Test
    void quotaBlockedBeforeChat_returnsLimitExceededWithoutCallingServices() {
        when(tenantQuotaService.checkBeforeChat("tenant1"))
                .thenReturn(new QuotaCheckResult(false, new TokenUsageDto(1000, 1000, 0), "LIMIT_EXCEEDED"));

        ChatResponse response = comprehendChatService.chat(baseRequest("hello"));

        assertTrue(response.getLimitExceeded());
        assertEquals("LIMIT_EXCEEDED", response.getBlockReason());
        verify(piiProtectionService, never()).anonymize(anyString());
        verify(piiProtectionService, never()).protect(any());
        verify(pythonRagService, never()).route(anyString());
        verify(openAIService, never()).chatWithRagContext(any(), any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
        verify(tenantQuotaService, never()).recordUsage(anyString(), anyInt(), anyString());
        verify(chatPersistenceService, never()).persistChat(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), anyBoolean(), anyString(), any(), any(), any(), any()
        );
    }

    @Test
    void piiSanitization_routesOnOriginalText_retrievesOnSanitized() {
        stubQuotaAllowed();
        String original = "Contact John at john@example.com about CRS900";
        String sanitized = "Contact [Name] at [EMAIL] about CRS900";
        stubSanitizeWithPii(original, sanitized);
        when(pythonRagService.route(original)).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(new OpenAIUsage(1, 1, 2, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertTrue(response.getSanitizationApplied());
        verify(pythonRagService).route(eq(original));
        verify(pythonRagService).retrieve(eq(sanitized), eq(List.of(sanitized)), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void comprehendFails_usesOriginalTextAndCompletes() {
        stubQuotaAllowed();
        String original = "how to configure purchase settings";
        org.mockito.Mockito.doThrow(new RuntimeException("Comprehend IAM denied"))
                .when(piiProtectionService).protect(any());
        when(pythonRagService.route(original)).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("still works", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(new OpenAIUsage(3, 3, 6, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertEquals("still works", response.getReply());
        assertFalse(response.getSanitizationApplied());
        verify(pythonRagService).route(eq(original));
    }

    @Test
    void documentationRoute_readyForGrounding_persistsChatWithUsageAndRetrievalReason() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("purchase settings")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(88);
        retrieval.setMaxScore(0.71f);
        ChunkItem chunk = new ChunkItem("chunk", 0.71f, "CRS780", "http://docs/crs780", List.of("CRS780"), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(100, 50, 150, "gpt-4.1");
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any())).thenReturn(
                new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "configure in CRS780", List.of()),
                        usage,
                        "{}"
                )
        );
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        comprehendChatService.chat(baseRequest("purchase settings"));

        ArgumentCaptor<OpenAIUsage> usageCaptor = ArgumentCaptor.forClass(OpenAIUsage.class);
        verify(chatPersistenceService).persistChat(
                eq("tenant1"),
                eq("user1"),
                eq("session1"),
                eq("purchase settings"),
                eq("purchase settings"),
                eq("configure in CRS780"),
                usageCaptor.capture(),
                eq("rag"),
                eq(false),
                eq("ready_for_grounding"),
                eq(88),
                isNull(),
                any(),
                eq(ChatMode.AUTO)
        );
        assertEquals(100, usageCaptor.getValue().getPromptTokens());
        assertEquals(50, usageCaptor.getValue().getCompletionTokens());
        assertEquals(150, usageCaptor.getValue().getTotalTokens());
        verify(tenantQuotaService).recordUsage(eq("tenant1"), eq(150), anyString());
    }

    @Test
    void extractUsage_parsesOpenAiResponse() {
        OpenAIService service = new OpenAIService(null, null, null);
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", 11);
        usage.put("completion_tokens", 22);
        usage.put("total_tokens", 33);
        response.put("usage", usage);

        OpenAIUsage parsed = service.extractUsage(response, "gpt-4.1");
        assertEquals(11, parsed.getPromptTokens());
        assertEquals(22, parsed.getCompletionTokens());
        assertEquals(33, parsed.getTotalTokens());
        assertEquals("gpt-4.1", parsed.getModel());
    }

    @Test
    void documentationRoute_queryRewriteDisabled_usesSanitizedQueryOnly() {
        ReflectionTestUtils.setField(comprehendChatService, "queryRewriteEnabled", false);
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("pricing issue")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        ChatResponse fallback = new ChatResponse("fallback", false);
        fallback.setActionTaken("gpt_infor");
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        comprehendChatService.chat(baseRequest("pricing issue"));

        verify(openAIService, never()).rewriteQueries(anyString());
        verify(openAIService, never()).rewriteQueries(any(), anyString());
        verify(pythonRagService).retrieve(eq("pricing issue"), eq(List.of("pricing issue")), any(), any(), any(), any());
    }

    @Test
    void documentationRoute_queryRewriteEnabled_callsSpringRewriteAndRetrieve() {
        ReflectionTestUtils.setField(comprehendChatService, "queryRewriteEnabled", true);
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("pricing issue")).thenReturn(new PythonRouteResponse("rag"));

        List<String> rewritten = List.of("customer pricing configuration", "price list setup");
        OpenAIUsage rewriteUsage = new OpenAIUsage(8, 4, 12, "gpt-4.1");
        when(openAIService.rewriteQueries(any(), eq("pricing issue"))).thenReturn(new QueryRewriteResult(rewritten, rewriteUsage));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage answerUsage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse fallback = new ChatResponse("fallback", false);
        fallback.setActionTaken("gpt_infor");
        fallback.setOpenAiUsage(answerUsage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("pricing issue"));

        verify(openAIService).rewriteQueries(any(), eq("pricing issue"));
        verify(pythonRagService).retrieve(
                eq("pricing issue"),
                eq(List.of("pricing issue", "customer pricing configuration", "price list setup")),
                any(),
                any(),
                any(),
                any()
        );
        assertEquals(13, response.getOpenAiUsage().getPromptTokens());
        assertEquals(9, response.getOpenAiUsage().getCompletionTokens());
        assertEquals(22, response.getOpenAiUsage().getTotalTokens());
    }

    @Test
    void ragRoute_neverCallsLex() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to configure dispatch policy")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        ChatResponse fallback = new ChatResponse("doc answer", false);
        fallback.setActionTaken("gpt_infor");
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        comprehendChatService.chat(baseRequest("how to configure dispatch policy"));

        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void liveRoute_lexDisabled_usesPythonChat() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(false);
        when(pythonRagService.route("show customer C001")).thenReturn(new PythonRouteResponse("live"));
        when(pythonRagService.query(any())).thenAnswer(invocation -> {
            com.ai.openai_api_service.model.python_rag.PythonQueryResponse response =
                    new com.ai.openai_api_service.model.python_rag.PythonQueryResponse();
            response.setReply("legacy live answer");
            response.setActionTaken("read");
            return response;
        });
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer C001"));

        assertEquals("legacy live answer", response.getReply());
        verify(pythonRagService).query(any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void liveRoute_lexElicitSlot_returnsPrompt() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("show customer details")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of(),
                List.of("What is the customer number?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer details")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer details"));

        assertEquals("What is the customer number?", response.getReply());
        assertEquals("lex_elicit_slot", response.getActionTaken());
        assertEquals("GetCustomer", response.getLexIntent());
        assertEquals("CustomerNumber", response.getLexSlotToElicit());
        assertNull(response.getM3Request());
        verify(pythonRagService, never()).query(any());
        verify(pythonRagService, never()).executeLiveIntent(anyString(), any());
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
    }

    @Test
    void liveRoute_lexElicitSlot_persistsRequestedInformationToLexSession() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Show address of customer")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of(),
                List.of("What is the customer number?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Show address of customer")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Show address of customer"));

        assertEquals("lex_elicit_slot", response.getActionTaken());
        verify(lexService).putSessionAttributes(
                eq("tenant1:user1:session1"),
                eq(Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, "ADDRESS"))
        );
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
    }

    @Test
    void liveRoute_lexElicitIntent_returnsLexClarificationAndMarksPending() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("give me orders having order type F10"))
                .thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        String clarification =
                "Which of the following options did you mean? Search Purchase Order or Search Distribution Order";
        LexRecognizeResult lexResult = new LexRecognizeResult(
                null,
                null,
                "ElicitIntent",
                null,
                Map.of(),
                List.of(clarification)
        );
        when(lexService.recognizeText("tenant1:user1:session1", "give me orders having order type F10"))
                .thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("give me orders having order type F10"));

        assertEquals(clarification, response.getReply());
        assertEquals("lex_elicit_intent", response.getActionTaken());
        assertEquals("ElicitIntent", response.getLexDialogAction());
        assertNull(response.getLexIntent());
        assertNull(response.getM3Request());
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isPresent());
        verify(pythonRagService, never()).query(any());
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
        verify(guidedSearchService, never()).start(anyString(), any());
    }

    @Test
    void pendingLex_elicitIntentReply_skipsPythonRouteAndCallsSameLexSession() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        pendingLexSessionService.markPending("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchPurchaseOrder",
                "InProgress",
                "ElicitSlot",
                "SupplierNumber",
                Map.of(),
                List.of("What supplier do you want to search for?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Search Purchase Order"))
                .thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Search Purchase Order"));

        assertEquals("What supplier do you want to search for?", response.getReply());
        assertEquals("lex_elicit_slot", response.getActionTaken());
        assertEquals("SearchPurchaseOrder", response.getLexIntent());
        verify(pythonRagService, never()).route(anyString());
        verify(lexService).recognizeText("tenant1:user1:session1", "Search Purchase Order");
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isPresent());
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
    }

    @Test
    void liveRoute_lexReadyForFulfillment_callsFulfillment() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("show customer CSU001")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "CSU001"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer CSU001")).thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", "CSU001"));
        ChatResponse fulfillResponse = new ChatResponse("Looking up customer CSU001...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("show customer CSU001"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer CSU001"));

        assertEquals("read", response.getActionTaken());
        assertEquals("Looking up customer CSU001...", response.getReply());
        assertEquals(List.of(RequestedInformationResolver.FULL), response.getRequestedInformation());
        assertNotNull(response.getM3Request());
        assertTrue(response.getM3Request().isExecute());
        assertEquals("CRS610MI", response.getM3Request().getProgram());
        assertEquals("GetBasicData", response.getM3Request().getTransaction());
        assertEquals("CSU001", response.getM3Request().getParams().get("CUNO"));
        assertNull(response.getM3Data());
        verify(lexFulfillmentService).fulfillOutcome(eq(lexResult), eq("show customer CSU001"), any());
        verify(pythonRagService, never()).query(any());
        verify(pythonRagService, never()).executeLiveIntent(anyString(), any());
        String expectedHistorySummary =
                "Viewed customer CSU001.\n\n" + LiveHistorySummaryBuilder.FOOTER;
        verify(chatPersistenceService).persistChat(
                eq("tenant1"),
                eq("user1"),
                eq("session1"),
                eq("show customer CSU001"),
                eq("show customer CSU001"),
                eq(expectedHistorySummary),
                nullable(OpenAIUsage.class),
                eq("read"),
                eq(false),
                isNull(),
                isNull(),
                eq(new LiveHistoryAuditMetadata("GetCustomer", "Customer", "CSU001")),
                isNull(),
                eq(ChatMode.AUTO)
        );
    }

    @Test
    void liveRoute_lexReadyForFulfillment_usesSessionRequestedInformationOnSlotReply() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Y11100")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of(),
                Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, "ADDRESS")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Y11100")).thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", "Y11100"));
        ChatResponse fulfillResponse = new ChatResponse("Looking up customer Y11100...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Y11100"));

        assertEquals("read", response.getActionTaken());
        assertEquals(List.of(RequestedInformationResolver.ADDRESS), response.getRequestedInformation());
        assertNotNull(response.getM3Request());
        assertEquals("Y11100", response.getM3Request().getParams().get("CUNO"));
    }

    @Test
    void liveRoute_lexReadyForFulfillment_addressInSameTurn() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Show address of customer Y11100")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Show address of customer Y11100"))
                .thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", "Y11100"));
        ChatResponse fulfillResponse = new ChatResponse("Looking up customer Y11100...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("Show address of customer Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Show address of customer Y11100"));

        assertEquals(List.of(RequestedInformationResolver.ADDRESS), response.getRequestedInformation());
    }

    @Test
    void liveRoute_lexFallbackIntent_returnsClarification() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("show customer")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "FallbackIntent",
                "Fulfilled",
                null,
                null,
                Map.of(),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer"));

        assertEquals(ComprehendChatService.LEX_FALLBACK_CLARIFICATION_MESSAGE, response.getReply());
        assertEquals("lex_fallback", response.getActionTaken());
        assertNull(response.getM3Request());
        assertNull(response.getRetrievalReason());
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
        verify(pythonRagService, never()).query(any());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(pythonRagService, never()).executeLiveIntent(anyString(), any());
        verify(openAIService, never()).chatWithRagContext(any(), anyList(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void liveRoute_unexpectedLexState_returnsClarification() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("show customer Y00111")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ConfirmIntent",
                null,
                Map.of("CustomerNumber", "Y00111"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer Y00111")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer Y00111"));

        assertEquals(ComprehendChatService.LEX_FALLBACK_CLARIFICATION_MESSAGE, response.getReply());
        assertEquals("lex_fallback", response.getActionTaken());
        assertNull(response.getM3Request());
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
        verify(pythonRagService, never()).query(any());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), anyList(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void liveRoute_searchCriteriaMissing_startsGuidedSearchMenu() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Show customer orders")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of(),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Show customer orders")).thenReturn(lexResult);

        ChatResponse missing = new ChatResponse("Unable to process...", false);
        missing.setActionTaken("search_criteria_missing");
        missing.setLexIntent("SearchCustomerOrder");
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("Show customer orders"), any()))
                .thenReturn(new LexFulfillmentOutcome(missing, List.of()));

        ChatResponse menu = new ChatResponse("Please select a search field.", false);
        menu.setActionTaken(GuidedSearchService.ACTION_SELECT_FIELD);
        menu.setLexIntent("SearchCustomerOrder");
        menu.setCollectingTool("SearchCustomerOrder");
        when(guidedSearchService.start(eq("SearchCustomerOrder"), any())).thenReturn(menu);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Show customer orders"));

        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, response.getActionTaken());
        verify(guidedSearchService).start(eq("SearchCustomerOrder"), any());
        verify(lexService).recognizeText(anyString(), anyString());
    }

    @Test
    void liveRoute_readyForFulfillmentWithCriteria_doesNotStartGuided() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Show customer orders with highest status 77"))
                .thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("HighestStatus", "77"),
                List.of()
        );
        when(lexService.recognizeText(anyString(), eq("Show customer orders with highest status 77")))
                .thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(
                true, "OIS100MI", "SearchHead", Map.of("SQRY", "ORST:'77'"));
        ChatResponse search = new ChatResponse("Processing...", false);
        search.setActionTaken("search");
        search.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), anyString(), any()))
                .thenReturn(new LexFulfillmentOutcome(
                        search,
                        List.of(new SearchCriterion("ORST", "77"))
                ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(
                baseRequest("Show customer orders with highest status 77"));

        assertEquals("search", response.getActionTaken());
        verify(guidedSearchService, never()).start(anyString(), any());
    }

    @Test
    void liveRoute_activeGuidedSession_delegatesTurnAndSkipsLexWhenNotAbandoned() {
        stubQuotaAllowed();
        stubSanitize();

        GuidedSearchState state = GuidedSearchState.selectField("SearchCustomerOrder");
        when(guidedSearchSessionService.find(any())).thenReturn(Optional.of(state));

        ChatResponse collect = new ChatResponse("Please enter Highest Status.", false);
        collect.setActionTaken(GuidedSearchService.ACTION_COLLECT_VALUE);
        when(guidedSearchService.handleTurn(any(), eq(state), eq("order 1000001234")))
                .thenReturn(new GuidedSearchService.GuidedTurnResult(collect, false));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("order 1000001234"));

        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, response.getActionTaken());
        verify(guidedSearchService).handleTurn(any(), eq(state), eq("order 1000001234"));
        verify(pythonRagService, never()).route(anyString());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(guidedSearchService, never()).start(anyString(), any());
    }

    @Test
    void guidedSession_cancelSkipsPythonRouteAndLex() {
        stubQuotaAllowed();
        stubSanitize();

        GuidedSearchState state = GuidedSearchState.selectField("SearchCustomerOrder");
        when(guidedSearchSessionService.find(any())).thenReturn(Optional.of(state));

        ChatResponse cancelled = new ChatResponse("Guided search cancelled. How else can I help?", false);
        cancelled.setActionTaken(GuidedSearchService.ACTION_CANCELLED);
        when(guidedSearchService.handleTurn(any(), eq(state), eq("cancel")))
                .thenReturn(new GuidedSearchService.GuidedTurnResult(cancelled, false));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("cancel"));

        assertEquals(GuidedSearchService.ACTION_CANCELLED, response.getActionTaken());
        verify(guidedSearchService).handleTurn(any(), eq(state), eq("cancel"));
        verify(pythonRagService, never()).route(anyString());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), anyList(), any());
    }

    @Test
    void guidedSession_invalidInputStaysGuidedAndSkipsRoute() {
        stubQuotaAllowed();
        stubSanitize();

        GuidedSearchState state = GuidedSearchState.selectField("SearchCustomerOrder");
        when(guidedSearchSessionService.find(any())).thenReturn(Optional.of(state));

        ChatResponse retry = new ChatResponse("I couldn't match that to a searchable field.", false);
        retry.setActionTaken(GuidedSearchService.ACTION_SELECT_FIELD);
        when(guidedSearchService.handleTurn(any(), eq(state), eq("something")))
                .thenReturn(new GuidedSearchService.GuidedTurnResult(retry, false));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("something"));

        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, response.getActionTaken());
        verify(guidedSearchService).handleTurn(any(), eq(state), eq("something"));
        verify(pythonRagService, never()).route(anyString());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void guidedSessionEnded_nextQuestionUsesNormalRagRoute() {
        stubQuotaAllowed();
        stubSanitize();
        when(guidedSearchSessionService.find(any())).thenReturn(Optional.empty());
        when(pythonRagService.route("What is OIS100?")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(42);
        retrieval.setMaxScore(0.62f);
        ChunkItem chunk = new ChunkItem(
                "OIS100 is a customer order program.",
                0.62f,
                "OIS100",
                "http://example.com/ois100",
                List.of("OIS100"),
                null,
                null,
                null,
                null
        );
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(10, 20, 30, "gpt-4.1");
        GroundedRagResult grounded = new GroundedRagResult(RagStatus.FULL, "OIS100 is used for customer order entry.", List.of());
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenReturn(new GroundedRagCallResult(grounded, usage, "{\"status\":\"FULL\"}"));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("What is OIS100?"));

        assertEquals("OIS100 is used for customer order entry.", response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route("What is OIS100?");
        verify(guidedSearchService, never()).handleTurn(any(), any(), anyString());
    }

    @Test
    void liveRoute_activeGuidedSession_abandonsToLexForNewSearchRequest() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("actually search purchase orders")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        GuidedSearchState state = GuidedSearchState.selectField("SearchCustomerOrder");
        when(guidedSearchSessionService.find(any()))
                .thenReturn(Optional.of(state))
                .thenReturn(Optional.empty());
        when(guidedSearchService.handleTurn(any(), eq(state), eq("actually search purchase orders")))
                .thenReturn(new GuidedSearchService.GuidedTurnResult(null, true));

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchPurchaseOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("Supplier", "S00001"),
                List.of()
        );
        when(lexService.recognizeText(anyString(), eq("actually search purchase orders"))).thenReturn(lexResult);

        ChatResponse fulfilled = new ChatResponse("Processing your request...", false);
        fulfilled.setActionTaken("search");
        fulfilled.setM3Request(new M3RequestDto(
                true, "PPS200MI", "SearchHead", Map.of("SQRY", "SUNO:'S00001'")
        ));
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("actually search purchase orders"), any()))
                .thenReturn(new LexFulfillmentOutcome(
                        fulfilled,
                        List.of(new SearchCriterion("SUNO", "S00001"))
                ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("actually search purchase orders"));

        assertEquals("search", response.getActionTaken());
        verify(guidedSearchService).handleTurn(any(), eq(state), eq("actually search purchase orders"));
        verify(pythonRagService).route("actually search purchase orders");
        verify(lexService).recognizeText(anyString(), eq("actually search purchase orders"));
    }

    @Test
    void pendingLex_elicitSlot_storesPendingMarker() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Show customer credit limit")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomerFinancial",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of(),
                List.of("Please provide the customer number.")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Show customer credit limit")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Show customer credit limit"));

        assertEquals("lex_elicit_slot", response.getActionTaken());
        assertEquals("GetCustomerFinancial", response.getLexIntent());
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isPresent());
    }

    @Test
    void pendingLex_slotReply_skipsPythonRouteAndCallsLex() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        pendingLexSessionService.markPending("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomerFinancial",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of(),
                Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, "CREDIT_LIMIT")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Y11100")).thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetFinancial", Map.of("CUNO", "Y11100"));
        ChatResponse fulfillResponse = new ChatResponse("Credit limit for Y11100...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Y11100"));

        assertEquals("read", response.getActionTaken());
        verify(pythonRagService, never()).route(anyString());
        verify(lexService).recognizeText("tenant1:user1:session1", "Y11100");
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isEmpty());
    }

    @Test
    void pendingLex_fulfillmentClears_andNextTurnRoutes() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        pendingLexSessionService.markPending("tenant1:user1:session1");

        LexRecognizeResult fulfillLex = new LexRecognizeResult(
                "GetCustomerFinancial",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Y11100")).thenReturn(fulfillLex);
        ChatResponse fulfillResponse = new ChatResponse("ok", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(new M3RequestDto(true, "CRS610MI", "GetFinancial", Map.of("CUNO", "Y11100")));
        when(lexFulfillmentService.fulfillOutcome(eq(fulfillLex), eq("Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        comprehendChatService.chat(baseRequest("Y11100"));
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isEmpty());

        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), anyList(), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "docs", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));

        comprehendChatService.chat(baseRequest("how to create customer"));

        verify(pythonRagService).route("how to create customer");
    }

    @Test
    void pendingLex_unrelatedMessageAfterCompletion_invokesRoute() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isEmpty());

        when(pythonRagService.route("Explain CRS610")).thenReturn(new PythonRouteResponse("rag"));
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), anyList(), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "CRS610 docs", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Explain CRS610"));

        assertEquals("CRS610 docs", response.getReply());
        verify(pythonRagService).route("Explain CRS610");
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void pendingLex_ttlExpiry_resumesPythonRouting() throws InterruptedException {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        pendingLexSessionService = new InMemoryPendingLexSessionService(0);
        ReflectionTestUtils.setField(comprehendChatService, "pendingLexSessionService", pendingLexSessionService);
        pendingLexSessionService.markPending("tenant1:user1:session1");
        Thread.sleep(5);

        when(pythonRagService.route("Y11100")).thenReturn(new PythonRouteResponse("rag"));
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), anyList(), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "rag after ttl", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Y11100"));

        assertEquals("rag after ttl", response.getReply());
        verify(pythonRagService).route("Y11100");
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void pendingLex_fallbackIntent_clearsPending() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        pendingLexSessionService.markPending("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "FallbackIntent",
                "Failed",
                "Close",
                null,
                Map.of(),
                List.of("Sorry")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "nonsense")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("nonsense"));

        assertEquals("lex_fallback", response.getActionTaken());
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isEmpty());
        verify(pythonRagService, never()).route(anyString());
    }

    @Test
    void pendingLex_normalRagUnchanged_whenNoPending() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        when(pythonRagService.route("Explain CRS610")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        ChunkItem chunk = new ChunkItem(
                "chunk", 0.9f, "CRS610", "http://example.com", List.of("CRS610"), null, null, null, null
        );
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "CRS610 explanation", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Explain CRS610"));

        assertEquals("CRS610 explanation", response.getReply());
        verify(pythonRagService).route("Explain CRS610");
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isEmpty());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void pendingLex_oneShotLiveWithCuno_leavesPendingEmpty() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("Show customer Y11100 credit limit"))
                .thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomerFinancial",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Show customer Y11100 credit limit"))
                .thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetFinancial", Map.of("CUNO", "Y11100"));
        ChatResponse fulfillResponse = new ChatResponse("Credit limit...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(
                eq(lexResult), eq("Show customer Y11100 credit limit"), any()
        )).thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Show customer Y11100 credit limit"));

        assertEquals("read", response.getActionTaken());
        assertTrue(pendingLexSessionService.get("tenant1:user1:session1").isEmpty());
        verify(pythonRagService).route("Show customer Y11100 credit limit");
        verify(lexService).recognizeText("tenant1:user1:session1", "Show customer Y11100 credit limit");
    }

    @Test
    void modeAbsent_usesPythonRoute_autoBehavior() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        stubDocsGroundedPath("how to create customer", "grounded answer");
        when(chatPersistenceService.persistChat(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(99L);

        ChatRequest request = baseRequest("how to create customer");
        assertNull(request.getMode());

        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("grounded answer", response.getReply());
        assertEquals("rag", response.getActionTaken());
        assertEquals(99L, response.getRequestLogId());
        verify(pythonRagService).route("how to create customer");
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void modeAuto_pythonRouteLive_entersExistingLiveBranch() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("show customer details")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of(),
                List.of("What is the customer number?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer details")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("show customer details");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("lex_elicit_slot", response.getActionTaken());
        verify(pythonRagService).route("show customer details");
        verify(lexService).recognizeText("tenant1:user1:session1", "show customer details");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void modeAuto_pythonRouteRag_entersExistingDocsBranch() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        stubDocsGroundedPath("how to create customer", "docs answer");

        ChatRequest request = baseRequest("how to create customer");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("docs answer", response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route("how to create customer");
        verify(pythonRagService).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void modeAuto_pythonRouteNull_defaultsToRagDocsBranch() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("unknown topic")).thenReturn(null);
        stubDocsGroundedPath("unknown topic", "fallback docs");

        ChatRequest request = baseRequest("unknown topic");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("fallback docs", response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route("unknown topic");
        verify(pythonRagService).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void modeM3_pythonLive_entersLex() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("show customer Y00111")).thenReturn(new PythonRouteResponse("live"));
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of(),
                List.of("What is the customer number?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer Y00111")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("show customer Y00111");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("lex_elicit_slot", response.getActionTaken());
        verify(pythonRagService).route("show customer Y00111");
        verify(lexService).recognizeText("tenant1:user1:session1", "show customer Y00111");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void modeM3_pythonRag_safeCannedWhenRouterDisabled() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("how to create customer");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_M3_CLASSIFIER_ERROR_MESSAGE, response.getReply());
        assertEquals("m3_classifier_error", response.getActionTaken());
        verify(pythonRagService).route("how to create customer");
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(openAIService, never()).understandRequest(any(), anyString());
    }

    @Test
    void modeDocs_skipsPythonRoute_entersExistingDocsBranch() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        stubDocsGroundedPath("show customer C001", "forced docs answer");

        ChatRequest request = baseRequest("show customer C001");
        request.setMode(ChatMode.DOCS);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("forced docs answer", response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void pendingLex_modeDocs_pendingLexStillOwnsTurn() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        pendingLexSessionService.markPending("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomerFinancial",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of(),
                Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, "CREDIT_LIMIT")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Y11100")).thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetFinancial", Map.of("CUNO", "Y11100"));
        ChatResponse fulfillResponse = new ChatResponse("Credit limit for Y11100...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("Y11100");
        request.setMode(ChatMode.DOCS);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("read", response.getActionTaken());
        assertNotNull(response.getM3Request());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService).recognizeText("tenant1:user1:session1", "Y11100");
    }

    @Test
    void editLatest_persistsResolvedMode_returnsNewRequestLogId_andSupersedes() {
        stubQuotaAllowed();
        stubSanitize();
        when(chatPersistenceService.validateLatestActiveEdit(
                eq("tenant1"), eq("user1"), eq("session1"), eq(123L)
        )).thenReturn(10L);
        stubDocsGroundedPath("edited question", "edited answer");
        when(chatPersistenceService.persistChat(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), eq(ChatMode.DOCS)
        )).thenReturn(456L);
        when(chatPersistenceService.supersedeEditedRequest(123L, 456L, 10L)).thenReturn(true);

        ChatRequest request = baseRequest("edited question");
        request.setMode(ChatMode.DOCS);
        request.setEditOfRequestLogId(123L);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(456L, response.getRequestLogId());
        assertEquals("edited answer", response.getReply());
        verify(pythonRagService, never()).route(anyString());
        verify(chatPersistenceService).supersedeEditedRequest(123L, 456L, 10L);
        verify(chatPersistenceService).persistChat(
                eq("tenant1"),
                eq("user1"),
                eq("session1"),
                eq("edited question"),
                anyString(),
                eq("edited answer"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(ChatMode.DOCS)
        );
    }

    @Test
    void editLatest_doubleSubmitConflict_afterLoserHidden() {
        stubQuotaAllowed();
        stubSanitize();
        when(chatPersistenceService.validateLatestActiveEdit(
                eq("tenant1"), eq("user1"), eq("session1"), eq(123L)
        )).thenReturn(10L);

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(10);
        retrieval.setMaxScore(0.7f);
        ChunkItem chunk = new ChunkItem(
                "chunk text", 0.7f, "Title", "http://example.com", List.of(), null, null, null, null
        );
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, "edited answer", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));

        when(chatPersistenceService.persistChat(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(457L);
        when(chatPersistenceService.supersedeEditedRequest(123L, 457L, 10L)).thenReturn(false);

        ChatRequest request = baseRequest("edited question");
        request.setEditOfRequestLogId(123L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> comprehendChatService.chat(request)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(chatPersistenceService).supersedeEditedRequest(123L, 457L, 10L);
        verify(suggestionEngineService, never()).generateSuggestions(any());
    }

    @Test
    void editNonLatest_rejectedBeforeAi() {
        stubQuotaAllowed();
        when(chatPersistenceService.validateLatestActiveEdit(
                eq("tenant1"), eq("user1"), eq("session1"), eq(100L)
        )).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Only the latest active request can be edited"));

        ChatRequest request = baseRequest("should not run");
        request.setEditOfRequestLogId(100L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> comprehendChatService.chat(request)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(pythonRagService, never()).route(anyString());
        verify(chatPersistenceService, never()).persistChat(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void sessionRequestLimit_blocksBeforeAi() {
        stubQuotaAllowed();
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Session request limit reached (50)"
        )).when(chatPersistenceService).enforceSessionRequestLimit("tenant1", "user1", "session1");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> comprehendChatService.chat(baseRequest("blocked"))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(pythonRagService, never()).route(anyString());
        verify(chatPersistenceService, never()).validateLatestActiveEdit(
                anyString(), anyString(), anyString(), anyLong()
        );
    }

    @ParameterizedTest(name = "docs insufficient externalSourceEnabled={0} allowExternal={1}")
    @MethodSource("docsExternalSourceFlagStates")
    void docsInsufficient_externalSourceFlagStates(
            Boolean externalSourceEnabled,
            boolean allowExternal
    ) {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(RagStatus.INSUFFICIENT, "", List.of());
        ChatResponse fallbackResponse = new ChatResponse("General GPT answer", false);
        fallbackResponse.setActionTaken("gpt_infor");
        if (allowExternal) {
            when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallbackResponse);
        }
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("how to add KIT", externalSourceEnabled));

        if (allowExternal) {
            assertEquals("General GPT answer", response.getReply());
            assertEquals("gpt_infor", response.getActionTaken());
            verify(openAIService).chatWithoutPersistence(any(), any());
        } else {
            assertEquals(ComprehendChatService.DOCS_INSUFFICIENT_MESSAGE, response.getReply());
            assertEquals("rag", response.getActionTaken());
            verify(openAIService, never()).chatWithoutPersistence(any(), any());
        }
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
    }

    static Stream<Arguments> docsExternalSourceFlagStates() {
        return Stream.of(
                Arguments.of(null, true),
                Arguments.of(true, true),
                Arguments.of(false, false)
        );
    }

    @Test
    void docsExternalOn_insufficient_usesGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(RagStatus.INSUFFICIENT, "", List.of());

        OpenAIUsage fallbackUsage = new OpenAIUsage(50, 100, 150, "gpt-4.1");
        ChatResponse fallbackResponse = new ChatResponse("General GPT answer", false);
        fallbackResponse.setActionTaken("gpt_infor");
        fallbackResponse.setOpenAiUsage(fallbackUsage);
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallbackResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("how to add KIT", true));

        assertEquals("General GPT answer", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        verify(openAIService).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
    }

    @Test
    void docsExternalOn_nullField_treatedAsOn_insufficientUsesGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(RagStatus.INSUFFICIENT, "", List.of());

        ChatResponse fallbackResponse = new ChatResponse("General GPT answer", false);
        fallbackResponse.setActionTaken("gpt_infor");
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallbackResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = docsRequest("how to add KIT", null);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("General GPT answer", response.getReply());
        verify(openAIService).chatWithoutPersistence(any(), any());
    }

    @Test
    void docsExternalOff_full_returnsGroundedAnswerOnly() {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(RagStatus.FULL, "Document answer from CRS780", List.of());
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("purchase settings", false));

        assertEquals("Document answer from CRS780", response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
    }

    @Test
    void docsExternalOff_partial_noGapFill_usesContinuationMessage() {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(
                RagStatus.PARTIAL,
                "MNS204 appears in user settings documentation.",
                List.of("Functional purpose", "Business usage")
        );
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("What is MNS204 used for?", false));

        assertTrue(response.getReply().contains("MNS204 appears in user settings documentation."));
        assertTrue(response.getReply().contains(ComprehendChatService.DOCS_PARTIAL_CONTINUATION));
        assertEquals("rag", response.getActionTaken());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void docsExternalOff_insufficient_noGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(RagStatus.INSUFFICIENT, "", List.of());
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("how to add KIT", false));

        assertEquals(ComprehendChatService.DOCS_INSUFFICIENT_MESSAGE, response.getReply());
        assertEquals("rag", response.getActionTaken());
        assertEquals("rag_no_answer_fallback", response.getRetrievalReason());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatGapFill(any(), any(), any(), any());
    }

    @Test
    void docsExternalOff_retrievalNotReady_noGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setMaxScore(0.2f);
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("unknown topic", false));

        assertEquals(ComprehendChatService.DOCS_INSUFFICIENT_MESSAGE, response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), any(), any());
    }

    @Test
    void docsExternalOff_retrievalException_noGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any()))
                .thenThrow(new OpenAIException("retrieval failed", 500));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("purchase settings", false));

        assertEquals(ComprehendChatService.DOCS_INSUFFICIENT_MESSAGE, response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void docsExternalOff_groundedParseFail_noGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        ChunkItem chunk = new ChunkItem("chunk", 0.7f, "Title", "http://example.com", List.of(), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenThrow(new OpenAIException("parse failed", 500));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("purchase settings", false));

        assertEquals(ComprehendChatService.DOCS_INSUFFICIENT_MESSAGE, response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void auto_insufficient_ignoresExternalSourceDisabled_usesGeneralGpt() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to add KIT")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        ChunkItem chunk = new ChunkItem("chunk", 0.64f, "Title", "http://example.com", List.of("OIS100"), null, null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any())).thenReturn(
                new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.INSUFFICIENT, "", List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                )
        );

        ChatResponse fallbackResponse = new ChatResponse("Auto general GPT", false);
        fallbackResponse.setActionTaken("gpt_infor");
        when(openAIService.chatWithoutPersistence(any(), any())).thenReturn(fallbackResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("how to add KIT");
        request.setExternalSourceEnabled(false);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("Auto general GPT", response.getReply());
        verify(openAIService).chatWithoutPersistence(any(), any());
    }

    @Test
    void docsExternalOn_partial_usesGapFillWhenEnabled() {
        stubQuotaAllowed();
        stubSanitize();
        stubDocsRetrievalGrounded(
                RagStatus.PARTIAL,
                "MNS204 appears in user settings documentation.",
                List.of("Functional purpose")
        );
        ChatResponse gapResponse = new ChatResponse("Functional purpose: ...", false);
        gapResponse.setOpenAiUsage(new OpenAIUsage(1, 1, 2, "gpt"));
        when(openAIService.chatGapFill(any(), anyString(), anyList(), any())).thenReturn(gapResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(docsRequest("What is MNS204 used for?", true));

        assertTrue(response.getReply().contains("Functional purpose: ..."));
        verify(openAIService).chatGapFill(any(), anyString(), anyList(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    private ChatRequest docsRequest(String message, Boolean externalSourceEnabled) {
        ChatRequest request = baseRequest(message);
        request.setMode(ChatMode.DOCS);
        request.setExternalSourceEnabled(externalSourceEnabled);
        return request;
    }

    private void stubDocsRetrievalGrounded(RagStatus status, String answer, List<String> missingTopics) {
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(10);
        retrieval.setMaxScore(0.7f);
        ChunkItem chunk = new ChunkItem(
                "chunk text", 0.7f, "Title", "http://example.com", List.of(), null, null, null, null
        );
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(status, answer, missingTopics),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));
    }

    private void stubDocsGroundedPath(String unusedMessage, String groundedReply) {
        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(10);
        retrieval.setMaxScore(0.7f);
        ChunkItem chunk = new ChunkItem(
                "chunk text", 0.7f, "Title", "http://example.com", List.of(), null, null, null, null
        );
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)), any()))
                .thenReturn(new GroundedRagCallResult(
                        new GroundedRagResult(RagStatus.FULL, groundedReply, List.of()),
                        new OpenAIUsage(1, 1, 2, "gpt"),
                        "{}"
                ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));
    }

    private void stubQuotaAllowed() {
        when(tenantQuotaService.checkBeforeChat(anyString()))
                .thenReturn(new QuotaCheckResult(true, new TokenUsageDto(0, 1000, 1000), null));
    }

    private void stubSanitize() {
        // Protection stubs are installed in setUp(); kept for call-site clarity.
    }

    private void stubSanitizeWithPii(String original, String sanitizedText) {
        lenient().when(piiProtectionService.anonymize(original)).thenReturn(sanitizedText);
        lenient().doAnswer(inv -> {
            ProtectionSession session = inv.getArgument(0);
            if (session != null) {
                session.applyPiiSanitizedText(sanitizedText);
            }
            return session;
        }).when(piiProtectionService).protect(any());
    }

    private ChatRequest baseRequest(String message) {
        ChatRequest request = new ChatRequest();
        request.setTenantCode("tenant1");
        request.setUserId("user1");
        request.setSessionId("session1");
        request.setUserMessage(message);
        return request;
    }

    private void enableRequestRouter() {
        ReflectionTestUtils.setField(comprehendChatService, "requestRouterEnabled", true);
    }

    private void stubPythonRoute(String message, String route) {
        when(pythonRagService.route(message)).thenReturn(new PythonRouteResponse(route));
    }

    @Test
    void requestRouter_hi_returnsConversationalWithoutRetrieval() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("hi", "rag");
        when(openAIService.understandRequest(any(), eq("hi"))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.CONVERSATIONAL,
                "Hi! I'm the M3 AI Assistant. How can I help you?",
                List.of(),
                new OpenAIUsage(3, 4, 7, "gpt")
        ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("hi"));

        assertEquals("Hi! I'm the M3 AI Assistant. How can I help you?", response.getReply());
        assertEquals("conversational", response.getActionTaken());
        verify(pythonRagService).route("hi");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(openAIService, never()).rewriteQueries(any(), anyString());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void requestRouter_gls037_retrievesWithRouterQueries() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("what is GLS037", "rag");
        List<String> queries = List.of("GLS037 accounting identities", "GLS037 Infor M3");
        when(openAIService.understandRequest(any(), eq("what is GLS037"))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.RAG,
                "",
                queries,
                new OpenAIUsage(2, 2, 4, "gpt")
        ));
        stubDocsGroundedPath("what is GLS037", "GLS037 is Accounting Identity.");

        ChatResponse response = comprehendChatService.chat(baseRequest("what is GLS037"));

        assertEquals("GLS037 is Accounting Identity.", response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route("what is GLS037");
        verify(openAIService, never()).rewriteQueries(any(), anyString());
        ArgumentCaptor<List<String>> queryCaptor = ArgumentCaptor.forClass(List.class);
        verify(pythonRagService).retrieve(eq("what is GLS037"), queryCaptor.capture(), any(), any(), any(), any());
        assertTrue(queryCaptor.getValue().contains("GLS037 accounting identities"));
    }

    @Test
    void requestRouter_getCustomerAuto_goesLiveNotRetrieve() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("get customer ABC", "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of(),
                List.of("What is the customer number?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "get customer ABC")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("get customer ABC"));

        assertEquals("What is the customer number?", response.getReply());
        verify(pythonRagService).route("get customer ABC");
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_live_anonymizesForPersistOnly_skipsPlanner() {
        enableRequestRouter();
        stubQuotaAllowed();
        String original = "fetch customer Y11100 for John";
        String sanitized = "fetch customer Y11100 for [Name]";
        stubSanitizeWithPii(original, sanitized);
        stubPythonRoute(original, "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", original)).thenReturn(lexResult);
        ChatResponse fulfillResponse = new ChatResponse("Customer Y11100", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", "Y11100")));
        when(lexFulfillmentService.fulfillOutcome(any(), eq(original), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertEquals("read", response.getActionTaken());
        verify(lexService).recognizeText("tenant1:user1:session1", original);
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(piiProtectionService).anonymize(original);
        verify(chatPersistenceService).persistChat(
                eq("tenant1"),
                eq("user1"),
                eq("session1"),
                eq(original),
                eq(sanitized),
                any(),
                any(),
                eq("read"),
                eq(true),
                any(),
                any(),
                any(),
                any(),
                eq(ChatMode.AUTO)
        );
    }

    @Test
    void requestRouter_getCustomerDocs_liveSteerNoRetrieve() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        when(openAIService.understandRequest(any(), eq("get customer ABC"))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.LIVE_M3,
                "",
                List.of(),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("get customer ABC");
        request.setMode(ChatMode.DOCS);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_DOCS_LIVE_STEER_MESSAGE, response.getReply());
        assertEquals("docs_live_steer", response.getActionTaken());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_docsExternalOff_nonM3_usesCannedInsufficient() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        when(openAIService.understandRequest(any(), eq("what is AWS"))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.NON_M3,
                "I mainly support Infor M3 and CloudSuite questions.",
                List.of(),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("what is AWS");
        request.setMode(ChatMode.DOCS);
        request.setExternalSourceEnabled(false);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DOCS_INSUFFICIENT_MESSAGE, response.getReply());
        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any(), any());
    }

    @Test
    void requestRouter_disabled_stillUsesPythonRoute() {
        ReflectionTestUtils.setField(comprehendChatService, "requestRouterEnabled", false);
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        stubDocsGroundedPath("how to create customer", "grounded");

        comprehendChatService.chat(baseRequest("how to create customer"));

        verify(pythonRagService).route("how to create customer");
        verify(openAIService, never()).understandRequest(any(), anyString());
    }

    static Stream<Arguments> m3ConversationalMessages() {
        return Stream.of(
                Arguments.of("hi", "Hello! How can I help?"),
                Arguments.of("how are you?", "I'm doing well. How can I help with live M3 data?"),
                Arguments.of("what is your name?", "I'm your Infor M3 assistant.")
        );
    }

    @ParameterizedTest
    @MethodSource("m3ConversationalMessages")
    void requestRouter_m3Conversational_skipsLex(String message, String reply) {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute(message, "rag");
        when(openAIService.understandRequest(any(), eq(message))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.CONVERSATIONAL,
                reply,
                List.of(),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest(message);
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(reply, response.getReply());
        assertEquals("conversational", response.getActionTaken());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(pythonRagService).route(message);
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_m3ShowCustomer_goesLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("Show customer Y00111", "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "InProgress",
                "ElicitSlot",
                "CustomerNumber",
                Map.of("CustomerNumber", "Y00111"),
                List.of("Here are the customer details.")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Show customer Y00111")).thenReturn(lexResult);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("Show customer Y00111");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("Here are the customer details.", response.getReply());
        verify(lexService).recognizeText("tenant1:user1:session1", "Show customer Y00111");
        verify(pythonRagService).route("Show customer Y00111");
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_m3Rag_returnsDocsSteerNotLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("what is OIS100?", "rag");
        when(openAIService.understandRequest(any(), eq("what is OIS100?"))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.RAG,
                "",
                List.of("OIS100"),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("what is OIS100?");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_M3_DOCS_STEER_MESSAGE, response.getReply());
        assertEquals("m3_docs_steer", response.getActionTaken());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(pythonRagService).route("what is OIS100?");
    }

    @Test
    void requestRouter_m3NonM3_returnsNonM3SteerNotGeneralRedirect() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("tell me a joke", "rag");
        when(openAIService.understandRequest(any(), eq("tell me a joke"))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.NON_M3,
                "I mainly support Infor M3 and CloudSuite questions.",
                List.of(),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("tell me a joke");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_M3_NON_M3_MESSAGE, response.getReply());
        assertEquals("m3_non_m3_steer", response.getActionTaken());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(pythonRagService).route("tell me a joke");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_m3PythonError_safeCannedNotLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("Show customer Y00111")).thenThrow(new RuntimeException("route down"));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("Show customer Y00111");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_M3_CLASSIFIER_ERROR_MESSAGE, response.getReply());
        assertEquals("m3_classifier_error", response.getActionTaken());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(openAIService, never()).understandRequest(any(), anyString());
    }

    @Test
    void pendingLex_modeM3_pendingLexStillOwnsTurnWithoutUnderstand() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        pendingLexSessionService.markPending("tenant1:user1:session1");

        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomerFinancial",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y00111"),
                List.of(),
                Map.of(LexRecognizeResult.ATTR_REQUESTED_INFORMATION, "CREDIT_LIMIT")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "Y00111")).thenReturn(lexResult);

        M3RequestDto m3Request = new M3RequestDto(true, "CRS610MI", "GetFinancial", Map.of("CUNO", "Y00111"));
        ChatResponse fulfillResponse = new ChatResponse("Credit limit for Y00111...", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(m3Request);
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("Y00111"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("Y00111");
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("read", response.getActionTaken());
        assertNotNull(response.getM3Request());
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService).recognizeText("tenant1:user1:session1", "Y00111");
    }

    @Test
    void requestRouter_tripPlanningAuto_redirectsWithoutRetrieveOrLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("tell me about trip planning", "rag");
        stubUnderstand("tell me about trip planning", RequestUnderstandType.NON_M3,
                "I mainly support Infor M3 and CloudSuite questions.");
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("tell me about trip planning");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("general_redirect", response.getActionTaken());
        verify(pythonRagService).route("tell me about trip planning");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_tripPlanningDocs_doesNotRetrieveOrCallLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubUnderstand("tell me about trip planning", RequestUnderstandType.NON_M3,
                "I mainly support Infor M3 and CloudSuite questions.");
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("tell me about trip planning");
        request.setMode(ChatMode.DOCS);
        ChatResponse response = comprehendChatService.chat(request);

        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        assertEquals("general_redirect", response.getActionTaken());
    }

    @Test
    void requestRouter_weatherAuto_redirectsWithoutRetrieve() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("what is the weather", "rag");
        stubUnderstand("what is the weather", RequestUnderstandType.NON_M3,
                "I mainly support Infor M3 and CloudSuite questions.");
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("what is the weather"));

        assertEquals("general_redirect", response.getActionTaken());
        verify(pythonRagService).route("what is the weather");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_ois100Auto_retrievesNotLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("what is OIS100?", "rag");
        stubUnderstandRag("what is OIS100?", List.of("OIS100"));
        stubDocsGroundedPath("what is OIS100?", "OIS100 is Customer Order.");

        ChatRequest request = baseRequest("what is OIS100?");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route("what is OIS100?");
        verify(pythonRagService).retrieve(eq("what is OIS100?"), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_ois100Docs_retrievesNotLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubUnderstandRag("what is OIS100?", List.of("OIS100"));
        stubDocsGroundedPath("what is OIS100?", "OIS100 is Customer Order.");

        ChatRequest request = baseRequest("what is OIS100?");
        request.setMode(ChatMode.DOCS);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_fetchCustomerAuto_callsLexNotRetrieve() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("fetch customer Y11100", "live");
        stubLexGetCustomerReady("fetch customer Y11100", "Y11100");

        ChatRequest request = baseRequest("fetch customer Y11100");
        request.setMode(ChatMode.AUTO);
        comprehendChatService.chat(request);

        verify(pythonRagService).route("fetch customer Y11100");
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(lexService).recognizeText("tenant1:user1:session1", "fetch customer Y11100");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_fetchCustomerDocs_docsLiveSteerNoRetrieve() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubUnderstandLive("fetch customer Y11100");
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("fetch customer Y11100");
        request.setMode(ChatMode.DOCS);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_DOCS_LIVE_STEER_MESSAGE, response.getReply());
        assertEquals("docs_live_steer", response.getActionTaken());
        verify(pythonRagService, never()).route(anyString());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_greetingPlusHowToCreateCustomerOrder_retrievesNotLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        String message = "Hi, how do I create a customer order?";
        stubPythonRoute(message, "rag");
        stubUnderstandRag(message, List.of("create customer order"));
        stubDocsGroundedPath(message, "How to create a customer order.");

        ChatRequest request = baseRequest(message);
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route(message);
        verify(pythonRagService).retrieve(eq(message), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_greetingPlusShowCustomer_pythonLiveLexSkipsPlanner() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        String message = "Hi, show customer C10001";
        stubPythonRoute(message, "live");
        stubLexGetCustomerReady(message, "C10001");

        ChatRequest request = baseRequest(message);
        request.setMode(ChatMode.AUTO);
        comprehendChatService.chat(request);

        verify(pythonRagService).route(message);
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(lexService).recognizeText("tenant1:user1:session1", message);
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_m3GreetingPlusHowTo_docsSteerNoQdrant() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        String message = "Hi, how do I create a custom MI?";
        stubPythonRoute(message, "rag");
        stubUnderstandRag(message, List.of("create custom MI"));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest(message);
        request.setMode(ChatMode.M3);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals(ComprehendChatService.DEFAULT_M3_DOCS_STEER_MESSAGE, response.getReply());
        assertEquals("m3_docs_steer", response.getActionTaken());
        verify(pythonRagService).route(message);
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_autoPlannerLiveM3AfterPythonRag_retrievesNeverLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        String message = "Show customer C10001";
        stubPythonRoute(message, "rag");
        stubUnderstandLive(message);
        stubDocsGroundedPath(message, "Docs about showing a customer.");

        ChatRequest request = baseRequest(message);
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("rag", response.getActionTaken());
        verify(pythonRagService).route(message);
        verify(pythonRagService).retrieve(eq(message), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_autoPythonError_plannerPathNeverLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("what is OIS100?")).thenThrow(new RuntimeException("route down"));
        stubUnderstandRag("what is OIS100?", List.of("OIS100"));
        stubDocsGroundedPath("what is OIS100?", "OIS100 is Customer Order.");

        ChatRequest request = baseRequest("what is OIS100?");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("rag", response.getActionTaken());
        verify(openAIService).understandRequest(any(), eq("what is OIS100?"));
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_helloAuto_conversationalWithoutRetrieveOrLex() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("hello", "rag");
        stubUnderstand("hello", RequestUnderstandType.CONVERSATIONAL, "Hello! How can I help?");
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("hello");
        request.setMode(ChatMode.AUTO);
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("conversational", response.getActionTaken());
        verify(pythonRagService).route("hello");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void requestRouter_showCustomerOrdersAuto_callsLexNotRetrieve() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("show customer orders for Y11100", "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer orders for Y11100"))
                .thenReturn(lexResult);
        ChatResponse fulfillResponse = new ChatResponse("Orders for Y11100", false);
        fulfillResponse.setActionTaken("search");
        fulfillResponse.setM3Request(new M3RequestDto(true, "OIS100MI", "SearchHead", Map.of("CUNO", "Y11100")));
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("show customer orders for Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("show customer orders for Y11100");
        request.setMode(ChatMode.AUTO);
        comprehendChatService.chat(request);

        verify(pythonRagService).route("show customer orders for Y11100");
        verify(openAIService, never()).understandRequest(any(), anyString());
        verify(lexService).recognizeText("tenant1:user1:session1", "show customer orders for Y11100");
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
        ArgumentCaptor<LexRecognizeResult> lexCaptor = ArgumentCaptor.forClass(LexRecognizeResult.class);
        verify(lexFulfillmentService).fulfillOutcome(lexCaptor.capture(), eq("show customer orders for Y11100"), any());
        assertEquals("SearchCustomerOrder", lexCaptor.getValue().getIntentName());
    }

    @Test
    void requestRouter_fetchCustomer_lexSearchCustomerOrderRemappedToGetCustomer() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("fetch customer Y11100", "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult confused = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "fetch customer Y11100")).thenReturn(confused);
        ChatResponse fulfillResponse = new ChatResponse("Customer Y11100", false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", "Y11100")));
        when(lexFulfillmentService.fulfillOutcome(any(), eq("fetch customer Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("fetch customer Y11100"));

        assertEquals("read", response.getActionTaken());
        assertEquals("CRS610MI", response.getM3Request().getProgram());
        ArgumentCaptor<LexRecognizeResult> lexCaptor = ArgumentCaptor.forClass(LexRecognizeResult.class);
        verify(lexFulfillmentService).fulfillOutcome(lexCaptor.capture(), eq("fetch customer Y11100"), any());
        assertEquals("GetCustomer", lexCaptor.getValue().getIntentName());
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any(), any(), any());
    }

    @Test
    void requestRouter_showCustomer_lexSearchCustomerOrderRemappedToGetCustomer() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("show customer Y11100", "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult confused = new LexRecognizeResult(
                "SearchCustomerOrder",
                "InProgress",
                "ElicitSlot",
                "CustomerOrderNumber",
                Map.of(),
                List.of("What is the order number?")
        );
        when(lexService.recognizeText("tenant1:user1:session1", "show customer Y11100")).thenReturn(confused);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("show customer Y11100"));

        assertEquals("lex_elicit_slot", response.getActionTaken());
        assertEquals("GetCustomer", response.getLexIntent());
        assertEquals("CustomerNumber", response.getLexSlotToElicit());
        verify(lexFulfillmentService, never()).fulfillOutcome(any(), any(), any());
    }

    @Test
    void requestRouter_fetchCustomerOrders_keepsSearchCustomerOrder() {
        enableRequestRouter();
        stubQuotaAllowed();
        stubSanitize();
        stubPythonRoute("fetch customer orders for Y11100", "live");
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "SearchCustomerOrder",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", "Y11100"),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", "fetch customer orders for Y11100"))
                .thenReturn(lexResult);
        ChatResponse fulfillResponse = new ChatResponse("Orders", false);
        fulfillResponse.setActionTaken("search");
        fulfillResponse.setM3Request(new M3RequestDto(true, "OIS100MI", "SearchHead", Map.of("CUNO", "Y11100")));
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq("fetch customer orders for Y11100"), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        comprehendChatService.chat(baseRequest("fetch customer orders for Y11100"));

        ArgumentCaptor<LexRecognizeResult> lexCaptor = ArgumentCaptor.forClass(LexRecognizeResult.class);
        verify(lexFulfillmentService).fulfillOutcome(lexCaptor.capture(), eq("fetch customer orders for Y11100"), any());
        assertEquals("SearchCustomerOrder", lexCaptor.getValue().getIntentName());
    }

    private void stubUnderstand(String message, RequestUnderstandType type, String response) {
        when(openAIService.understandRequest(any(), eq(message))).thenReturn(new RequestUnderstandResult(
                type,
                response,
                List.of(),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
    }

    private void stubUnderstandRag(String message, List<String> queries) {
        when(openAIService.understandRequest(any(), eq(message))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.RAG,
                "",
                queries,
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
    }

    private void stubUnderstandLive(String message) {
        when(openAIService.understandRequest(any(), eq(message))).thenReturn(new RequestUnderstandResult(
                RequestUnderstandType.LIVE_M3,
                "",
                List.of(),
                new OpenAIUsage(1, 1, 2, "gpt")
        ));
    }

    private void stubLexGetCustomerReady(String message, String cuno) {
        when(lexService.isEnabled()).thenReturn(true);
        when(lexService.buildLexSessionId(any())).thenReturn("tenant1:user1:session1");
        LexRecognizeResult lexResult = new LexRecognizeResult(
                "GetCustomer",
                "ReadyForFulfillment",
                "Close",
                null,
                Map.of("CustomerNumber", cuno),
                List.of()
        );
        when(lexService.recognizeText("tenant1:user1:session1", message)).thenReturn(lexResult);
        ChatResponse fulfillResponse = new ChatResponse("Customer " + cuno, false);
        fulfillResponse.setActionTaken("read");
        fulfillResponse.setM3Request(new M3RequestDto(true, "CRS610MI", "GetBasicData", Map.of("CUNO", cuno)));
        when(lexFulfillmentService.fulfillOutcome(eq(lexResult), eq(message), any()))
                .thenReturn(new LexFulfillmentOutcome(fulfillResponse, List.of()));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));
    }
}
