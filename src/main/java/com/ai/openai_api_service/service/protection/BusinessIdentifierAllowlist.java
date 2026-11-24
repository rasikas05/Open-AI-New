package com.ai.openai_api_service.service.protection;

import com.ai.openai_api_service.service.rag.ProgramIdDetector;

import java.util.Locale;
import java.util.Set;

/**
 * Product and program identifiers that must not be BIP-masked (e.g. {@code M3} is not a PRNO).
 * Legitimate business IDs such as {@code P1001} / {@code Y00111} are not listed here.
 */
public final class BusinessIdentifierAllowlist {

    private static final Set<String> PRODUCT_TOKENS = Set.of(
            "m3",
            "infor",
            "cloudsuite",
            "infor m3"
    );

    private BusinessIdentifierAllowlist() {
    }

    /**
     * True when {@code value} is a known product name or M3 program ID and must not be wrapped.
     */
    public static boolean shouldSkipMasking(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalize(value);
        if (PRODUCT_TOKENS.contains(normalized)) {
            return true;
        }
        return ProgramIdDetector.isProgramId(value.trim());
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
