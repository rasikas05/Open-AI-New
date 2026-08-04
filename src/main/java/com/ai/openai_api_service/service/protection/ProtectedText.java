package com.ai.openai_api_service.service.protection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProtectedText(
        String text,
        List<ProtectionAction> actions,
        Map<String, String> replacementMap
) {
    public ProtectedText {
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (replacementMap == null || replacementMap.isEmpty()) {
            replacementMap = Map.of();
        } else {
            replacementMap = Map.copyOf(new LinkedHashMap<>(replacementMap));
        }
    }

    public ProtectedText(String text, List<ProtectionAction> actions) {
        this(text, actions, Map.of());
    }
}
