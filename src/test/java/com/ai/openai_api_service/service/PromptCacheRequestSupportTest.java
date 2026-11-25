package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptCacheRequestSupportTest {

    @Test
    void shouldApplyExplicitCache_onlyForExplicitGrounded() {
        assertTrue(PromptCacheRequestSupport.shouldApplyExplicitCache("explicit", "Grounded"));
        assertTrue(PromptCacheRequestSupport.shouldApplyExplicitCache("EXPLICIT", "grounded"));
        assertFalse(PromptCacheRequestSupport.shouldApplyExplicitCache("off", "Grounded"));
        assertFalse(PromptCacheRequestSupport.shouldApplyExplicitCache("explicit", "Planner"));
        assertFalse(PromptCacheRequestSupport.shouldApplyExplicitCache("explicit", "Rewrite"));
        assertFalse(PromptCacheRequestSupport.shouldApplyExplicitCache("explicit", "General"));
        assertFalse(PromptCacheRequestSupport.shouldApplyExplicitCache("explicit", "GapFill"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyExplicitGroundedCache_rewritesSystemStringAndAddsOptions() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(
                Map.of("role", "system", "content", "stable"),
                Map.of("role", "user", "content", "q + rag")
        ));

        PromptCacheRequestSupport.applyExplicitGroundedCache(body, "30m");

        assertEquals(Map.of("mode", "explicit", "ttl", "30m"), body.get("prompt_cache_options"));
        assertFalse(body.containsKey("prompt_cache_key"));
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        Object content = messages.get(0).get("content");
        assertTrue(content instanceof List<?>);
        Map<String, Object> part = (Map<String, Object>) ((List<?>) content).get(0);
        assertEquals("text", part.get("type"));
        assertEquals("stable", part.get("text"));
        assertEquals(Map.of("mode", "explicit"), part.get("prompt_cache_breakpoint"));
        assertEquals("q + rag", messages.get(1).get("content"));
    }

    @Test
    void applyExplicitGroundedCache_noSystemMessage_leavesBodyUnchanged() {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(Map.of("role", "user", "content", "only user")));
        PromptCacheRequestSupport.applyExplicitGroundedCache(body, "30m");
        assertFalse(body.containsKey("prompt_cache_options"));
        assertEquals("only user", ((List<?>) body.get("messages")).isEmpty()
                ? null
                : ((Map<?, ?>) ((List<?>) body.get("messages")).get(0)).get("content"));
    }
}
