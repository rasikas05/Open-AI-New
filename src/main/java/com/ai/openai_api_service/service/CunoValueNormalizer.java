package com.ai.openai_api_service.service;

import java.util.Locale;
import java.util.regex.Pattern;

final class CunoValueNormalizer {

    private static final Pattern CUNO_PATTERN = Pattern.compile("^[A-Z0-9]{1,10}$");
    private static final Pattern TRAILING_LABEL_WITH_SPACE = Pattern.compile("(?i)\\s+(NUMBER|NO|ID)$");
    private static final Pattern TRAILING_LABEL_GLUED = Pattern.compile("(?i)(NUMBER|NO|ID)$");

    private CunoValueNormalizer() {
    }

    record Result(String cuno, boolean valid, String userMessage) {
        static Result ok(String cuno) {
            return new Result(cuno, true, null);
        }

        static Result invalid(String userMessage) {
            return new Result(null, false, userMessage);
        }
    }

    static Result normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Result.invalid("Please provide a customer number.");
        }

        String normalized = stripTrailingLabelWords(raw.trim().toUpperCase(Locale.ROOT));
        if (normalized.isBlank()) {
            return Result.invalid(
                    "Please provide a valid customer number (for example, 107685 or Y11300)."
            );
        }

        if (!CUNO_PATTERN.matcher(normalized).matches()) {
            return Result.invalid(
                    "I couldn't recognize \"" + raw.trim() + "\" as a valid customer number. "
                            + "Please provide only the customer number (for example, 107685 or Y11300)."
            );
        }

        return Result.ok(normalized);
    }

    private static String stripTrailingLabelWords(String value) {
        String result = value;
        boolean changed;
        do {
            changed = false;
            String withoutSpace = TRAILING_LABEL_WITH_SPACE.matcher(result).replaceFirst("");
            if (!withoutSpace.equals(result)) {
                result = withoutSpace;
                changed = true;
            }
            String withoutGlued = TRAILING_LABEL_GLUED.matcher(result).replaceFirst("");
            if (!withoutGlued.equals(result)) {
                result = withoutGlued;
                changed = true;
            }
        } while (changed);
        return result;
    }
}
