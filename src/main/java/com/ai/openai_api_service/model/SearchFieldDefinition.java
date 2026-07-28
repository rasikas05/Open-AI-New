package com.ai.openai_api_service.model;

import java.util.List;

public record SearchFieldDefinition(
        String intentName,
        String m3Field,
        List<String> keywords,
        String description,
        String lexSlotName,
        int displayOrder,
        String prompt,
        String example,
        List<String> aliases
) {
    public SearchFieldDefinition {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public SearchFieldDefinition(
            String intentName,
            String m3Field,
            List<String> keywords,
            String description,
            String lexSlotName
    ) {
        this(intentName, m3Field, keywords, description, lexSlotName, 0, null, null, List.of());
    }
}
