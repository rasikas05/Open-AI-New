package com.ai.openai_api_service.model.rag;

import com.ai.openai_api_service.model.OpenAIUsage;

/**
 * Grounded RAG OpenAI call result plus Phase 3 stage timings.
 * {@code promptBuildMs} covers formatRagContext + buildRagUserPrompt + buildMessages.
 * {@code openAiWaitMs} is HTTP request + OpenAI wait (non-streaming wall clock).
 * {@code responseParseMs} is parseGroundedRagResult.
 */
public record GroundedRagCallResult(
        GroundedRagResult grounded,
        OpenAIUsage usage,
        String rawContent,
        long promptBuildMs,
        long openAiWaitMs,
        long responseParseMs,
        int promptContextChars,
        int chunkCount
) {
    /** Backward-compatible constructor for tests/mocks without stage timings. */
    public GroundedRagCallResult(GroundedRagResult grounded, OpenAIUsage usage, String rawContent) {
        this(grounded, usage, rawContent, 0L, 0L, 0L, 0, 0);
    }
}
