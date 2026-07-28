package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
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
import com.ai.openai_api_service.model.python_rag.PythonRetrievalResponse;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
import com.ai.openai_api_service.model.rag.GroundedRagCallResult;
import com.ai.openai_api_service.model.rag.GroundedRagResult;
import com.ai.openai_api_service.model.rag.RagStatus;
import com.ai.openai_api_service.service.query.SearchContextService;
import com.ai.openai_api_service.service.guided.GuidedSearchService;
import com.ai.openai_api_service.service.guided.InMemoryGuidedSearchSessionService;
import com.ai.openai_api_service.service.api.InformationRequestCatalog;
import com.ai.openai_api_service.service.TenantQuotaService.QuotaCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
    private ComprehendAnonymizationService comprehendAnonymizationService;
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
        lenient().when(guidedSearchSessionService.find(any())).thenReturn(Optional.empty());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(10, 20, 30, "gpt-4.1");
        GroundedRagResult grounded = new GroundedRagResult(RagStatus.FULL, "grounded answer", List.of());
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk))))
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
        verify(openAIService).chatWithRagContext(any(), eq(List.of(chunk)));
        verify(openAIService, never()).chatWithoutPersistence(any());
        verify(openAIService, never()).chatGapFill(any(), any(), any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("weak docs"));

        assertEquals("fallback answer", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        assertNotNull(response.getSources());
        assertTrue(response.getSources().isEmpty());
        verify(openAIService).chatWithoutPersistence(any());
        verify(openAIService, never()).chatWithRagContext(any(), any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("grounded answer", false);
        openAiResponse.setActionTaken("rag");
        when(openAIService.chatWithRagContext(any(), anyList())).thenReturn(
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
        verify(openAIService, never()).chatWithRagContext(any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage ragUsage = new OpenAIUsage(3000, 20, 3020, "gpt-4.1");
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(
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
        when(openAIService.chatWithoutPersistence(any())).thenReturn(fallbackResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to add KIT"));

        assertEquals("To add a KIT on a customer order line, open OIS100...", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        assertEquals("rag_no_answer_fallback", response.getRetrievalReason());
        assertEquals(3170, response.getOpenAiUsage().getTotalTokens());
        verify(openAIService).chatWithRagContext(any(), eq(List.of(chunk)));
        verify(openAIService).chatWithoutPersistence(any());
        verify(openAIService, never()).chatGapFill(any(), any(), any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage ragUsage = new OpenAIUsage(100, 50, 150, "gpt-4.1");
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(
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
        when(openAIService.chatGapFill(any(), anyString(), anyList())).thenReturn(gapResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("What is MNS204 used for?"));

        assertTrue(response.getReply().contains("## Documentation"));
        assertTrue(response.getReply().contains("MNS204 appears in user settings documentation."));
        assertTrue(response.getReply().contains("## Additional AI Information"));
        assertTrue(response.getReply().contains("Functional purpose: ..."));
        assertEquals("rag", response.getActionTaken());
        assertEquals("ready_for_grounding", response.getRetrievalReason());
        assertEquals(200, response.getOpenAiUsage().getTotalTokens());
        verify(openAIService).chatGapFill(
                any(),
                eq("MNS204 appears in user settings documentation."),
                eq(List.of("Functional purpose", "Business usage"))
        );
        verify(openAIService, never()).chatWithoutPersistence(any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(
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
        verify(openAIService, never()).chatGapFill(any(), any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);
        when(openAIService.chatWithRagContext(any(), anyList()))
                .thenThrow(new OpenAIException("Failed to parse grounded RAG JSON", 502));

        ChatResponse fallback = new ChatResponse("general answer", false);
        fallback.setActionTaken("gpt_infor");
        fallback.setOpenAiUsage(new OpenAIUsage(5, 5, 10, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to create customer"));

        assertEquals("general answer", response.getReply());
        assertEquals("rag_no_answer_fallback", response.getRetrievalReason());
        verify(openAIService).chatWithoutPersistence(any());
    }

    @Test
    void documentationRoute_retrievalTimeout_fallsBackToOpenAi() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenThrow(
                new OpenAIException("Python RAG API timeout after 180000ms", 504)
        );

        OpenAIUsage usage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback after timeout", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to create customer"));

        assertEquals("fallback after timeout", response.getReply());
        assertEquals("retrieval_error", response.getRetrievalReason());
        verify(openAIService).chatWithoutPersistence(any());
        verify(openAIService, never()).chatWithRagContext(any(), any());
    }

    @Test
    void documentationRoute_noMatches_usesFallback() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("unknown topic")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("no_matches");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(8, 12, 20, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("general m3 answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("unknown topic"));

        assertEquals("general m3 answer", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        assertEquals("no_matches", response.getRetrievalReason());
        verify(openAIService).chatWithoutPersistence(any());
        verify(openAIService, never()).chatWithRagContext(any(), any());
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(6, 4, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback after qdrant error", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to configure CRS900"));

        assertEquals("fallback after qdrant error", response.getReply());
        assertEquals("retrieval_error", response.getRetrievalReason());
        assertEquals("gpt_infor", response.getActionTaken());
        verify(openAIService).chatWithoutPersistence(any());
        verify(tenantQuotaService).recordUsage(eq("tenant1"), eq(10), anyString());
    }

    @Test
    void documentationRoute_pythonUnreachable_fallsBackAndRecordsTokens() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenThrow(
                new OpenAIException("Python RAG API connection refused: WinError 10061", 503)
        );

        OpenAIUsage usage = new OpenAIUsage(15, 25, 40, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback after connection error", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("how to create customer"));

        assertEquals("fallback after connection error", response.getReply());
        assertEquals("retrieval_error", response.getRetrievalReason());
        assertEquals(40, response.getOpenAiUsage().getTotalTokens());
        verify(openAIService).chatWithoutPersistence(any());
        verify(tenantQuotaService).recordUsage(eq("tenant1"), eq(40), anyString());
    }

    @Test
    void quotaBlockedBeforeChat_returnsLimitExceededWithoutCallingServices() {
        when(tenantQuotaService.checkBeforeChat("tenant1"))
                .thenReturn(new QuotaCheckResult(false, new TokenUsageDto(1000, 1000, 0), "LIMIT_EXCEEDED"));

        ChatResponse response = comprehendChatService.chat(baseRequest("hello"));

        assertTrue(response.getLimitExceeded());
        assertEquals("LIMIT_EXCEEDED", response.getBlockReason());
        verify(comprehendAnonymizationService, never()).detectAndAnonymize(anyString());
        verify(pythonRagService, never()).route(anyString());
        verify(openAIService, never()).chatWithRagContext(any(), any());
        verify(openAIService, never()).chatWithoutPersistence(any());
        verify(tenantQuotaService, never()).recordUsage(anyString(), anyInt(), anyString());
        verify(chatPersistenceService, never()).persistChat(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), anyBoolean(), anyString(), any(), any()
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(new OpenAIUsage(1, 1, 2, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertTrue(response.getSanitizationApplied());
        verify(pythonRagService).route(eq(original));
        verify(pythonRagService).retrieve(eq(sanitized), eq(List.of(sanitized)), any(), any());
        verify(lexService, never()).recognizeText(anyString(), anyString());
    }

    @Test
    void comprehendFails_usesOriginalTextAndCompletes() {
        stubQuotaAllowed();
        String original = "how to configure purchase settings";
        when(comprehendAnonymizationService.detectAndAnonymize(original))
                .thenThrow(new RuntimeException("Comprehend IAM denied"));
        when(pythonRagService.route(original)).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("still works", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(new OpenAIUsage(3, 3, 6, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(100, 50, 150, "gpt-4.1");
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(
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
                isNull()
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        ChatResponse fallback = new ChatResponse("fallback", false);
        fallback.setActionTaken("gpt_infor");
        when(openAIService.chatWithoutPersistence(any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        comprehendChatService.chat(baseRequest("pricing issue"));

        verify(openAIService, never()).rewriteQueries(anyString());
        verify(pythonRagService).retrieve(eq("pricing issue"), eq(List.of("pricing issue")), any(), any());
    }

    @Test
    void documentationRoute_queryRewriteEnabled_callsSpringRewriteAndRetrieve() {
        ReflectionTestUtils.setField(comprehendChatService, "queryRewriteEnabled", true);
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("pricing issue")).thenReturn(new PythonRouteResponse("rag"));

        List<String> rewritten = List.of("customer pricing configuration", "price list setup");
        OpenAIUsage rewriteUsage = new OpenAIUsage(8, 4, 12, "gpt-4.1");
        when(openAIService.rewriteQueries("pricing issue")).thenReturn(new QueryRewriteResult(rewritten, rewriteUsage));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        OpenAIUsage answerUsage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse fallback = new ChatResponse("fallback", false);
        fallback.setActionTaken("gpt_infor");
        fallback.setOpenAiUsage(answerUsage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(fallback);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("pricing issue"));

        verify(openAIService).rewriteQueries("pricing issue");
        verify(pythonRagService).retrieve(
                eq("pricing issue"),
                eq(List.of("pricing issue", "customer pricing configuration", "price list setup")),
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
        when(pythonRagService.retrieve(anyString(), anyList(), any(), any())).thenReturn(retrieval);

        ChatResponse fallback = new ChatResponse("doc answer", false);
        fallback.setActionTaken("gpt_infor");
        when(openAIService.chatWithoutPersistence(any())).thenReturn(fallback);
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
                eq(new LiveHistoryAuditMetadata("GetCustomer", "Customer", "CSU001"))
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
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any());
        verify(pythonRagService, never()).executeLiveIntent(anyString(), any());
        verify(openAIService, never()).chatWithRagContext(any(), anyList());
        verify(openAIService, never()).chatWithoutPersistence(any());
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
        verify(pythonRagService, never()).retrieve(anyString(), anyList(), any(), any());
        verify(openAIService, never()).chatWithRagContext(any(), anyList());
        verify(openAIService, never()).chatWithoutPersistence(any());
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

        ChatResponse menu = new ChatResponse("I can search customer orders.", false);
        menu.setActionTaken(GuidedSearchService.ACTION_SELECT_FIELD);
        menu.setLexIntent("SearchCustomerOrder");
        menu.setCollectingTool("SearchCustomerOrder");
        menu.setNextField(GuidedSearchService.NEXT_FIELD_CHOICE);
        when(guidedSearchService.start(eq("SearchCustomerOrder"), any())).thenReturn(menu);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("Show customer orders"));

        assertEquals(GuidedSearchService.ACTION_SELECT_FIELD, response.getActionTaken());
        assertTrue(response.getReply().contains("customer orders"));
        verify(guidedSearchService).start(eq("SearchCustomerOrder"), any());
        verify(lexService).recognizeText(anyString(), anyString());
    }

    @Test
    void liveRoute_activeGuidedSession_skipsLexAndHandlesTurn() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("2")).thenReturn(new PythonRouteResponse("live"));

        GuidedSearchState state = GuidedSearchState.selectField("SearchCustomerOrder");
        when(guidedSearchSessionService.find(any())).thenReturn(Optional.of(state));

        ChatResponse collect = new ChatResponse("Please enter the customer order number.", false);
        collect.setActionTaken(GuidedSearchService.ACTION_COLLECT_VALUE);
        collect.setNextField("ORNO");
        when(guidedSearchService.handleTurn(any(), eq(state), eq("2")))
                .thenReturn(GuidedSearchService.GuidedTurnResult.response(collect));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("2"));

        assertEquals(GuidedSearchService.ACTION_COLLECT_VALUE, response.getActionTaken());
        assertEquals("ORNO", response.getNextField());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(lexFulfillmentService, never()).fulfillSearch(anyString(), any(), anyString(), any());
    }

    @Test
    void liveRoute_guidedValueCollected_resumesFulfillSearch() {
        stubQuotaAllowed();
        stubSanitize();
        when(lexService.isEnabled()).thenReturn(true);
        when(pythonRagService.route("1000001234")).thenReturn(new PythonRouteResponse("live"));

        GuidedSearchState state = GuidedSearchState.collectValue(
                "SearchCustomerOrder", "ORNO", "CustomerOrderNumber");
        when(guidedSearchSessionService.find(any())).thenReturn(Optional.of(state));
        when(guidedSearchService.handleTurn(any(), eq(state), eq("1000001234")))
                .thenReturn(GuidedSearchService.GuidedTurnResult.resume(
                        "SearchCustomerOrder",
                        Map.of("CustomerOrderNumber", "1000001234")
                ));

        M3RequestDto m3Request = new M3RequestDto(
                true, "OIS100MI", "SearchHead", Map.of("SQRY", "ORNO:'1000001234'"));
        ChatResponse fulfilled = new ChatResponse("Processing your request...", false);
        fulfilled.setActionTaken("search");
        fulfilled.setM3Request(m3Request);
        fulfilled.setLexIntent("SearchCustomerOrder");
        when(lexFulfillmentService.fulfillSearch(
                eq("SearchCustomerOrder"),
                eq(Map.of("CustomerOrderNumber", "1000001234")),
                eq("1000001234"),
                any()
        )).thenReturn(new LexFulfillmentOutcome(fulfilled, List.of(new SearchCriterion("ORNO", "1000001234"))));
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("1000001234"));

        assertEquals("search", response.getActionTaken());
        assertNotNull(response.getM3Request());
        assertEquals("OIS100MI", response.getM3Request().getProgram());
        verify(lexService, never()).recognizeText(anyString(), anyString());
        verify(lexFulfillmentService).fulfillSearch(
                eq("SearchCustomerOrder"),
                eq(Map.of("CustomerOrderNumber", "1000001234")),
                eq("1000001234"),
                any()
        );
    }

    private void stubQuotaAllowed() {
        when(tenantQuotaService.checkBeforeChat(anyString()))
                .thenReturn(new QuotaCheckResult(true, new TokenUsageDto(0, 1000, 1000), null));
    }

    private void stubSanitize() {
        when(comprehendAnonymizationService.detectAndAnonymize(anyString())).thenAnswer(invocation -> {
            Map<String, Object> sanitized = new HashMap<>();
            sanitized.put("sanitizedText", invocation.getArgument(0));
            return sanitized;
        });
    }

    private void stubSanitizeWithPii(String original, String sanitizedText) {
        when(comprehendAnonymizationService.detectAndAnonymize(original)).thenReturn(
                Map.of("sanitizedText", sanitizedText)
        );
    }

    private ChatRequest baseRequest(String message) {
        ChatRequest request = new ChatRequest();
        request.setTenantCode("tenant1");
        request.setUserId("user1");
        request.setSessionId("session1");
        request.setUserMessage(message);
        return request;
    }
}
