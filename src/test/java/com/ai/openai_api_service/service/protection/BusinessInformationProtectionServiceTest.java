package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessInformationProtectionServiceTest {

    @Test
    void protect_whenDisabled_returnsOriginal() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                false
        );

        ProtectedText result = service.protect(
                "Show customer 1001",
                ProtectionContext.forPurpose(ProtectionPurpose.ANSWER)
        );

        assertEquals("Show customer 1001", result.text());
        assertTrue(result.actions().isEmpty());
    }

    @Test
    void protect_answerPipeline_replacesCustomerAllowsWarehouse() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );

        ProtectedText answer = service.protect(
                "Show customer 1001 at warehouse A01",
                ProtectionContext.forPurpose(ProtectionPurpose.ANSWER)
        );
        assertEquals("Show customer <CUSTOMER_NUMBER> at warehouse A01", answer.text());
        assertEquals("1001", answer.replacementMap().get("<CUSTOMER_NUMBER>"));

        ProtectedText rewrite = service.protect(
                "Show customer 1001 at warehouse A01",
                ProtectionContext.forPurpose(ProtectionPurpose.REWRITE)
        );
        assertEquals("Show customer 1001 at warehouse A01", rewrite.text());
        assertTrue(rewrite.replacementMap().isEmpty());
    }

    /**
     * Phase 7A closeout: ANSWER REPLACE for alphanumeric supplier ID (typical ID that survives Presidio).
     * Simulates the OpenAI-bound text after protectForLlm(ANSWER).
     */
    @Test
    void protect_answer_replacesSupplierAlphanumeric_openaiBoundText() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );

        String input = "How can I change payment terms for supplier SUP99?";
        ProtectionSession session = ProtectionSession.fromPiiSanitized(input);
        service.protect(session, ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true));

        String openaiBound = session.textForLlm();
        assertEquals("How can I change payment terms for supplier <SUPPLIER_NUMBER>?", openaiBound);
        assertTrue(openaiBound.contains("<SUPPLIER_NUMBER>"));
        assertEquals("SUP99", session.replacementMap().get("<SUPPLIER_NUMBER>"));
    }

    @Test
    void protect_preservesM3AndProgramIds_masksRealProductAndCustomer() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );

        assertEquals(
                "How to create a new item in M3?",
                service.protectText("How to create a new item in M3?", ProtectionPurpose.ANSWER)
        );
        assertEquals(
                "create a new item in m3",
                service.protectText("create a new item in m3", ProtectionPurpose.ANSWER)
        );
        assertEquals(
                "How can I register a customer in Infor M3?",
                service.protectText("How can I register a customer in Infor M3?", ProtectionPurpose.ANSWER)
        );
        assertEquals(
                "Open item MMS001",
                service.protectText("Open item MMS001", ProtectionPurpose.ANSWER)
        );
        assertEquals(
                "Register customer CRS610",
                service.protectText("Register customer CRS610", ProtectionPurpose.ANSWER)
        );
        assertEquals(
                "product <PRODUCT_NUMBER>",
                service.protectText("product P1001", ProtectionPurpose.ANSWER)
        );
        assertEquals(
                "customer <CUSTOMER_NUMBER>",
                service.protectText("customer Y00111", ProtectionPurpose.ANSWER)
        );
    }

    /**
     * Phase 7A closeout: when PII already masked the ID, business protect is a no-op (Decision #20).
     */
    @Test
    void protect_answer_piiMaskedNumber_noBusinessPlaceholder() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );

        String piiMasked = "How can I cancel customer order [NUMBER]?";
        ProtectedText result = service.protect(
                piiMasked,
                ProtectionContext.forPurpose(ProtectionPurpose.ANSWER)
        );
        assertEquals(piiMasked, result.text());
        assertTrue(result.actions().isEmpty());
    }
}
