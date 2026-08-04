package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Flag-off path must leave text unchanged (OpenAIService identical behavior).
 */
class ProtectForLlmFlagOffTest {

    @Test
    void disabledService_passthrough() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        BusinessInformationProtectionService service = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                false
        );
        assertEquals("customer 1001", service.protectText("customer 1001", ProtectionPurpose.ANSWER));
        assertNull(service.protectText(null, ProtectionPurpose.ANSWER));
    }
}
