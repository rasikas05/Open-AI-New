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

        String input = "How can I change payment terms for supplier ABC001?";
        ProtectionSession session = ProtectionSession.fromPiiSanitized(input);
        service.protect(session, ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true));

        String openaiBound = session.textForLlm();
        assertEquals("How can I change payment terms for supplier <SUPPLIER_NUMBER>?", openaiBound);
        assertTrue(openaiBound.contains("<SUPPLIER_NUMBER>"));
        assertEquals("ABC001", session.replacementMap().get("<SUPPLIER_NUMBER>"));
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
