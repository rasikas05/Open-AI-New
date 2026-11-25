package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptCacheProbeTest {

    @Test
    void probe_emptyMessages_returnsDashes() {
        PromptCacheProbe.ProbeResult result = PromptCacheProbe.probe(List.of());
        assertEquals("-", result.systemPromptHash());
        assertEquals("-", result.historyHash());
        assertEquals("-", result.ragContextHash());
        assertEquals("-", result.fullPromptHash());
        assertEquals(0, result.systemPromptChars());
        assertEquals(0, result.systemPromptTokensEst());
    }

    @Test
    void probe_systemOnlyThenUser_setsCumulativeBoundaries() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are helpful."),
                Map.of("role", "user", "content", "What is PPS?")
        );

        PromptCacheProbe.ProbeResult result = PromptCacheProbe.probe(messages);

        assertNotEquals("-", result.systemPromptHash());
        assertEquals("-", result.historyHash());
        assertEquals("-", result.ragContextHash());
        assertEquals(result.systemPromptHash(), result.cumulativeSys());
        assertEquals(result.cumulativeSys(), result.cumulativeSysHist());
        assertNotEquals(result.cumulativeSysHist(), result.cumulativeSysHistQuestion());
        assertEquals(result.fullPromptHash(), result.cumulativeFull());
        assertEquals(result.cumulativeSysHistQuestion(), result.fullPromptHash());
        assertTrue(result.systemPromptChars() > 0);
        assertEquals(PromptCacheProbe.estimateTokens(result.systemPromptChars()), result.systemPromptTokensEst());
    }

    @Test
    void probe_withHistory_divergesAtHistoryBoundary() {
        List<Map<String, String>> withHistory = List.of(
                Map.of("role", "system", "content", "sys"),
                Map.of("role", "user", "content", "prior question"),
                Map.of("role", "user", "content", "current question")
        );
        List<Map<String, String>> withoutHistory = List.of(
                Map.of("role", "system", "content", "sys"),
                Map.of("role", "user", "content", "current question")
        );

        PromptCacheProbe.ProbeResult a = PromptCacheProbe.probe(withHistory);
        PromptCacheProbe.ProbeResult b = PromptCacheProbe.probe(withoutHistory);

        assertEquals(a.cumulativeSys(), b.cumulativeSys());
        assertNotEquals(a.cumulativeSysHist(), b.cumulativeSysHist());
        assertNotEquals("-", a.historyHash());
        assertEquals("-", b.historyHash());
    }

    @Test
    void probe_groundedRag_splitsQuestionAndRagHashes() {
        String question = "CURRENT QUESTION:\nHow do I create a purchase order?\n\n";
        String rag = "doc chunk A about PPS\n";
        String fullUser = question + PromptCacheProbe.RAG_MARKER + "\n" + rag;

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "grounded system"),
                Map.of("role", "user", "content", fullUser)
        );

        PromptCacheProbe.ProbeResult result = PromptCacheProbe.probe(messages);

        assertNotEquals("-", result.ragContextHash());
        assertNotEquals(result.cumulativeSysHistQuestion(), result.fullPromptHash());
        assertTrue(result.prefixCharsBeforeRag() > 0);
        assertEquals(
                PromptCacheProbe.estimateTokens(result.prefixCharsBeforeRag()),
                result.prefixTokensBeforeRagEst()
        );

        List<Map<String, String>> sameQuestionDifferentRag = List.of(
                Map.of("role", "system", "content", "grounded system"),
                Map.of("role", "user", "content", question + PromptCacheProbe.RAG_MARKER + "\nother docs")
        );
        PromptCacheProbe.ProbeResult other = PromptCacheProbe.probe(sameQuestionDifferentRag);
        assertEquals(result.cumulativeSysHistQuestion(), other.cumulativeSysHistQuestion());
        assertNotEquals(result.ragContextHash(), other.ragContextHash());
        assertNotEquals(result.fullPromptHash(), other.fullPromptHash());
    }

    @Test
    void probe_identicalMessages_sameHashes() {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "s"),
                Map.of("role", "user", "content", "q")
        );
        PromptCacheProbe.ProbeResult a = PromptCacheProbe.probe(messages);
        PromptCacheProbe.ProbeResult b = PromptCacheProbe.probe(messages);
        assertEquals(a.fullPromptHash(), b.fullPromptHash());
        assertEquals(a.cumulativeSysHistQuestion(), b.cumulativeSysHistQuestion());
    }

    @Test
    void bodyHasExplicitCacheOptions_detectsTopLevelAndBreakpoint() {
        assertFalse(PromptCacheProbe.bodyHasExplicitCacheOptions(Map.of("model", "x", "messages", List.of())));
        assertTrue(PromptCacheProbe.bodyHasExplicitCacheOptions(
                Map.of("prompt_cache_key", "k", "messages", List.of())));
        assertTrue(PromptCacheProbe.bodyHasExplicitCacheOptions(
                Map.of("prompt_cache_options", Map.of("ttl", "5m"), "messages", List.of())));

        Map<String, Object> withBreakpoint = Map.of(
                "messages",
                List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", "hi",
                                "prompt_cache_breakpoint", true
                        ))
                ))
        );
        assertTrue(PromptCacheProbe.bodyHasExplicitCacheOptions(withBreakpoint));
    }

    @Test
    void estimateTokens_isCeilCharsDiv4() {
        assertEquals(0, PromptCacheProbe.estimateTokens(0));
        assertEquals(1, PromptCacheProbe.estimateTokens(1));
        assertEquals(1, PromptCacheProbe.estimateTokens(4));
        assertEquals(2, PromptCacheProbe.estimateTokens(5));
    }

    @Test
    void resolveStage_prefersExplicitArgOverMdc() {
        try {
            MDC.put(LlmChatCompletionsClient.MDC_LLM_STAGE, "FromMdc");
            assertEquals("Suggestion", LlmChatCompletionsClient.resolveStage("Suggestion"));
            assertEquals("FromMdc", LlmChatCompletionsClient.resolveStage(null));
            assertEquals("FromMdc", LlmChatCompletionsClient.resolveStage("  "));
        } finally {
            MDC.remove(LlmChatCompletionsClient.MDC_LLM_STAGE);
        }
        assertEquals("Unknown", LlmChatCompletionsClient.resolveStage(null));
    }
}
