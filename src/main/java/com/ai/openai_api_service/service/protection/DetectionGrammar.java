package com.ai.openai_api_service.service.protection;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared NL grammar for keyword→value detection: connector words and optional separators.
 * Kept separate from the detector so entity knowledge stays in the catalog.
 *
 * <p><b>Connector bridge rule (Phase 2A):</b> A connector may only <em>bridge</em> the keyword
 * and value. It must never change sentence semantics. Do not grow long NLP phrases
 * ({@code associated with}, {@code that is}, …). After 2A, add connectors only with Phase 2C
 * production evidence.
 *
 * <p>Separators are limited to {@code = : #}. Arrow {@code ->} is deferred. Simple parentheses
 * around the value are handled in the detector ({@code customer (45678)}), not as connectors.
 */
public final class DetectionGrammar {

    /**
     * Single-token and short multi-word bridges between keyword and value
     * (e.g. {@code salesperson is MAHESHD}, {@code customer with number 45678}).
     * Question words remain so {@code customer how} does not capture {@code how} as a value.
     */
    public static final Set<String> CONNECTOR_WORDS = Set.copyOf(new LinkedHashSet<>(Stream.of(
            // existing bridges
            "is", "are", "was", "were", "be", "been", "being",
            "for", "of", "with", "from", "to", "at", "by", "on", "in",
            "no", "number", "num", "id", "code",
            "how", "what", "which", "where", "when", "who", "why", "whom",
            // Phase 2A short additions
            "having", "whose", "named", "called", "reference", "ref",
            "with number", "having number", "identified by", "identified as"
    ).toList()));

    /** Optional punctuation between keyword/connectors and value: {@code customer=1001}. No {@code ->}. */
    public static final Set<Character> OPTIONAL_SEPARATORS = Set.of('=', ':', '#');

    private static final String CONNECTOR_ALTERNATION = CONNECTOR_WORDS.stream()
            .sorted((a, b) -> Integer.compare(b.length(), a.length())) // longer phrases first in alternation
            .map(DetectionGrammar::quoteRegex)
            .collect(Collectors.joining("|"));

    private static final String SEPARATOR_CLASS = OPTIONAL_SEPARATORS.stream()
            .map(c -> "\\" + c)
            .collect(Collectors.joining("", "[", "]"));

    private DetectionGrammar() {
    }

    /** Alternation suitable for embedding in a regex (case handled by Pattern flag). */
    public static String connectorAlternation() {
        return CONNECTOR_ALTERNATION;
    }

    /** Character class for optional separators, e.g. {@code [=:#]}. */
    public static String separatorClass() {
        return SEPARATOR_CLASS;
    }

    public static boolean isConnector(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return CONNECTOR_WORDS.contains(token.trim().toLowerCase(Locale.ROOT));
    }

    private static String quoteRegex(String word) {
        return Arrays.stream(word.split("\\s+"))
                .map(java.util.regex.Pattern::quote)
                .collect(Collectors.joining("\\s+"));
    }
}
