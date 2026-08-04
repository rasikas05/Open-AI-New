package com.ai.openai_api_service.service.protection;

/**
 * Catalog-level character-set metadata for M3 identifiers.
 * Regex mapping lives in {@link ValueShapeValidator} — not in the catalog.
 */
public enum IdentifierCharacterSet {
    /** Letters A–Z and digits 0–9 only (after uppercasing). */
    ALPHANUMERIC
}
