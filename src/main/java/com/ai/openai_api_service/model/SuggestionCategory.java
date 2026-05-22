package com.ai.openai_api_service.model;

import java.util.Locale;

public enum SuggestionCategory {
    FOLLOW_UP,
    RELATED_TOPIC,
    TROUBLESHOOTING,
    BEST_PRACTICE,
    CONFIGURATION,
    API_RELATED,
    GENERIC;

    public static SuggestionCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        try {
            return SuggestionCategory.valueOf(value.trim().replaceAll("[^A-Za-z_]+", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GENERIC;
        }
    }
}
