package com.ai.openai_api_service.service;

import com.ai.openai_api_service.model.OpenAIUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies mergeUsage preserves/aggregates Bedrock cache metrics across stages.
 * This does not reduce prompt_tokens — it only keeps cache tracking intact after merges.
 */
class ComprehendChatServiceMergeUsageTest {

    private ComprehendChatService service;

    @BeforeEach
    void setUp() {
        // Real mergeUsage only; no constructor deps required.
        service = mock(ComprehendChatService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    @Test
    void mergeUsage_nullFirst_returnsSecond() {
        OpenAIUsage grounded = withCache("terra", 2000, 100, 2100, 0, 1491);
        assertSame(grounded, service.mergeUsage(null, grounded));
    }

    @Test
    void mergeUsage_nullSecond_returnsFirst() {
        OpenAIUsage planner = noCache("terra", 80, 20, 100);
        assertSame(planner, service.mergeUsage(planner, null));
    }

    @Test
    void mergeUsage_bothNull_returnsNull() {
        assertNull(service.mergeUsage(null, null));
    }

    @Test
    void mergeUsage_plannerWithoutCache_plusGroundedWithCache_preservesGroundedMetrics() {
        OpenAIUsage planner = noCache("global.openai.gpt-5.6-terra", 120, 40, 160);
        OpenAIUsage grounded = withCache("global.openai.gpt-5.6-terra", 3500, 300, 3800, 0, 1491);

        OpenAIUsage merged = service.mergeUsage(planner, grounded);

        assertEquals(3620, merged.getPromptTokens());
        assertEquals(340, merged.getCompletionTokens());
        assertEquals(3960, merged.getTotalTokens());
        assertTrue(merged.isCacheMetricsPresent());
        assertEquals(0, merged.getCacheWriteTokens());
        assertEquals(1491, merged.getCachedTokens());
    }

    @Test
    void mergeUsage_plannerAndGrounded_bothWithCache_sumsCacheTokens() {
        OpenAIUsage planner = withCache("terra", 100, 10, 110, 5, 0);
        OpenAIUsage grounded = withCache("terra", 2000, 200, 2200, 1491, 0);

        OpenAIUsage merged = service.mergeUsage(planner, grounded);

        assertEquals(2100, merged.getPromptTokens());
        assertEquals(210, merged.getCompletionTokens());
        assertEquals(2310, merged.getTotalTokens());
        assertTrue(merged.isCacheMetricsPresent());
        assertEquals(1496, merged.getCacheWriteTokens());
        assertEquals(0, merged.getCachedTokens());
    }

    @Test
    void mergeUsage_bothWithoutCache_leavesCacheFieldsAbsent() {
        OpenAIUsage planner = noCache("terra", 50, 10, 60);
        OpenAIUsage grounded = noCache("terra", 500, 50, 550);

        OpenAIUsage merged = service.mergeUsage(planner, grounded);

        assertEquals(550, merged.getPromptTokens());
        assertEquals(60, merged.getCompletionTokens());
        assertEquals(610, merged.getTotalTokens());
        assertFalse(merged.isCacheMetricsPresent());
        assertNull(merged.getCacheWriteTokens());
        assertNull(merged.getCachedTokens());
    }

    @Test
    void mergeUsage_multiStage_plannerGroundedGapFillGeneral_keepsCacheFromGrounded() {
        OpenAIUsage planner = noCache("terra", 100, 20, 120);
        OpenAIUsage grounded = withCache("terra", 3000, 250, 3250, 1491, 0);
        OpenAIUsage afterGrounded = service.mergeUsage(planner, grounded);

        OpenAIUsage gapFill = noCache("terra", 200, 80, 280);
        OpenAIUsage afterGap = service.mergeUsage(afterGrounded, gapFill);

        OpenAIUsage general = noCache("terra", 400, 100, 500);
        OpenAIUsage finalUsage = service.mergeUsage(afterGap, general);

        assertEquals(3700, finalUsage.getPromptTokens());
        assertEquals(450, finalUsage.getCompletionTokens());
        assertEquals(4150, finalUsage.getTotalTokens());
        assertTrue(finalUsage.isCacheMetricsPresent());
        assertEquals(1491, finalUsage.getCacheWriteTokens());
        assertEquals(0, finalUsage.getCachedTokens());
    }

    @Test
    void mergeUsage_groundedCacheRead_thenGeneral_preservesCachedTokens() {
        OpenAIUsage planner = noCache("terra", 90, 10, 100);
        OpenAIUsage grounded = withCache("terra", 3100, 200, 3300, 0, 1491);
        OpenAIUsage merged = service.mergeUsage(planner, grounded);
        OpenAIUsage general = noCache("terra", 150, 40, 190);

        OpenAIUsage finalUsage = service.mergeUsage(merged, general);

        assertEquals(3340, finalUsage.getPromptTokens());
        assertTrue(finalUsage.isCacheMetricsPresent());
        assertEquals(0, finalUsage.getCacheWriteTokens());
        assertEquals(1491, finalUsage.getCachedTokens());
    }

    @Test
    void mergeUsage_cachePresentWithNullTokenFields_treatsAsZero() {
        OpenAIUsage planner = noCache("terra", 10, 1, 11);
        OpenAIUsage grounded = new OpenAIUsage(100, 5, 105, "terra");
        grounded.setCacheMetricsPresent(true);
        grounded.setCacheWriteTokens(null);
        grounded.setCachedTokens(null);

        OpenAIUsage merged = service.mergeUsage(planner, grounded);

        assertTrue(merged.isCacheMetricsPresent());
        assertEquals(0, merged.getCacheWriteTokens());
        assertEquals(0, merged.getCachedTokens());
    }

    private static OpenAIUsage noCache(String model, int prompt, int completion, int total) {
        return new OpenAIUsage(prompt, completion, total, model);
    }

    private static OpenAIUsage withCache(
            String model,
            int prompt,
            int completion,
            int total,
            int cacheWrite,
            int cached
    ) {
        OpenAIUsage usage = new OpenAIUsage(prompt, completion, total, model);
        usage.setCacheMetricsPresent(true);
        usage.setCacheWriteTokens(cacheWrite);
        usage.setCachedTokens(cached);
        return usage;
    }
}
