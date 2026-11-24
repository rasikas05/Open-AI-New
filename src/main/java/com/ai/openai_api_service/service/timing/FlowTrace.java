package com.ai.openai_api_service.service.timing;

import com.ai.openai_api_service.service.i18n.LanguageDetectionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request-scoped events for the colored flow log. Events are stored on the
 * chat request thread and printed together when the turn ends.
 */
public final class FlowTrace {

    public enum Color {
        NONE,
        GREEN,
        CYAN,
        BLUE,
        YELLOW,
        RED
    }

    public record Event(Instant occurredAt, String stage, String message, Color color) {
    }

    private static final ThreadLocal<FlowTrace> CURRENT = new ThreadLocal<>();

    private final List<Event> events = new ArrayList<>();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private String languageState = "certain";
    private String detectedCode = "none";
    private String localeLabel = "unchanged";
    private String translationStatus = "skipped";
    private String gapFillStatus = "skipped";
    private String gapFillReason = "not_applicable";
    private String mode = "-";
    private String routeLabel = "skipped";
    private String plannerLabel = "skipped";
    private String handlerLabel = "-";
    private String lexLabel = "skipped";
    private String ragLabel = "skipped";
    private long durationMs;
    private String outcome = "SUCCESS";
    private String errorType = "none";
    private String questionText = "-";
    private String translatedEnglish = "-";
    private String bipText = "-";
    private String llmText = "-";
    private String bipPlaceholders = "-";
    private String plannerType = "-";
    private String plannerQueries = "-";
    private String retrievalQuery = "-";

    private FlowTrace() {
    }

    public static void begin() {
        CURRENT.set(new FlowTrace());
    }

    public static FlowTrace current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Bind this request's trace onto a worker thread. Does not replace a trace already current. */
    public static void attach(FlowTrace trace) {
        if (trace != null) {
            CURRENT.set(trace);
        }
    }

    public static void detach() {
        CURRENT.remove();
    }

    public static void recordLlmCall() {
        FlowTrace trace = CURRENT.get();
        if (trace != null) {
            trace.llmCalls.incrementAndGet();
        }
    }

    public static void markLanguage(String state, String detected, String locale) {
        FlowTrace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.languageState = shown(state);
        trace.detectedCode = shown(detected);
        trace.localeLabel = shown(locale);
    }

    public static void markTranslation(String status) {
        FlowTrace trace = CURRENT.get();
        if (trace != null && status != null && !status.isBlank()) {
            trace.translationStatus = status;
        }
    }

    public static void markGapFill(String status, String reason) {
        FlowTrace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.gapFillStatus = status == null || status.isBlank() ? "skipped" : status;
        trace.gapFillReason = reason;
    }

    public static void markRouting(
            String mode,
            String pythonRoute,
            String planner,
            String handler,
            boolean lexCalled,
            boolean ragCalled,
            long durationMs
    ) {
        FlowTrace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.mode = shown(mode);
        trace.routeLabel = routeLabel(pythonRoute);
        trace.plannerLabel = skippedOrValue(planner);
        trace.handlerLabel = shown(handler);
        trace.lexLabel = lexCalled ? "called" : "skipped";
        trace.ragLabel = ragCalled ? "called" : "skipped";
        trace.durationMs = Math.max(0L, durationMs);
    }

