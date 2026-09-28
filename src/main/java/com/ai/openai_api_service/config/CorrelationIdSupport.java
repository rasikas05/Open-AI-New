package com.ai.openai_api_service.config;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Shared helpers for inbound/outbound {@code X-Correlation-Id} and SLF4J MDC.
 */
public final class CorrelationIdSupport {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

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

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
