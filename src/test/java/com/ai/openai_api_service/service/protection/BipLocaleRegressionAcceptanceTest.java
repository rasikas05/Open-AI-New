package com.ai.openai_api_service.service.protection;

import com.ai.openai_api_service.service.i18n.ResponseLanguageSwitch;
import com.ai.openai_api_service.service.i18n.SupportedResponseLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance matrix for BIP allowlist + language-switch persistence inputs (P2).
 * Chat-path locale and RAG routing assertions live in {@code ComprehendChatServiceTest}.
 */
class BipLocaleRegressionAcceptanceTest {

    private BusinessInformationProtectionService protection;
    private final SupportedResponseLanguages supported = new SupportedResponseLanguages("en,hi,mr,de,es,fr");

    @BeforeEach
    void setUp() {
        FieldClassificationCatalog catalog = new FieldClassificationCatalog();
        protection = new BusinessInformationProtectionService(
                new BusinessInformationDetector(catalog, new ValueShapeValidator()),
                catalog,
                new LLMPolicyApplier(new PlaceholderFormatter()),
                true
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "How do I create a new item in M3?",
            "¿Cómo creo un artículo nuevo en M3?",
            "Comment créer un nouvel article dans M3 ?",
            "How to create a new item in M3?",
            "create a new item in m3",
            "Open item MMS001",
            "Register customer CRS610"
    })
    void itemCreateAndProgramPhrases_preserveProductAndProgramIds(String input) {
        String protectedText = protection.protectText(input, ProtectionPurpose.ANSWER);
        assertFalse(protectedText.contains("<PRODUCT_NUMBER>"), () -> "masked product in: " + protectedText);
        assertFalse(protectedText.contains("<CUSTOMER_NUMBER>"), () -> "masked customer in: " + protectedText);
        assertTrue(protectedText.contains("M3") || protectedText.contains("m3")
                        || protectedText.contains("MMS001") || protectedText.contains("CRS610"),
                () -> "lost identifier in: " + protectedText);
    }

    @Test
    void productP1001_stillMasked() {
        assertEquals(
                "product <PRODUCT_NUMBER>",
                protection.protectText("product P1001", ProtectionPurpose.ANSWER)
        );
    }

    @Test
    void customerY00111_stillMasked() {
        assertEquals(
                "customer <CUSTOMER_NUMBER>",
                protection.protectText("customer Y00111", ProtectionPurpose.ANSWER)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "Please answer in English, en_US",
            "reply in French, fr_FR",
            "Speak Spanish, es_ES",
            "answer in Marathi, mr_IN"
    })
    void explicitLanguageSwitch_resolvesSupportedLocale(String message, String locale) {
        assertEquals(locale, ResponseLanguageSwitch.resolvePreferredLocale(message, supported).orElseThrow());
        assertTrue(ResponseLanguageSwitch.isSwitchOnly(message));
    }
}
