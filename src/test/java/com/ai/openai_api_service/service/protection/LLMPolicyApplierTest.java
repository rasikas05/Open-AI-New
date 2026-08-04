package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMPolicyApplierTest {

    private LLMPolicyApplier applier;
    private FieldClassificationCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new FieldClassificationCatalog();
        applier = new LLMPolicyApplier(new PlaceholderFormatter());
    }

    @Test
    void answer_replacesBdiCustomer() {
        String text = "customer 1001";
        DetectedSpan span = new DetectedSpan(9, 13, "CUNO", 0.9, "customer");
        SpanClassification sc = new SpanClassification(span, catalog.lookup("CUNO"));

        ProtectedText result = applier.apply(text, List.of(sc), ProtectionContext.forPurpose(ProtectionPurpose.ANSWER));

        assertEquals("customer <CUSTOMER_NUMBER>", result.text());
        assertEquals(LlmExposurePolicy.REPLACE, result.actions().get(0).policyApplied());
    }

    @Test
    void rewrite_allowsBdiCustomer() {
        String text = "customer 1001";
        DetectedSpan span = new DetectedSpan(9, 13, "CUNO", 0.9, "customer");
        SpanClassification sc = new SpanClassification(span, catalog.lookup("CUNO"));

        ProtectedText result = applier.apply(text, List.of(sc), ProtectionContext.forPurpose(ProtectionPurpose.REWRITE));

        assertEquals("customer 1001", result.text());
        assertEquals(LlmExposurePolicy.ALLOW, result.actions().get(0).policyApplied());
    }

    @Test
    void answer_allowsOmdWarehouse() {
        String text = "warehouse A01";
        DetectedSpan span = new DetectedSpan(10, 13, "WHLO", 0.9, "warehouse");
        SpanClassification sc = new SpanClassification(span, catalog.lookup("WHLO"));

        ProtectedText result = applier.apply(text, List.of(sc), ProtectionContext.forPurpose(ProtectionPurpose.ANSWER));

        assertEquals("warehouse A01", result.text());
        assertEquals(LlmExposurePolicy.ALLOW, result.actions().get(0).policyApplied());
    }

    @Test
    void unclassified_blocks() {
        String text = "secret XYZ99";
        DetectedSpan span = new DetectedSpan(7, 12, "UNKNOWN", 0.5, "secret");
        SpanClassification sc = new SpanClassification(span, Optional.empty());

        ProtectedText result = applier.apply(text, List.of(sc), ProtectionContext.forPurpose(ProtectionPurpose.ANSWER));

        assertTrue(result.text().contains("<BLOCKED>"));
        assertFalse(result.text().contains("XYZ99"));
        assertEquals(LlmExposurePolicy.BLOCK, result.actions().get(0).policyApplied());
    }

    @Test
    void answer_blocksBfiCreditLimit() {
        String text = "credit limit 50000";
        DetectedSpan span = new DetectedSpan(13, 18, "CRLM", 0.9, "credit limit");
        SpanClassification sc = new SpanClassification(span, catalog.lookup("CRLM"));

        ProtectedText result = applier.apply(text, List.of(sc), ProtectionContext.forPurpose(ProtectionPurpose.ANSWER));

        assertEquals("credit limit <CREDIT_LIMIT>", result.text());
        assertEquals(LlmExposurePolicy.BLOCK, result.actions().get(0).policyApplied());
    }
}
