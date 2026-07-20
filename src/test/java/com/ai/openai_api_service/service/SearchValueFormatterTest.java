package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchValueFormatterTest {

    private final SearchValueFormatter formatter = new SearchValueFormatter();

    @Test
    void format_ordt_isoDate_convertsToCompact() {
        assertEquals("20260424", formatter.format("ORDT", "2026-04-24"));
    }

    @Test
    void format_pudt_isoDate_convertsToCompact() {
        assertEquals("20260424", formatter.format("PUDT", "2026-04-24"));
    }

    @Test
    void format_ordt_compactDate_unchanged() {
        assertEquals("20260424", formatter.format("ORDT", "20260424"));
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
}
