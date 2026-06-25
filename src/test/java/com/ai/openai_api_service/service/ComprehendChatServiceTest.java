package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatRequest;
import com.ai.openai_api_service.model.ChatResponse;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.SuggestionResult;
import com.ai.openai_api_service.model.TokenUsageDto;
import com.ai.openai_api_service.model.python_rag.ChunkItem;
import com.ai.openai_api_service.model.python_rag.PythonRetrievalResponse;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
import com.ai.openai_api_service.service.TenantQuotaService.QuotaCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private ComprehendChatService comprehendChatService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(comprehendChatService, "ragFallbackOnNoAnswer", true);
        ReflectionTestUtils.setField(comprehendChatService, "skipRewriteDefault", true);
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
        ChunkItem chunk = new ChunkItem("chunk text", 0.62f, "Title", "http://example.com", List.of("CRS610"), null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(10, 20, 30, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("grounded answer", false);
        openAiResponse.setActionTaken("rag");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatRequest request = baseRequest("how to create customer");
        ChatResponse response = comprehendChatService.chat(request);

        assertEquals("grounded answer", response.getReply());
        assertEquals("rag", response.getActionTaken());
        assertEquals("ready_for_grounding", response.getRetrievalReason());
        assertNotNull(response.getOpenAiUsage());
        verify(openAIService).chatWithRagContext(any(), eq(List.of(chunk)));
        verify(openAIService, never()).chatWithoutPersistence(any());
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
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(5, 5, 10, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("fallback answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest("weak docs"));

        assertEquals("fallback answer", response.getReply());
        assertEquals("gpt_infor", response.getActionTaken());
        verify(openAIService).chatWithoutPersistence(any());
        verify(openAIService, never()).chatWithRagContext(any(), any());
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
    void documentationRoute_ragInsufficientAnswer_fallsBackToOpenAi() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to add KIT")).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("ready_for_grounding");
        retrieval.setRetrievalTimeMs(100);
        retrieval.setMaxScore(0.64f);
        ChunkItem chunk = new ChunkItem("chunk text", 0.64f, "Title", "http://example.com", List.of("OIS100"), null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

        OpenAIUsage ragUsage = new OpenAIUsage(3000, 20, 3020, "gpt-4.1");
        ChatResponse ragResponse = new ChatResponse(
                "This information is not available in the current documentation. Please refer to the official Infor M3 documentation or contact your M3 administrator.",
                false
        );
        ragResponse.setActionTaken("rag");
        ragResponse.setOpenAiUsage(ragUsage);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(ragResponse);

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
    }

    @Test
    void documentationRoute_retrievalTimeout_fallsBackToOpenAi() {
        stubQuotaAllowed();
        stubSanitize();
        when(pythonRagService.route("how to create customer")).thenReturn(new PythonRouteResponse("rag"));
        when(pythonRagService.retrieve(any())).thenThrow(
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
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

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
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

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
        when(pythonRagService.retrieve(any())).thenThrow(
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
                anyString(), any(), anyString(), anyBoolean(), anyString(), any()
        );
    }

    @Test
    void piiSanitization_sendsSanitizedTextToPythonRoute() {
        stubQuotaAllowed();
        String original = "Contact John at john@example.com about CRS900";
        String sanitized = "Contact [Name] at [EMAIL] about CRS900";
        stubSanitizeWithPii(original, sanitized);
        when(pythonRagService.route(sanitized)).thenReturn(new PythonRouteResponse("rag"));

        PythonRetrievalResponse retrieval = new PythonRetrievalResponse();
        retrieval.setRetrievalReason("below_prompt_threshold");
        retrieval.setPromptChunks(List.of());
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

        ChatResponse openAiResponse = new ChatResponse("answer", false);
        openAiResponse.setActionTaken("gpt_infor");
        openAiResponse.setOpenAiUsage(new OpenAIUsage(1, 1, 2, "gpt-4.1"));
        when(openAIService.chatWithoutPersistence(any())).thenReturn(openAiResponse);
        when(suggestionEngineService.generateSuggestions(any())).thenReturn(new SuggestionResult(List.of(), List.of()));

        ChatResponse response = comprehendChatService.chat(baseRequest(original));

        assertTrue(response.getSanitizationApplied());
        verify(pythonRagService).route(eq(sanitized));
        verify(pythonRagService).retrieve(any());
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
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

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
        ChunkItem chunk = new ChunkItem("chunk", 0.71f, "CRS780", "http://docs/crs780", List.of("CRS780"), null, null, null);
        retrieval.setPromptChunks(List.of(chunk));
        when(pythonRagService.retrieve(any())).thenReturn(retrieval);

        OpenAIUsage usage = new OpenAIUsage(100, 50, 150, "gpt-4.1");
        ChatResponse openAiResponse = new ChatResponse("configure in CRS780", false);
        openAiResponse.setActionTaken("rag");
        openAiResponse.setOpenAiUsage(usage);
        when(openAIService.chatWithRagContext(any(), eq(List.of(chunk)))).thenReturn(openAiResponse);
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
                eq(88)
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
