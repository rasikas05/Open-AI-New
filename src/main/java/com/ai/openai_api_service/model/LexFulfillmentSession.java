package com.ai.openai_api_service.model;

/**
 * Session identifiers for server-side search pagination state (in-memory v1).
 */
public record LexFulfillmentSession(String tenantCode, String userId, String sessionId) {

    public boolean isComplete() {
        return tenantCode != null && !tenantCode.isBlank()
                && userId != null && !userId.isBlank()
                && sessionId != null && !sessionId.isBlank();
    }

    public static LexFulfillmentSession of(String tenantCode, String userId, String sessionId) {
        return new LexFulfillmentSession(tenantCode, userId, sessionId);
    }
}
