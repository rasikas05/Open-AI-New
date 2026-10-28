package com.ai.openai_api_service.service.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic M3 program ID extraction (aligned with Python ingestion detect_programs).
 */
public final class ProgramIdDetector {

    private static final Pattern PROGRAM_ID = Pattern.compile(
            "\\b([A-Z]{2,4}\\d{3,4})(?:/[A-Z])?\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ProgramIdDetector() {
    }

    /**
     * Extract unique program IDs from one or more texts (uppercase, first-seen order).
     */
    public static List<String> detect(String... texts) {
        Set<String> found = new LinkedHashSet<>();
        if (texts == null) {
            return List.of();
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher matcher = PROGRAM_ID.matcher(text.toUpperCase(Locale.ROOT));
            while (matcher.find()) {
                found.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(new ArrayList<>(found));
    }

    /**
     * True when the whole token is an M3 program ID (same {@link #PROGRAM_ID} pattern as {@link #detect}).
     */
    public static boolean isProgramId(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String upper = token.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = PROGRAM_ID.matcher(upper);
        return matcher.find() && matcher.start() == 0 && matcher.end() == upper.length();
    }
}
