package com.ai.openai_api_service.service.timing;

import java.util.Locale;

/**
 * One-request human-readable summaries. Timing values must be existing measured buckets only.
 *
 * <p>Phase 2 measure protocol (no optimization): run each case several times and read {@code [TIMING]}:
 * <ul>
 *   <li>Conversational — e.g. {@code hi} (M3/AUTO): {@code python}, {@code pii}, {@code openai},
 *       {@code persistence}, {@code suggestions}, {@code total}</li>
 *   <li>RAG — e.g. {@code What is OIS100?} (AUTO/DOCS): above plus {@code m3OrQdrant}/{@code grounding}</li>
 *   <li>LIVE — e.g. {@code Show customer Y00111}: {@code python}, lex path, persistence; planner SKIP so
 *       {@code openai} should be {@code 0.00s}</li>
 * </ul>
 * {@code pii} remains combined (Comprehend + Presidio not split). {@code openai} is planner HTTP only;
 * it must not include PII. Compare majority wall time vs tokens, then pick one later optimization.
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

    /**
     * Additive stage buckets. {@code openaiMs} is understandRequest only (excludes PII).
     * {@code pythonMs} is Python {@code /route} HTTP responseTime.
     */
    public static String formatTiming(
            long pythonMs,
            long piiMs,
            long openaiMs,
            long lexMs,
            long m3OrQdrantMs,
            long groundingMs,
            long persistenceMs,
            long suggestionsMs,
            long totalMs
    ) {
        return "[TIMING] python=" + seconds(pythonMs)
                + " | pii=" + seconds(piiMs)
                + " | openai=" + seconds(openaiMs)
                + " | lex=" + seconds(lexMs)
                + " | m3OrQdrant=" + seconds(m3OrQdrantMs)
                + " | grounding=" + seconds(groundingMs)
                + " | persistence=" + seconds(persistenceMs)
                + " | suggestions=" + seconds(suggestionsMs)
                + " | total=" + seconds(totalMs);
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

    static String seconds(long durationMs) {
        return String.format(Locale.ROOT, "%.2fs", Math.max(0L, durationMs) / 1000.0);
    }

    static String dash(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? "-" : value;
    }
}
