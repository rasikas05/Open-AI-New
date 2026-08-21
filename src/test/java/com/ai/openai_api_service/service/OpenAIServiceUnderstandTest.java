package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.model.RequestUnderstandResult;
import com.ai.openai_api_service.model.RequestUnderstandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIServiceUnderstandTest {

    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        openAIService = new OpenAIService(null, null, null);
    }

    @Test
    void routerPrompt_distinguishesLiveTenantDataFromRagDocumentation() {
        String prompt = openAIService.routerSystemPrompt();
        assertTrue(prompt.contains("Mode (HIGH PRIORITY"));
        assertTrue(prompt.contains("M3: live tenant data/operations ONLY"));
        assertTrue(prompt.contains("Must not offer documentation, how-to, procedures, or configuration help."));
        assertTrue(prompt.contains("AUTO: live tenant data AND documentation/how-to."));
        assertTrue(prompt.contains("DOCS: documentation ONLY"));
        assertTrue(prompt.contains("LIVE_M3: semantic label"));
        assertTrue(prompt.contains("Does not authorize Lex"));
        assertTrue(prompt.contains("RAG: M3 documentation"));
        assertTrue(prompt.contains("never invent program"));
        assertTrue(prompt.contains("Never identify as ChatGPT"));
        assertTrue(prompt.contains("politely redirect"));
        assertFalse(prompt.contains("You are ChatGPT"));
    }

    @Test
    void routerPrompt_usesDomainTestForNonM3NotPhraseAllowlist() {
        String prompt = openAIService.routerSystemPrompt();
        assertTrue(prompt.contains("Classify by this domain test"));
        assertTrue(prompt.contains("NON_M3: not about Infor M3 / CloudSuite"));
        assertTrue(prompt.contains("Mixed greeting + in-domain how-to/docs → RAG"));
        assertFalse(prompt.contains("trip planning"));
        assertFalse(prompt.contains("tell me a joke"));
    }

    @Test
    void parseUnderstandFromLlm_parsesConversationalObject() {
        RequestUnderstandResult result = openAIService.parseUnderstandFromLlm(
                "{\"type\":\"CONVERSATIONAL\",\"response\":\"Hi! I'm the M3 AI Assistant.\",\"queries\":[]}",
                new OpenAIUsage(1, 2, 3, "gpt")
        );
        assertEquals(RequestUnderstandType.CONVERSATIONAL, result.type());
        assertEquals("Hi! I'm the M3 AI Assistant.", result.response());
        assertEquals(List.of(), result.queries());
    }

    @Test
    void parseUnderstandFromLlm_parsesRagQueriesAndClearsResponse() {
        RequestUnderstandResult result = openAIService.parseUnderstandFromLlm(
                """
                {"type":"RAG","response":"should be dropped","queries":["GLS037 accounting identities","GLS037 Infor M3"]}
                """,
                null
        );
        assertEquals(RequestUnderstandType.RAG, result.type());
        assertEquals("", result.response());
        assertEquals(List.of("GLS037 accounting identities", "GLS037 Infor M3"), result.queries());
    }

    @Test
    void parseUnderstandFromLlm_parsesLiveAndNonM3() {
        RequestUnderstandResult live = openAIService.parseUnderstandFromLlm(
                "{\"type\":\"LIVE_M3\",\"response\":\"nope\",\"queries\":[\"ignore\"]}",
                null
        );
        assertEquals(RequestUnderstandType.LIVE_M3, live.type());
        assertEquals("", live.response());
        assertEquals(List.of(), live.queries());

        RequestUnderstandResult nonM3 = openAIService.parseUnderstandFromLlm(
                "{\"type\":\"NON_M3\",\"response\":\"I mainly support Infor M3 and CloudSuite questions.\",\"queries\":[]}",
                null
        );
        assertEquals(RequestUnderstandType.NON_M3, nonM3.type());
        assertTrue(nonM3.response().contains("Infor M3"));
    }

    @Test
    void parseUnderstandFromLlm_rejectsJsonArrayRewriteShape() {
        assertThrows(
                OpenAIException.class,
                () -> openAIService.parseUnderstandFromLlm("[\"customer pricing\"]", null)
        );
    }

    @Test
    void parseUnderstandFromLlm_rejectsInvalidJson() {
        assertThrows(OpenAIException.class, () -> openAIService.parseUnderstandFromLlm("not json", null));
    }

    @Test
    void buildUnderstandUserContent_includesModeLine() {
        String content = openAIService.buildUnderstandUserContent("hi", List.of(), ChatMode.M3);
        assertTrue(content.startsWith("Mode: M3\n"));
        assertTrue(content.contains("CURRENT QUESTION:\nhi"));
    }

    @Test
    void buildUnderstandUserContent_prefixesPreviousUserQuestions() {
        String content = openAIService.buildUnderstandUserContent(
                "hello",
                List.of(new com.ai.openai_api_service.model.MessageDto("user", "what is your name")),
                ChatMode.DOCS
        );
        assertTrue(content.startsWith("Mode: DOCS\n"));
        assertTrue(content.contains("PREVIOUS USER QUESTIONS:"));
        assertTrue(content.contains("- what is your name"));
        assertTrue(content.contains("CURRENT QUESTION:\nhello"));
    }
}
