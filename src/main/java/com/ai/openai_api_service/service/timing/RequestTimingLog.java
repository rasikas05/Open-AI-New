package com.ai.openai_api_service.service.timing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Phase 4 helpers: ISO stage timestamps and residual accounting.
 */
public final class RequestTimingLog {
    private static final Logger log = LoggerFactory.getLogger(RequestTimingLog.class);

    public static final String REQUEST_ATTR = "com.ai.openai_api_service.ComprehendChatTimingSnapshot";

    private RequestTimingLog() {
    }

    public static void logStage(String stage, Instant start, Instant end) {
        long durationMs = Math.max(0L, end.toEpochMilli() - start.toEpochMilli());
        log.debug(
                "Stage Timing | stage={} | start={} | end={} | durationMs={}",
                stage,
                start,
                end,
                durationMs
        );
    }

    public static void logStage(String stage, long startEpochMs, long endEpochMs) {
        logStage(stage, Instant.ofEpochMilli(startEpochMs), Instant.ofEpochMilli(endEpochMs));
    }

    public static long durationMs(Instant start, Instant end) {
        return Math.max(0L, end.toEpochMilli() - start.toEpochMilli());
    }

    public static Residual computeResidual(long measuredSumMs, long totalMs) {
        long residual = Math.max(0L, totalMs - measuredSumMs);
        double pct = totalMs > 0 ? (100.0 * residual / totalMs) : 0.0;
        return new Residual(measuredSumMs, totalMs, residual, pct);
    }

    public static void logResidual(Residual residual, String totalScope) {
        log.debug(
                "Request Timing Residual | measuredSum={}ms | total={}ms | residual={}ms | residualPct={} | totalScope={}",
                residual.measuredSumMs(),
                residual.totalMs(),
                residual.residualMs(),
                String.format(java.util.Locale.ROOT, "%.2f", residual.residualPct()),
                totalScope
        );
    }

    public record Residual(long measuredSumMs, long totalMs, long residualMs, double residualPct) {
    }
}
