package com.ai.openai_api_service.service.protection;

/**
 * Deterministic diagnostic band for detection analytics (Phase 2A).
 * Does <strong>not</strong> change REPLACE / ALLOW / BLOCK policy behavior.
 */
public enum DetectionMatchBand {
    /** Catalog keyword; no connector bridge; no paren wrap. Optional direct {@code =:#}. */
    EXACT,
    /** Matched a catalog alias (system abbreviation path). */
    ALIAS,
    /** Keyword or alias with connector bridge(s) and/or simple parentheses around the value. */
    GRAMMAR,
    /** Soft natural keyword phrases (e.g. {@code customer reference}) — still a valid hit. */
    WEAK
}
