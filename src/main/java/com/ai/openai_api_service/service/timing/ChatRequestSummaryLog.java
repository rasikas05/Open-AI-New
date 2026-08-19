package com.ai.openai_api_service.service.timing;

/**
 * One-request human-readable summaries. Timing values must be existing measured buckets only.
 */
public final class ChatRequestSummaryLog {

    private ChatRequestSummaryLog() {
    }

    public static String formatChat(
            String input,
            String mode,
            String type,
            String route,
            String handler,
            String intent,
            String action
    ) {
        return "[CHAT] input=\"" + RoutingSummaryLog.oneLine(input, RoutingSummaryLog.MAX_REQUEST_CHARS)
                + "\" | mode=" + dash(mode)
                + " | type=" + dash(type)
                + " | route=" + dash(route)
                + " | handler=" + dash(handler)
                + " | intent=" + dash(intent)
                + " | action=" + dash(action);
    }

    public static String formatTiming(
            long piiMs,
            long routerMs,
            long lexMs,
            long m3OrQdrantMs,
            long groundingMs,
            long persistenceMs,
            long suggestionsMs,
            long totalMs
    ) {
        return "[TIMING] pii=" + piiMs
                + "ms | router=" + routerMs
                + "ms | lex=" + lexMs
                + "ms | m3OrQdrant=" + m3OrQdrantMs
                + "ms | grounding=" + groundingMs
                + "ms | persistence=" + persistenceMs
                + "ms | suggestions=" + suggestionsMs
                + "ms | total=" + totalMs
                + "ms";
    }

    public static String formatTokens(
            int router,
            int grounding,
            int gapFill,
            int suggestions,
            int total
    ) {
        return "[TOKENS] router=" + router
                + " | grounding=" + grounding
                + " | gapFill=" + gapFill
                + " | suggestions=" + suggestions
                + " | total=" + total;
    }

    static String dash(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? "-" : value;
    }
}
