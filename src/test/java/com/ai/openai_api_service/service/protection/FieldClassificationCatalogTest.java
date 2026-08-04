package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldClassificationCatalogTest {

    private FieldClassificationCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new FieldClassificationCatalog();
    }

    @Test
    void lookup_cuno_isIdentifierWithMaxLength10() {
        Optional<FieldClassification> result = catalog.lookup("cuno");
        assertTrue(result.isPresent());
        assertEquals("CUNO", result.get().code());
        assertEquals(InformationCategory.BDI, result.get().category());
        assertEquals(LlmExposurePolicy.REPLACE, result.get().llmExposurePolicy());
        assertFalse(result.get().detectionKeywords().isEmpty());
        assertEquals(ValueShapeValidator.M3_IDENTIFIER, result.get().valueShapeKey());
        assertEquals(10, result.get().maxLength());
        assertEquals(IdentifierCharacterSet.ALPHANUMERIC, result.get().characterSet());
    }

    @Test
    void lookup_prno_isIdentifierWithMaxLength15() {
        FieldClassification prno = catalog.lookup("PRNO").orElseThrow();
        assertEquals(ValueShapeValidator.M3_IDENTIFIER, prno.valueShapeKey());
        assertEquals(15, prno.maxLength());
        assertEquals(IdentifierCharacterSet.ALPHANUMERIC, prno.characterSet());
    }

    @Test
    void lookup_warehouse_hasNoIdentifierMetadata() {
        FieldClassification whlo = catalog.lookup("WHLO").orElseThrow();
        assertEquals(ValueShapeValidator.M3_SITE_CODE, whlo.valueShapeKey());
        assertNull(whlo.maxLength());
        assertNull(whlo.characterSet());
    }

    @Test
    void lookup_unknownCode_returnsEmpty() {
        assertTrue(catalog.lookup("XYZ1").isEmpty());
        assertTrue(catalog.lookup(null).isEmpty());
        assertTrue(catalog.lookup("  ").isEmpty());
    }

    @Test
    void all_containsPriorityCodes() {
        assertTrue(catalog.all().stream().anyMatch(c -> "ORNO".equals(c.code())));
        assertTrue(catalog.all().stream().anyMatch(c -> "WHLO".equals(c.code())));
    }
}
