package com.ai.openai_api_service.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight in-memory guided search dialog state (SEARCH intents only).
 */
public record GuidedSearchState(
        String intentName,
        GuidedSearchPhase phase,
        String selectedM3Field,
        String selectedLexSlot,
        Map<String, String> collectedCriteria,
        Instant updatedAt
) {
    public GuidedSearchState {
        if (intentName == null || intentName.isBlank()) {
            throw new IllegalArgumentException("intentName is required");
        }
        if (phase == null) {
            throw new IllegalArgumentException("phase is required");
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        collectedCriteria = collectedCriteria != null ? Map.copyOf(new LinkedHashMap<>(collectedCriteria)) : Map.of();
    }

    public static GuidedSearchState selectField(String intentName) {
        return new GuidedSearchState(intentName, GuidedSearchPhase.SELECT_FIELD, null, null, Map.of(), Instant.now());
    }

    public static GuidedSearchState collectValue(
            String intentName,
            String m3Field,
            String lexSlot,
            Map<String, String> collectedCriteria
    ) {
        return new GuidedSearchState(
                intentName,
                GuidedSearchPhase.COLLECT_VALUE,
                m3Field,
                lexSlot,
                collectedCriteria,
                Instant.now()
        );
    }
}
