package com.ai.openai_api_service.service.protection;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Restores business placeholders in LLM replies using {@link ProtectionSession#replacementMap()} (7B.2).
 * Does not mutate protection text fields (Decision #28).
 */
@Component
public class BusinessPlaceholderRestorer {

    public String restore(String llmReply, ProtectionSession session) {
        if (llmReply == null || session == null) {
            return llmReply;
        }
        Map<String, String> map = session.replacementMap();
        if (map == null || map.isEmpty()) {
            return llmReply;
        }
        String restored = llmReply;
        // Longer placeholders first to avoid partial collisions
        for (Map.Entry<String, String> e : map.entrySet()) {
            String placeholder = e.getKey();
            String original = e.getValue();
            if (placeholder != null && original != null && restored.contains(placeholder)) {
                restored = restored.replace(placeholder, original);
            }
        }
        return restored;
    }

    /** Applies restore into session reply artifacts; returns restored text for UI. */
    public String restoreIntoSession(String llmReply, ProtectionSession session) {
        String restored = restore(llmReply, session);
        if (session != null) {
            session.applyRestoredReply(llmReply, restored);
        }
        return restored;
    }
}
