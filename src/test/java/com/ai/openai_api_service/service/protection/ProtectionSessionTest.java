package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionSessionTest {

    @Test
    void protect_populatesSessionAndReplacementMap() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );

        ProtectionSession session = ProtectionSession.fromOriginal("Show customer 1001", true);
        service.protect(session, ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true));

        assertEquals("Show customer <CUSTOMER_NUMBER>", session.textForLlm());
        assertEquals("Show customer <CUSTOMER_NUMBER>", session.businessProtectedText());
        assertEquals(1, session.replacementMap().size());
        assertEquals("1001", session.replacementMap().get("<CUSTOMER_NUMBER>"));
        assertTrue(session.actions().stream().anyMatch(a -> a.policyApplied() == LlmExposurePolicy.REPLACE));
    }

    @Test
    void protect_whenDisabled_leavesSessionWithoutBusinessResult() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                false
        );

        ProtectionSession session = ProtectionSession.fromOriginal("Show customer 1001", false);
        service.protect(session, ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, false));

        assertEquals("Show customer 1001", session.textForLlm());
        assertTrue(session.replacementMap().isEmpty());
    }
}
