package com.ai.openai_api_service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CunoValueNormalizerTest {

    @Test
    void normalize_uppercasesValue() {
        CunoValueNormalizer.Result result = CunoValueNormalizer.normalize("y11300");

        assertTrue(result.valid());
        assertEquals("Y11300", result.cuno());
    }

    @Test
    void normalize_stripsTrailingNumberLabelWithSpace() {
        CunoValueNormalizer.Result result = CunoValueNormalizer.normalize("107685 number");

        assertTrue(result.valid());
        assertEquals("107685", result.cuno());
    }

    @Test
    void normalize_stripsGluedTrailingNumberLabel() {
        CunoValueNormalizer.Result result = CunoValueNormalizer.normalize("107685number");

        assertTrue(result.valid());
        assertEquals("107685", result.cuno());
    }

    @Test
    void normalize_stripsTrailingIdLabel() {
        CunoValueNormalizer.Result result = CunoValueNormalizer.normalize("CSU001 id");

        assertTrue(result.valid());
        assertEquals("CSU001", result.cuno());
    }

    @Test
    void normalize_rejectsInvalidCharacters() {
        CunoValueNormalizer.Result result = CunoValueNormalizer.normalize("107685-NUMBER");

        assertFalse(result.valid());
        assertTrue(result.userMessage().contains("valid customer number"));
    }

    @Test
    void normalize_rejectsBlankValue() {
        CunoValueNormalizer.Result result = CunoValueNormalizer.normalize("   ");

        assertFalse(result.valid());
        assertEquals("Please provide a customer number.", result.userMessage());
    }
}
