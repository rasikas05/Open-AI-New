package com.ai.openai_api_service.config;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared helpers for inbound/outbound {@code X-Correlation-Id} and SLF4J MDC.
 * Also holds optional request-scoped diagnostic keys ({@code sessionId}, {@code userId})
 * used by Bedrock observation dumps.
 */
public final class CorrelationIdSupport {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_SESSION_ID = "sessionId";
    public static final String MDC_USER_ID = "userId";

    private CorrelationIdSupport() {
    }

    /**
     * Returns a trimmed inbound id when non-blank; otherwise a new UUID.
     * Does not write MDC.
     */
    public static String resolveOrCreate(String inbound) {
        if (inbound != null) {
            String trimmed = inbound.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Idempotent: returns existing MDC value if present; otherwise generates,
     * stores in MDC, and returns the same id.
     */
    public static String current() {
        String existing = MDC.get(MDC_KEY);
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        String created = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, created);
        return created;
    }

    /**
     * Puts chat request identifiers into MDC for diagnostic dumps.
     * Blank values are removed rather than stored.
     */
    public static void putRequestIds(String sessionId, String userId) {
        putOrRemove(MDC_SESSION_ID, sessionId);
        putOrRemove(MDC_USER_ID, userId);
    }

    public static void clearRequestIds() {
        MDC.remove(MDC_SESSION_ID);
        MDC.remove(MDC_USER_ID);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
        clearRequestIds();
    }

    /**
     * Snapshot of the current thread's MDC for later restoration on a worker thread.
     * Capture this on the request thread <em>before</em> {@code supplyAsync}.
     */
    public static Map<String, String> copyContextMap() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * Runs {@code action} with {@code contextMap} installed, then restores the prior MDC.
     * Pass a map captured via {@link #copyContextMap()} on the parent request thread.
     */
    public static <T> T callWithMdc(Map<String, String> contextMap, Supplier<T> action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }
            return action.get();
        } finally {
            if (previous != null) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }

    private static void putOrRemove(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value.trim());
        } else {
            MDC.remove(key);
        }
    }
}
