package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchValueFormatterTest {

    private final SearchValueFormatter formatter = new SearchValueFormatter();

    @ParameterizedTest
    @ValueSource(strings = {"ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"})
    void format_dateFields_isoDash(String field) {
        assertEquals("20260424", formatter.format(field, "2026-04-24"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"})
    void format_dateFields_isoSlash(String field) {
        assertEquals("20260424", formatter.format(field, "2026/04/24"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"})
    void format_dateFields_compact(String field) {
        assertEquals("20260424", formatter.format(field, "20260424"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"})
    void format_dateFields_yymmdd(String field) {
        assertEquals("20260424", formatter.format(field, "260424"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"})
    void format_dateFields_ddMmYyyy(String field) {
        assertEquals("20260424", formatter.format(field, "24/04/2026"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDT", "PUDT", "RLDZ", "STDT", "FIDT", "RIDT"})
    void format_dateFields_ddDashMmYyyy(String field) {
        assertEquals("20260424", formatter.format(field, "24-04-2026"));
    }

    @Test
    void format_ambiguousSlashDate_prefersDdMm() {
        assertEquals("20260201", formatter.format("ORDT", "01/02/2026"));
    }

    @Test
    void format_mmDdWhenSecondPartGt12() {
        assertEquals("20260115", formatter.format("ORDT", "01/15/2026"));
    }

    @Test
    void format_ddMmWhenFirstPartGt12() {
        assertEquals("20260115", formatter.format("ORDT", "15/01/2026"));
    }

    @Test
    void format_unknownDate_passthrough() {
        assertEquals("next week", formatter.format("ORDT", "next week"));
    }

    @Test
    void format_cuno_unchanged() {
        assertEquals("C00001", formatter.format("CUNO", "C00001"));
    }

    @Test
    void format_faci_unchanged() {
        assertEquals("A01", formatter.format("FACI", "A01"));
    }

    @Test
    void format_orst_unchanged() {
        assertEquals("33", formatter.format("ORST", "33"));
    }

    @Test
    void format_nullValue_returnsNull() {
        assertNull(formatter.format("ORDT", null));
    }

    @Test
    void format_trimsWhitespace() {
        assertEquals("C00001", formatter.format("CUNO", "  C00001  "));
    }

    @ParameterizedTest
    @CsvSource({
            "ORDT, 2026-04-24, 20260424",
            "PUDT, 2026/04/24, 20260424",
            "RLDZ, 20260424, 20260424",
            "STDT, 24/04/2026, 20260424",
            "FIDT, 24-04-2026, 20260424",
            "RIDT, 260424, 20260424"
    })
    void format_eachDateFieldFormat(String field, String input, String expected) {
        assertEquals(expected, formatter.format(field, input));
    }
}
