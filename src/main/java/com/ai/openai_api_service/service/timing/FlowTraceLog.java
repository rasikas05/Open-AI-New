package com.ai.openai_api_service.service.timing;

import com.ai.openai_api_service.config.CorrelationIdSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Prints a buffered {@link FlowTrace} as one block. A formatting failure is logged
 * and swallowed so it cannot replace the chat exception that is already in flight.
 */
public final class FlowTraceLog {

    private static final Logger log = LoggerFactory.getLogger(FlowTraceLog.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private static volatile boolean ansi = true;

    private FlowTraceLog() {
    }

    public static void setAnsi(boolean enabled) {
        ansi = enabled;
    }

    public static void finish() {
        try {
            FlowTrace trace = FlowTrace.current();
            if (trace == null) {
                return;
            }
            try {
                log.info("{}", formatSummary(trace));
                if (!trace.events().isEmpty()) {
                    String block = render(trace, ansi);
                    if (block != null && !block.isBlank()) {
                        log.debug("{}", block);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("FlowTraceLog failed errorType={}", ex.getClass().getSimpleName());
            }
        } finally {
            FlowTrace.clear();
        }
    }

    static void finish(Function<FlowTrace, String> renderer, Consumer<String> sink) {
        try {
            FlowTrace trace = FlowTrace.current();
            if (trace == null || trace.events().isEmpty()) {
                return;
            }
            try {
                String block = renderer.apply(trace);
                if (block != null && !block.isBlank()) {
                    sink.accept(block);
                }
            } catch (RuntimeException ex) {
                log.warn("FlowTraceLog failed errorType={}", ex.getClass().getSimpleName());
            }
        } finally {
            FlowTrace.clear();
        }
    }

    public static String render(FlowTrace trace, boolean color) {
        if (trace == null) {
            return "";
        }
        return format(trace.events(), color);
    }

    static String formatSummary(FlowTrace trace) {
        String correlation = MDC.get(CorrelationIdSupport.MDC_KEY);
        if (correlation == null || correlation.isBlank()) {
            correlation = "-";
        }
        return """
                [FLOW] correlationId=%s mode=%s
                question=%s
                translatedEn=%s
                bip=%s
                bipPlaceholders=%s
                llmText=%s
                plannerType=%s
                plannerQueries=%s
                retrievalQuery=%s
                language=%s detected=%s locale=%s
                translation=%s route=%s planner=%s
                handler=%s lex=%s rag=%s
                %s
                llmCalls=%d durationMs=%d outcome=%s error=%s\
                """.formatted(
                correlation,
                trace.mode(),
                trace.questionText(),
                trace.translatedEnglish(),
                trace.bipText(),
                trace.bipPlaceholders(),
                trace.llmTextCheckpoint(),
                trace.plannerType(),
                trace.plannerQueries(),
                trace.retrievalQuery(),
                trace.languageState(),
                trace.detectedCode(),
                trace.localeLabel(),
                trace.translationStatus(),
                trace.routeLabel(),
                trace.plannerLabel(),
                trace.handlerLabel(),
                trace.lexLabel(),
                trace.ragLabel(),
                trace.gapFillText(),
                trace.llmCallCount(),
                trace.durationMs(),
                trace.outcome(),
                trace.errorType()
        );
    }

    static String format(List<FlowTrace.Event> events, boolean color) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        StringBuilder block = new StringBuilder();
        for (FlowTrace.Event event : events) {
            if (event == null || event.occurredAt() == null || event.stage() == null) {
                continue;
            }
            if (!block.isEmpty()) {
                block.append('\n');
            }
            String body = event.stage() + "  " + (event.message() == null ? "" : event.message());
            block.append(TIME.format(event.occurredAt()))
                    .append(' ')
                    .append(paint(body, event.color(), color));
        }
        return block.toString();
    }

    private static String paint(String text, FlowTrace.Color color, boolean enabled) {
        if (!enabled || color == null || color == FlowTrace.Color.NONE) {
            return text;
        }
        String code = switch (color) {
            case GREEN -> "32";
            case CYAN -> "36";
            case BLUE -> "34";
            case YELLOW -> "33";
            case RED -> "31";
            case NONE -> null;
        };
        if (code == null) {
            return text;
        }
        return "\u001B[" + code + "m" + text + "\u001B[0m";
    }
}
