package com.ai.openai_api_service.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies Bedrock Chat Completions explicit prompt-cache fields for Grounded RAG only.
 * Does not set {@code prompt_cache_key} until AWS confirms Chat Completions support.
 */
final class PromptCacheRequestSupport {

    static final String MODE_OFF = "off";
    static final String MODE_EXPLICIT = "explicit";
    static final String STAGE_GROUNDED = "Grounded";

    private PromptCacheRequestSupport() {
    }

    static boolean isExplicitMode(String mode) {
        return MODE_EXPLICIT.equalsIgnoreCase(normalize(mode));
    }

    static boolean shouldApplyExplicitCache(String mode, String stage) {
        return isExplicitMode(mode) && STAGE_GROUNDED.equalsIgnoreCase(normalize(stage));
    }

    /**
     * Mutates {@code body} in place: structured system text part + breakpoint + prompt_cache_options.
     * No-op when messages are missing or system content cannot be converted.
     */
    static void applyExplicitGroundedCache(Map<String, Object> body, String ttl) {
        if (body == null) {
            return;
        }
        Object messagesObj = body.get("messages");
        if (!(messagesObj instanceof List<?> messages) || messages.isEmpty()) {
            return;
        }

        List<Map<String, Object>> rewritten = new ArrayList<>(messages.size());
        boolean breakpointApplied = false;
        for (Object messageObj : messages) {
            if (!(messageObj instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> message = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    message.put(entry.getKey().toString(), entry.getValue());
                }
            }
            if (!breakpointApplied && isSystemRole(message.get("role"))) {
                Object content = message.get("content");
                if (content instanceof String text && !text.isBlank()) {
                    Map<String, Object> part = new HashMap<>();
                    part.put("type", "text");
                    part.put("text", text);
                    part.put("prompt_cache_breakpoint", Map.of("mode", MODE_EXPLICIT));
                    message.put("content", List.of(part));
                    breakpointApplied = true;
                } else if (content instanceof List<?> parts) {
                    List<Object> newParts = new ArrayList<>(parts.size());
                    boolean marked = false;
                    for (Object partObj : parts) {
                        if (!marked && partObj instanceof Map<?, ?> partMap) {
                            Map<String, Object> part = new HashMap<>();
                            for (Map.Entry<?, ?> entry : partMap.entrySet()) {
                                if (entry.getKey() != null) {
                                    part.put(entry.getKey().toString(), entry.getValue());
                                }
                            }
                            if ("text".equals(String.valueOf(part.get("type"))) || part.containsKey("text")) {
                                part.putIfAbsent("type", "text");
                                part.put("prompt_cache_breakpoint", Map.of("mode", MODE_EXPLICIT));
                                marked = true;
                                breakpointApplied = true;
                            }
                            newParts.add(part);
                        } else {
                            newParts.add(partObj);
                        }
                    }
                    message.put("content", newParts);
                }
            }
            rewritten.add(message);
        }

        if (!breakpointApplied) {
            return;
        }

        body.put("messages", rewritten);
        Map<String, Object> options = new HashMap<>();
        options.put("mode", MODE_EXPLICIT);
        String effectiveTtl = (ttl == null || ttl.isBlank()) ? "30m" : ttl.trim();
        options.put("ttl", effectiveTtl);
        body.put("prompt_cache_options", options);
        // Intentionally omit prompt_cache_key until AWS confirms Chat Completions support.
    }

    private static boolean isSystemRole(Object role) {
        return role != null && "system".equalsIgnoreCase(role.toString().trim());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
