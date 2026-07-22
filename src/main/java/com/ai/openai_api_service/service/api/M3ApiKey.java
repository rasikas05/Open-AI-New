package com.ai.openai_api_service.service.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Identifies an M3 MI endpoint (program + transaction) for API-aware field capability.
 */
public record M3ApiKey(String program, String transaction) {

    public M3ApiKey {
        program = normalize(program);
        transaction = normalize(transaction);
    }

    public static M3ApiKey of(String program, String transaction) {
        return new M3ApiKey(program, transaction);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof M3ApiKey that)) {
            return false;
        }
        return program.equalsIgnoreCase(that.program) && transaction.equalsIgnoreCase(that.transaction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(program.toUpperCase(Locale.ROOT), transaction.toUpperCase(Locale.ROOT));
    }
}
