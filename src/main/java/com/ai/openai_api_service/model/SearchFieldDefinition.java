package com.ai.openai_api_service.model;

import java.util.List;

public record SearchFieldDefinition(
        String intentName,
        String m3Field,
        List<String> keywords,
        String description,
        String lexSlotName
) {
}
