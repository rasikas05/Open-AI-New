package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionSessionPhase7bTest {

    @Test
    void textForLlm_prefersPiiThenBusinessThenOriginal() {
        ProtectionSession session = ProtectionSession.fromOriginal("customer 45678", true);
        assertEquals("customer 45678", session.textForLlm());

        session.applyBusinessResult(new ProtectedText(
                "customer <CUSTOMER_NUMBER>",
                List.of(),
                Map.of("<CUSTOMER_NUMBER>", "45678")
        ));
        assertEquals("customer <CUSTOMER_NUMBER>", session.textForLlm());

        session.applyPiiSanitizedText("customer <CUSTOMER_NUMBER> contact [PERSON]");
        assertEquals("customer <CUSTOMER_NUMBER> contact [PERSON]", session.textForLlm());
    }

    @Test
    void businessBeforePii_numericIdBecomesPlaceholder() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService bip = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );
        ProtectionSession session = ProtectionSession.fromOriginal(
                "How can I register customer 45678 in Infor M3?",
                true
        );
        bip.protect(session, ProtectionContext.forPurpose(ProtectionPurpose.ANSWER, true));
        assertTrue(session.businessProtectionApplied());
        assertTrue(session.businessProtectedText().contains("<CUSTOMER_NUMBER>"));
        assertFalse(session.businessProtectedText().contains("45678"));
    }

    @Test
    void restorer_restoresPlaceholderInReply() {
        ProtectionSession session = ProtectionSession.fromOriginal("customer 45678", true);
        session.applyBusinessResult(new ProtectedText(
                "customer <CUSTOMER_NUMBER>",
                List.of(),
                Map.of("<CUSTOMER_NUMBER>", "45678")
        ));
        BusinessPlaceholderRestorer restorer = new BusinessPlaceholderRestorer();
        String restored = restorer.restoreIntoSession(
                "Use CRS610 for customer <CUSTOMER_NUMBER>.",
                session
        );
        assertEquals("Use CRS610 for customer 45678.", restored);
        assertEquals("Use CRS610 for customer <CUSTOMER_NUMBER>.", session.replyBeforeRestore());
        assertEquals("Use CRS610 for customer 45678.", session.finalResponse());
    }
}
