package com.ai.openai_api_service.model;

import java.time.Instant;
import java.util.Map;

/**
 * Server-side pagination state for a single live search (session-scoped, in-memory v1).
 */
public record SearchContext(
        String searchContextId,
        String intentName,
        String program,
        String transaction,
        String queryFingerprint,
        Map<String, Object> recordParams,
        String positionKey,
        Integer pageSize,
        long version,
        Instant updatedAt
) {
    public SearchContext {
        recordParams = recordParams != null ? Map.copyOf(recordParams) : Map.of();
    }
}
