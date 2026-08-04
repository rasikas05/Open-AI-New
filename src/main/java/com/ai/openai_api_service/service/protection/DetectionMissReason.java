package com.ai.openai_api_service.service.protection;

/**
 * Why a likely entity mention did not produce an accepted span (Phase 2A diagnostics).
 */
public enum DetectionMissReason {
    VALUE_MISSING,
    SHAPE_INVALID,
    CONNECTOR_INVALID,
    VALUE_BEFORE_KEYWORD,
    RESERVED_VALUE
}
