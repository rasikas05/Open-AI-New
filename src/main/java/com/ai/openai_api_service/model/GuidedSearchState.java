package com.ai.openai_api_service.model;

import java.time.Instant;

/**
 * Lightweight in-memory guided search dialog state (SEARCH intents only).
 */
public record GuidedSearchState(
        String intentName,
        GuidedSearchPhase phase,
        String selectedM3Field,
        String selectedLexSlot,
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
    }

    public static GuidedSearchState selectField(String intentName) {
        return new GuidedSearchState(intentName, GuidedSearchPhase.SELECT_FIELD, null, null, Instant.now());
    }

    public static GuidedSearchState collectValue(String intentName, String m3Field, String lexSlot) {
        return new GuidedSearchState(
                intentName,
                GuidedSearchPhase.COLLECT_VALUE,
                m3Field,
                lexSlot,
                Instant.now()
        );
    }
}
