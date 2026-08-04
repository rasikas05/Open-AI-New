package com.ai.openai_api_service.service.protection;

/**
 * Detector output only — no category or placeholder (per implementation design).
 * {@code matchedKeyword} is the catalog keyword/alias that triggered the span.
 * {@code matchBand} is deterministic diagnostic confidence (not policy).
 * {@code aliasMatch} is true when the trigger came from {@code detectionAliases}.
 */
public record DetectedSpan(
        int start,
        int end,
        String code,
        double detectionConfidence,
        String matchedKeyword,
        DetectionMatchBand matchBand,
        boolean aliasMatch
) {
    /** Backward-compatible constructor (defaults to EXACT keyword match). */
    public DetectedSpan(int start, int end, String code, double detectionConfidence, String matchedKeyword) {
        this(start, end, code, detectionConfidence, matchedKeyword, DetectionMatchBand.EXACT, false);
    }
}
