package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessInformationDetectorTest {

    private BusinessInformationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BusinessInformationDetector(new FieldClassificationCatalog(), new ValueShapeValidator());
    }

    @Test
    void detect_customerNumber() {
        List<DetectedSpan> spans = detector.detect("Show status for customer 1001");
        assertEquals(1, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals("1001", extract(spans.get(0), "Show status for customer 1001"));
        assertEquals("customer", spans.get(0).matchedKeyword());
    }

    @Test
    void detect_orderNumber() {
        String text = "What is the status of order 0012345678?";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("ORNO", spans.get(0).code());
        assertEquals("0012345678", extract(spans.get(0), text));
    }

    @Test
    void detect_warehouse_omd() {
        String text = "Check stock at warehouse A01";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("WHLO", spans.get(0).code());
        assertEquals("A01", extract(spans.get(0), text));
    }

    @Test
    void detect_supplier() {
        String text = "Details for supplier SUP99";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("SUNO", spans.get(0).code());
        assertEquals("SUP99", extract(spans.get(0), text));
    }

    @Test
    void detect_connector_salespersonIs() {
        String text = "salesperson is MAHESHD";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("SMCD", spans.get(0).code());
        assertEquals("MAHESHD", extract(spans.get(0), text));
        assertEquals("salesperson", spans.get(0).matchedKeyword());
    }

    @Test
    void detect_connector_orderFor() {
        String text = "order for 76948";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("ORNO", spans.get(0).code());
        assertEquals("76948", extract(spans.get(0), text));
    }

    @Test
    void detect_separator_customerEquals() {
        String text = "customer=45678";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals("45678", extract(spans.get(0), text));
    }

    @Test
    void detect_salesRepresentative_preservesMatchedKeyword() {
        String text = "sales representative MAHESHD";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("SMCD", spans.get(0).code());
        assertEquals("sales representative", spans.get(0).matchedKeyword());
        assertEquals("MAHESHD", extract(spans.get(0), text));
    }

    @Test
    void detect_multiEntity_customerAndOrder() {
        String text = "Show customer 45678 order 1000234";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(2, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals("45678", extract(spans.get(0), text));
        assertEquals("ORNO", spans.get(1).code());
        assertEquals("1000234", extract(spans.get(1), text));
    }

    @Test
    void detect_ambiguous_customerOrder_isOrno() {
        String text = "customer order 100001";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("ORNO", spans.get(0).code());
        assertEquals("100001", extract(spans.get(0), text));
        assertEquals("customer order", spans.get(0).matchedKeyword());
    }

    @Test
    void detect_ambiguous_purchaseOrder_isPuno() {
        String text = "purchase order 450001";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("PUNO", spans.get(0).code());
        assertEquals("450001", extract(spans.get(0), text));
    }

    @Test
    void negative_customerService_noSpan() {
        assertTrue(detector.detect("customer service").isEmpty());
    }

    @Test
    void negative_warehouseManagement_noSpan() {
        assertTrue(detector.detect("warehouse management").isEmpty());
    }

    @Test
    void negative_customerSupplier_noCunoEqualsSupplier() {
        List<DetectedSpan> spans = detector.detect("customer supplier");
        assertTrue(spans.isEmpty(), "supplier must not be captured as CUNO value");
    }

    @Test
    void regression_customerHow_noSpan() {
        assertTrue(detector.detect("customer how").isEmpty());
    }

    @Test
    void regression_salespersonIs_notSmcdEqualsIs() {
        List<DetectedSpan> spans = detector.detect("salesperson is MAHESHD");
        assertEquals(1, spans.size());
        assertEquals("MAHESHD", extract(spans.get(0), "salesperson is MAHESHD"));
        assertTrue(spans.stream().noneMatch(s -> "is".equalsIgnoreCase(extract(s, "salesperson is MAHESHD"))));
    }

    @Test
    void regression_orderFor_notOrnoEqualsFor() {
        List<DetectedSpan> spans = detector.detect("order for 76948");
        assertEquals(1, spans.size());
        assertEquals("76948", extract(spans.get(0), "order for 76948"));
    }

    @Test
    void detectWithStats_exposesInternalCounters() {
        BusinessInformationDetector.DetectionResult result =
                detector.detectWithStats("Show customer 45678 order 100123");
        assertEquals(2, result.spans().size());
        DetectionStats stats = result.stats();
        assertTrue(stats.rulesEvaluated() > 0);
        assertTrue(stats.matchesFound() >= 2);
        assertEquals(2, stats.finalSpans());
        assertTrue(stats.toString().contains("Final spans: 2"));
    }

    @Test
    void detect_alias_custHash() {
        String text = "cust#45678";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals("45678", extract(spans.get(0), text));
        assertEquals("cust#", spans.get(0).matchedKeyword());
    }

    @Test
    void detect_latencySanity_typicalPrompt() {
        String text = "Please show customer 1001 order 0012345678 from warehouse A01 for supplier SUP99";
        long start = System.nanoTime();
        List<DetectedSpan> spans = detector.detect(text);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(spans.size() >= 3, "expected multiple entity detections, got "
                + spans.stream().map(DetectedSpan::code).collect(Collectors.joining(",")));
        assertTrue(elapsedMs < 50, "detector latency should be under 50ms for typical prompt, was " + elapsedMs);
    }

    @Test
    void detect_paren_customer() {
        String text = "customer (45678)";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals("45678", extract(spans.get(0), text));
        assertEquals(DetectionMatchBand.GRAMMAR, spans.get(0).matchBand());
    }

    @Test
    void detect_connector_withNumber() {
        String text = "customer with number 45678";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals(DetectionMatchBand.GRAMMAR, spans.get(0).matchBand());
    }

    @Test
    void detect_exact_vs_alias_bands() {
        assertEquals(DetectionMatchBand.EXACT, detector.detect("customer 45678").get(0).matchBand());
        assertEquals(DetectionMatchBand.ALIAS, detector.detect("cust 45678").get(0).matchBand());
        assertEquals(DetectionMatchBand.WEAK, detector.detect("customer reference 45678").get(0).matchBand());
    }

    @Test
    void detect_ambiguous_manufacturingAndDistribution() {
        assertEquals("MFNO", detector.detect("manufacturing order 900001").get(0).code());
        assertEquals("MFNO", detector.detect("manufacturing order MF123456").get(0).code());
        assertEquals("TRNR", detector.detect("distribution order 800001").get(0).code());
        assertEquals("TRNR", detector.detect("transfer order 800002").get(0).code());
    }

    @Test
    void detect_alphanumericOrderIdentifiers() {
        assertEquals("ORNO", detector.detect("order SO10001").get(0).code());
        assertEquals("PUNO", detector.detect("purchase order PO450001").get(0).code());
    }

    @Test
    void detect_multiEntity_customerAndPurchaseOrder() {
        String text = "How is customer 67890 linked with purchase order 450001?";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(2, spans.size());
        assertEquals("CUNO", spans.get(0).code());
        assertEquals("67890", extract(spans.get(0), text));
        assertEquals("PUNO", spans.get(1).code());
        assertEquals("450001", extract(spans.get(1), text));
    }

    @Test
    void punctuation_contract_passAndFail() {
        assertEquals(1, detector.detect("customer:45678").size());
        assertEquals(1, detector.detect("customer=45678").size());
        assertEquals(1, detector.detect("customer#45678").size());
        assertTrue(detector.detect("customer, 45678").isEmpty());
        assertTrue(detector.detect("customer;45678").isEmpty());
        assertTrue(detector.detect("customer-45678").isEmpty());
    }

    @Test
    void negative_shape_and_accountSettings() {
        assertTrue(detector.detect("customer hello").isEmpty());
        assertTrue(detector.detect("customer banana").isEmpty());
        assertTrue(detector.detect("customer ###").isEmpty());
        assertTrue(detector.detect("account settings").isEmpty());
        assertTrue(detector.detect("order management").isEmpty());
    }

    @Test
    void miss_classification_valueBeforeKeyword() {
        BusinessInformationDetector.DetectionResult result =
                detector.detectWithStats("45678 this customer");
        assertTrue(result.spans().isEmpty());
        assertTrue(result.stats().misses().stream()
                .anyMatch(m -> m.reason() == DetectionMissReason.VALUE_BEFORE_KEYWORD));
    }

    @Test
    void miss_classification_shapeInvalid() {
        BusinessInformationDetector.DetectionResult result =
                detector.detectWithStats("customer banana");
        assertTrue(result.spans().isEmpty());
        assertTrue(result.stats().misses().stream()
                .anyMatch(m -> m.reason() == DetectionMissReason.SHAPE_INVALID
                        || m.reason() == DetectionMissReason.RESERVED_VALUE
                        || m.reason() == DetectionMissReason.VALUE_MISSING
                        || m.reason() == DetectionMissReason.CONNECTOR_INVALID));
    }

    @Test
    void detect_alias_po() {
        String text = "po 450001";
        List<DetectedSpan> spans = detector.detect(text);
        assertEquals(1, spans.size());
        assertEquals("PUNO", spans.get(0).code());
        assertTrue(spans.get(0).aliasMatch());
    }

    private static String extract(DetectedSpan span, String text) {
        return text.substring(span.start(), span.end());
    }
}
