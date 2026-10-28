package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessIdentifierAllowlistTest {

    private BusinessInformationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BusinessInformationDetector(new FieldClassificationCatalog(), new ValueShapeValidator());
    }

    @ParameterizedTest
    @ValueSource(strings = {"M3", "m3", "Infor", "INFOR", "CloudSuite", "cloudsuite", "Infor M3", "infor  m3"})
    void shouldSkipMasking_productTokens(String value) {
        assertTrue(BusinessIdentifierAllowlist.shouldSkipMasking(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MMS001", "CRS610", "OIS100", "mms001"})
    void shouldSkipMasking_programIds(String value) {
        assertTrue(BusinessIdentifierAllowlist.shouldSkipMasking(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"P1001", "Y00111", "SUP99", "1001", "SKU01"})
    void shouldSkipMasking_falseForBusinessIds(String value) {
        assertFalse(BusinessIdentifierAllowlist.shouldSkipMasking(value));
    }

    @Test
    void detect_itemInM3_recordsAllowlistSkipAndNoSpan() {
        BusinessInformationDetector.DetectionResult result =
                detector.detectWithStats("How to create a new item in M3?");
        assertTrue(result.spans().isEmpty());
        assertTrue(result.stats().rejectedByAllowlist() >= 1);
        assertTrue(result.stats().misses().stream()
                .anyMatch(m -> m.reason() == DetectionMissReason.ALLOWLIST_SKIP));
    }

    @Test
    void detect_itemMms001_and_customerCrs610_skipped() {
        assertTrue(detector.detect("Open item MMS001").isEmpty());
        assertTrue(detector.detect("Register customer CRS610").isEmpty());
    }

    @Test
    void detect_productP1001_and_customerY00111_stillDetected() {
        assertEquals(1, detector.detect("product P1001").size());
        assertEquals("PRNO", detector.detect("product P1001").get(0).code());
        assertEquals(1, detector.detect("customer Y00111").size());
        assertEquals("CUNO", detector.detect("customer Y00111").get(0).code());
    }
}