    public static void markOutcome(String outcome, String errorType) {
        FlowTrace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.outcome = shown(outcome);
        trace.errorType = shown(errorType);
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public static void question(String text) {
        FlowTrace trace = CURRENT.get();
        String shownQuestion = cap(text);
        if (trace != null) {
            trace.questionText = shownQuestion;
        }
        record("CHAT", "question=" + shownQuestion, Color.NONE);
    }

    public static void language(LanguageDetectionResult detected) {
        if (detected == null) {
            return;
        }
        // Keep detected vs selected distinct — mixed-script policy may select en while detected stays non-en.
        String message = "detected=" + shown(detected.detectedLanguage())
                + " detectedLocale=" + shown(detected.detectedLocale())
                + " selected=" + shown(detected.selectedLanguage())
                + " selectedLocale=" + shown(detected.selectedLocale())
                + " fallback=" + detected.fallback();
        record("LANG", message, Color.GREEN);
    }

    public static void translationSkipped(String source) {
        markTranslation("skipped");
        if (source == null || source.isBlank()) {
            record("TRANS", "status=SKIPPED", Color.CYAN);
            return;
        }
        record("TRANS", "source=" + source + " status=SKIPPED", Color.CYAN);
    }

    public static void translationOk(String source, String english) {
        markTranslation("ok");
        FlowTrace trace = CURRENT.get();
        if (trace != null) {
            trace.translatedEnglish = cap(english);
        }
        record(
                "TRANS",
                "source=" + shown(source) + " target=en status=OK text=" + cap(english),
                Color.CYAN
        );
    }

    /**
     * BIP output (pre-PII) and post-PII {@code textForLlm} for side-by-side checkpoint diffs.
     * {@code placeholderTypes} must be placeholder type labels only — never original values.
     */
    public static void protectionCheckpoint(
            String bipText,
            String llmText,
            boolean businessApplied,
            String placeholderTypes
    ) {
        FlowTrace trace = CURRENT.get();
        if (trace != null) {
            trace.bipText = bipText == null ? "-" : cap(bipText);
            trace.llmText = llmText == null ? "-" : cap(llmText);
            trace.bipPlaceholders = shown(placeholderTypes);
        }
        record(
                "PROT",
                "businessApplied=" + businessApplied
                        + " placeholders=" + shown(placeholderTypes)
                        + " bip=" + (bipText == null ? "-" : cap(bipText))
                        + " llmText=" + (llmText == null ? "-" : cap(llmText)),
                Color.BLUE
        );
    }

    /**
     * Planner type and up to three query strings as {@code q1=…; q2=…; q3=…}.
     * Null or empty queries store {@code -} (not an empty string).
     */
    public static void plannerQueries(String type, List<String> queries) {
        FlowTrace trace = CURRENT.get();
        String formatted = formatNumberedQueries(queries, 3);
        if (trace != null) {
            trace.plannerType = shown(type);
            trace.plannerQueries = formatted;
        }
        record(
                "PLAN",
                "type=" + shown(type) + " queries=" + formatted,
                Color.BLUE
        );
    }

    /**
     * Exact assembled retrieval query list (capped, numbered). Null/empty → {@code -}.
     */
    public static void retrievalQuery(List<String> queries) {
        FlowTrace trace = CURRENT.get();
        String formatted = formatNumberedQueries(queries, 4);
        if (trace != null) {
            trace.retrievalQuery = formatted;
        }
        record("RETR", "queries=" + formatted, Color.BLUE);
    }

    public static void retrievalQuery(String query) {
        if (query == null || query.isBlank()) {
            retrievalQuery(List.of());
            return;
        }
        retrievalQuery(List.of(query));
    }

    public static void translationFailed(String source, String errorType) {
        markTranslation("failed");
        record(
                "TRANS",
                "source=" + shown(source) + " target=en status=FAILED errorType=" + shown(errorType),
                Color.RED
        );
    }

    public static void route(String pythonRoute) {
        record("ROUTE", "python=" + pythonRoute, Color.GREEN);
    }

    public static void routeEmpty() {
        record("ROUTE", "python=-", Color.NONE);
    }

    public static void routeFailed(String errorType) {
        record("ROUTE", "status=FAILED errorType=" + shown(errorType), Color.RED);
    }

    public static void comprehendEntities(int entityCount) {
        record("API", "comprehendEntities=" + entityCount, Color.BLUE);
    }

    public static void presidioChanged(boolean textChanged) {
        record("API", "presidio textChanged=" + textChanged, Color.BLUE);
    }

    public static void apiFailed(String api, String errorType) {
        record("API", api + " status=FAILED errorType=" + shown(errorType), Color.RED);
    }

    public static void lex(String intent, String state, String dialogAction) {
        record(
                "LEX",
                "intent=" + shown(intent) + " state=" + shown(state) + " dialogAction=" + shown(dialogAction),
                Color.GREEN
        );
    }

    public static void lexFailed(String errorType) {
        record("LEX", "status=FAILED errorType=" + shown(errorType), Color.RED);
    }

    public static void slotRejected(String slotName, String value) {
        if (value == null) {
            return;
        }
        record("SLOT", "rejected " + shown(slotName) + "=" + cap(value), Color.YELLOW);
    }

    public static void slotRepaired(String slotName, String value) {
        if (value == null) {
            return;
        }
        record("SLOT", "repaired " + shown(slotName) + "=" + cap(value), Color.GREEN);
    }

    public static void mi(String program, String transaction, String params) {
        record(
                "MI",
                "program=" + shown(program) + " transaction=" + shown(transaction) + " params=" + cap(params),
                Color.GREEN
        );
    }

    public static void m3Handoff() {
        record("M3", "handedToWidget=true Spring M3 execution=NO", Color.BLUE);
    }

    public static void responseLocale(String locale) {
        record(
                "CHAT",
                "locale=" + shown(locale) + " outgoingTranslation=skipped widgetLabels=rendered by widget",
                Color.NONE
        );
    }

    String questionText() {
        return questionText;
    }

    String translatedEnglish() {
        return translatedEnglish;
    }

    String bipText() {
        return bipText;
    }

    String llmTextCheckpoint() {
        return llmText;
    }

    String bipPlaceholders() {
        return bipPlaceholders;
    }

    String plannerType() {
        return plannerType;
    }

    String plannerQueries() {
        return plannerQueries;
    }

    String retrievalQuery() {
        return retrievalQuery;
    }

    String languageState() {
        return languageState;
    }

    String detectedCode() {
        return detectedCode;
    }

    String localeLabel() {
        return localeLabel;
    }

    String translationStatus() {
        return translationStatus;
    }

    String gapFillText() {
        if ("called".equals(gapFillStatus)) {
            return "gapFill=called";
        }
        String reason = gapFillReason == null || gapFillReason.isBlank() ? "not_applicable" : gapFillReason;
        return "gapFill=skipped reason=" + reason;
    }

    String mode() {
        return mode;
    }

    String routeLabel() {
        return routeLabel;
    }

    String plannerLabel() {
        return plannerLabel;
    }

    String handlerLabel() {
        return handlerLabel;
    }

    String lexLabel() {
        return lexLabel;
    }

    String ragLabel() {
        return ragLabel;
    }

    long durationMs() {
        return durationMs;
    }

    int llmCallCount() {
        return llmCalls.get();
    }

    String outcome() {
        return outcome;
    }

    String errorType() {
        return errorType;
    }

    private static String routeLabel(String pythonRoute) {
        if (pythonRoute == null || pythonRoute.isBlank() || "-".equals(pythonRoute) || "skip".equalsIgnoreCase(pythonRoute)) {
            return "skipped";
        }
        return pythonRoute.toLowerCase(Locale.ROOT);
    }

    private static String skippedOrValue(String value) {
        if (value == null || value.isBlank() || "-".equals(value) || "skip".equalsIgnoreCase(value)) {
            return "skipped";
        }
        return value;
    }

    static void record(String stage, String message, Color color) {
        FlowTrace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        trace.events.add(new Event(Instant.now(), stage, message, color == null ? Color.NONE : color));
    }

    private static String displayLanguage(String locale) {
        if (locale == null || locale.isBlank() || "none".equalsIgnoreCase(locale)) {
            return null;
        }
        Locale parsed = Locale.forLanguageTag(locale.replace('_', '-'));
        if (parsed.getLanguage() == null || parsed.getLanguage().isBlank()) {
            return null;
        }
        String display = parsed.getDisplayLanguage(Locale.ENGLISH);
        if (display == null || display.isBlank()) {
            return null;
        }
        return display;
    }

    private static String cap(String text) {
        return RoutingSummaryLog.oneLine(text, RoutingSummaryLog.MAX_REQUEST_CHARS);
    }

    private static String shown(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Numbered {@code q1=…; q2=…} segments. Each value is one-lined; missing list → {@code -}.
     */
    public static String formatNumberedQueries(List<String> queries, int max) {
        if (queries == null || queries.isEmpty() || max < 1) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(max, queries.size());
        int written = 0;
        for (int i = 0; i < limit; i++) {
            String q = queries.get(i);
            if (q == null || q.isBlank()) {
                continue;
            }
            if (written > 0) {
                sb.append("; ");
            }
            written++;
            sb.append('q').append(written).append('=').append(cap(q));
        }
        return written == 0 ? "-" : sb.toString();
    }
}
