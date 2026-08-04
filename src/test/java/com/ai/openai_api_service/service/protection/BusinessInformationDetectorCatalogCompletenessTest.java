package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Catalog completeness gate: every seeded entity must prove POSITIVE, ALIAS (or N/A), and NL detection
 * with configured metadata before expanding aliases/grammar.
 */
class BusinessInformationDetectorCatalogCompletenessTest {

    private static final FieldClassificationCatalog CATALOG = new FieldClassificationCatalog();

    /** One valid sample value per catalog code (must satisfy that row's shape). */
    private static final Map<String, String> SAMPLE_VALUES = Map.ofEntries(
            Map.entry("CUNO", "45678"),
            Map.entry("ORNO", "SO10001"),
            Map.entry("PUNO", "PO450001"),
            Map.entry("MFNO", "MF123456"),
            Map.entry("TRNR", "800001"),
            Map.entry("SUNO", "SUP99"),
            Map.entry("PRNO", "P1001"),
            Map.entry("WHLO", "A01"),
            Map.entry("FACI", "F01"),
            Map.entry("DIVI", "D01"),
            Map.entry("MAIL", "a@b.com"),
            Map.entry("PHNO", "5551234567"),
            Map.entry("RESP", "ALICE01"),
            Map.entry("SMCD", "MAHESHD"),
            Map.entry("BUYE", "BUYER1"),
            Map.entry("CRLM", "10000"),
            Map.entry("NTAM", "5000"),
            Map.entry("KSTR", "KEY123")
    );

    private BusinessInformationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BusinessInformationDetector(CATALOG, new ValueShapeValidator());
    }

    @Test
    void everyCatalogCodeHasASampleValue() {
        Set<String> catalogCodes = CATALOG.all().stream().map(FieldClassification::code).collect(Collectors.toSet());
        assertEquals(catalogCodes, SAMPLE_VALUES.keySet(),
                "SAMPLE_VALUES must cover every catalog code exactly (add sample when seeding a new entity)");
    }

    @ParameterizedTest(name = "{0} | {1} | {2}")
    @MethodSource("completenessCases")
    void catalogEntity_detectionProof(String code, String proofKind, String utterance, String expectedValue) {
        FieldClassification row = CATALOG.lookup(code).orElseThrow();

        if ("ALIAS_NA".equals(proofKind)) {
            assertTrue(row.detectionAliases().isEmpty(),
                    code + " marked ALIAS_NA but has aliases: " + row.detectionAliases());
            return;
        }

        List<DetectedSpan> spans = detector.detect(utterance);
        assertFalse(spans.isEmpty(), () -> code + " " + proofKind + " produced no spans for: " + utterance);
        assertTrue(
                spans.stream().anyMatch(s -> code.equals(s.code())
                        && expectedValue.equalsIgnoreCase(utterance.substring(s.start(), s.end()))),
                () -> code + " " + proofKind + " missing " + code + "=" + expectedValue
                        + " in " + spans + " for utterance=[" + utterance + "]"
        );
    }

    static Stream<Arguments> completenessCases() {
        Map<String, List<Arguments>> byCode = new LinkedHashMap<>();
        for (FieldClassification row : CATALOG.all()) {
            String code = row.code();
            String value = SAMPLE_VALUES.get(code);
            if (value == null) {
                fail("No SAMPLE_VALUES entry for catalog code " + code);
            }
            String primaryKeyword = longestPhrase(row.detectionKeywords());
            String positive = joinKeywordValue(primaryKeyword, value);
            String nl = "Please check " + primaryKeyword + " " + value + " in Infor M3";

            List<Arguments> cases = new java.util.ArrayList<>();
            cases.add(Arguments.of(code, "POSITIVE", positive, value));
            if (row.detectionAliases().isEmpty()) {
                cases.add(Arguments.of(code, "ALIAS_NA", "", ""));
            } else {
                String alias = row.detectionAliases().get(0);
                cases.add(Arguments.of(code, "ALIAS", joinKeywordValue(alias, value), value));
            }
            cases.add(Arguments.of(code, "NL", nl, value));
            byCode.put(code, cases);
        }
        return byCode.values().stream().flatMap(List::stream);
    }

    private static String longestPhrase(List<String> phrases) {
        return phrases.stream()
                .max(Comparator.comparingInt(String::length).thenComparing(s -> s))
                .orElseThrow();
    }

    private static String joinKeywordValue(String keyword, String value) {
        String trimmed = keyword.trim();
        char last = trimmed.charAt(trimmed.length() - 1);
        if (!Character.isLetterOrDigit(last)) {
            // cust# / po# / mo# / do# / so# attach value directly
            return trimmed + value;
        }
        return trimmed + " " + value;
    }
}
