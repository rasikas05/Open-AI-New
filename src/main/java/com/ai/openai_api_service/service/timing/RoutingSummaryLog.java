package com.ai.openai_api_service.service.timing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compact one-line operational routing log. {@code total=} is ComprehendChatService
 * wall-clock time (serviceStart to finally), not the sum of outbound HTTP/LEX calls.
 */
public final class RoutingSummaryLog {

    private static final Logger log = LoggerFactory.getLogger(RoutingSummaryLog.class);
    static final int MAX_REQUEST_CHARS = 120;

    private RoutingSummaryLog() {
    }

    public static void log(RoutingSummaryState state, long serviceWallMs) {
        if (state == null) {
            return;
        }
        log.info("{}", format(state, RoutingCallTracker.lexCalled(), RoutingCallTracker.ragCalled(), serviceWallMs));
    }

    public static String format(
            RoutingSummaryState state,
            boolean lexCalled,
            boolean ragCalled,
            long serviceWallMs
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("[ROUTING] \"" + oneLine(state.getRequestText(), MAX_REQUEST_CHARS) + "\"");
        parts.add("mode=" + state.getMode());
        String compactRouter = compactRouter(state.getRouter());
        if (compactRouter != null) {
            parts.add("router=" + compactRouter);
        }
        if (state.getType() != null && !state.getType().isBlank() && !"-".equals(state.getType())) {
            parts.add("type=" + state.getType());
        }
        if (state.getOverride() != null && !state.getOverride().isBlank() && !"none".equals(state.getOverride())) {
            parts.add("override=" + state.getOverride().replace(" -> ", "->"));
        }
        parts.add("route=" + state.getRoute());
        parts.add("handler=" + state.getHandler());
        if (state.getIntent() != null && !state.getIntent().isBlank() && !"-".equals(state.getIntent())) {
            parts.add("intent=" + state.getIntent());
        }
        parts.add("action=" + state.getAction());
        parts.add("lex=" + (lexCalled ? "CALLED" : "SKIP"));
        parts.add("rag=" + (ragCalled ? "CALLED" : "SKIP"));
        parts.add("total=" + formatSeconds(serviceWallMs));
        return String.join(" | ", parts);
    }

    /** Successful OpenAI router calls are omitted; skip reasons stay as SKIP(...). */
    static String compactRouter(String router) {
        if (router == null || router.isBlank() || "-".equals(router)) {
            return null;
        }
        if (router.startsWith("OpenAI /") || router.startsWith("OpenAI/")) {
            return null;
        }
        if (router.startsWith("skipped (")) {
            String inner = router.substring("skipped (".length());
            if (inner.endsWith(")")) {
                inner = inner.substring(0, inner.length() - 1);
            }
            return "SKIP(" + inner + ")";
        }
        return router;
    }

    static String formatSeconds(long millis) {
        return String.format(Locale.ROOT, "%.2fs", Math.max(0L, millis) / 1000.0);
    }

    static String oneLine(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String flattened = text.replace('\r', ' ').replace('\n', ' ').replace('"', '\'').strip();
        if (flattened.length() <= maxChars) {
            return flattened;
        }
        return flattened.substring(0, maxChars);
    }
}
