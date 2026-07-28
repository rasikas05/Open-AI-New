package com.ai.openai_api_service.service.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the multi-query search set: sanitized original first, then rewritten queries.
 * Trims, drops blanks, and case-insensitive dedupes while preserving first-seen order.
 */
public final class SearchQueryAssembler {

    private SearchQueryAssembler() {
    }

    /**
     * @param sanitizedOriginal sanitized user text (always included when non-blank)
     * @param rewrittenQueries  CLEAR rewrite results (may be null/empty)
     * @param maxQueries        max total queries after union (typically 4 = 1 + 3 rewrites)
     */
    public static List<String> assemble(String sanitizedOriginal, List<String> rewrittenQueries, int maxQueries) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addIfNew(ordered, seen, sanitizedOriginal);
        if (rewrittenQueries != null) {
            for (String q : rewrittenQueries) {
                if (ordered.size() >= maxQueries) {
                    break;
                }
                addIfNew(ordered, seen, q);
            }
        }
        return List.copyOf(ordered);
    }

    private static void addIfNew(List<String> ordered, Set<String> seen, String raw) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        if (seen.add(key)) {
            ordered.add(trimmed);
        }
    }
}
