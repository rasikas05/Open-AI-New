package com.ai.openai_api_service.service.i18n;

import com.github.pemistahl.lingua.api.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageDetectionServiceTest {

    private static LanguageDetectionService service;

    @BeforeAll
    static void setUp() {
        service = new LanguageDetectionService(0.80);
    }

    @Test
    void configuredMinimumConfidenceStaysAtPointEight() {
        assertEquals(0.80, service.minimumConfidence());
    }

    @Test
    void englishSentenceMapsToEnUs() {
        assertClearSelection(
                "The customer order cannot be released until the warehouse has allocated every item and the salesperson has confirmed the delivery date with the buyer.",
                "en",
                "en_US"
        );
        assertClearSelection("How do I check customer details?", "en", "en_US");
    }

    @Test
    void germanSentenceMapsToDeDe() {
        assertClearSelection(
                "Bitte zeigen Sie mir, wie ich einen Kundenauftrag im Verkaufsprozess erstelle.",
                "de",
                "de_DE"
        );
        assertClearSelection("Wie kann ich die Kundendaten überprüfen?", "de", "de_DE");
    }

    @Test
    void frenchSentenceDetectsFrench() {
        assertClearSelection(
                "Bonjour, je voudrais savoir comment vérifier les informations du client dans le processus de vente avant de confirmer la commande et l'adresse de livraison.",
                "fr",
                "fr_FR"
        );
        assertClearSelection("Comment puis-je vérifier les détails du client ?", "fr", "fr_FR");
    }

    @Test
    void spanishSentenceDetectsSpanish() {
        assertClearSelection(
                "Buenos días, quisiera saber cómo consultar los datos del cliente en el proceso de ventas antes de confirmar el pedido y la dirección de entrega.",
                "es",
                "es_ES"
        );
        assertClearSelection("¿Cómo puedo consultar los datos del cliente?", "es", "es_ES");
    }

    @Test
    void hindiSentenceDetectsHindi() {
        assertClearSelection(
                "मुझे यह बताइए कि मैं ग्राहक की पूरी जानकारी कैसे देख सकता हूँ और बिक्री प्रक्रिया में ऑर्डर की पुष्टि करने से पहले डिलीवरी का पता कैसे जाँच सकता हूँ।",
                "hi",
                "hi_IN"
        );
        assertClearSelection("मुझे ग्राहक की जानकारी कैसे देखनी है?", "hi", "hi_IN");
    }

    @Test
    void marathiSentenceDetectsMarathi() {
        assertClearSelection(
                "कृपया मला कळवा की मी ग्राहकाची संपूर्ण माहिती कशी तपासू शकतो आणि विक्रीच्या प्रक्रियेत ऑर्डर निश्चित करण्यापूर्वी पत्ता कसा दुरुस्त करायचा.",
                "mr",
                "mr_IN"
        );
        assertClearSelection("मला ग्राहकाची माहिती कशी तपासायची आहे?", "mr", "mr_IN");
    }

    @Test
    void blankShortNumericAndIdentifierInputsFallBack() {
        assertNoDetection(service.detect(""));
        assertNoDetection(service.detect("   "));
        assertNoDetection(service.detect("ja"));
        assertNoDetection(service.detect("1001"));
        assertNoDetection(service.detect("OIS100"));
        assertNoDetection(service.detect("CUNO"));
        assertNoDetection(service.detect("OIS100 CUNO"));
    }

    @Test
    void singleScriptHighRunnerUpSelectsDetectedLanguage() {
        LanguageDetectionResult french = service.decide("fr", "fr_FR", "es", 1.0, 0.80, false);
        assertEquals("fr", french.detectedLanguage());
        assertEquals("fr_FR", french.detectedLocale());
        assertEquals("es", french.runnerUpLanguage());
        assertEquals("fr", french.selectedLanguage());
        assertEquals("fr_FR", french.selectedLocale());
        assertEquals(0.80, french.runnerUpConfidence());
        assertFalse(french.fallback());

        LanguageDetectionResult german = service.decide("de", "de_DE", "nl", 1.0, 0.90, false);
        assertEquals("de", german.detectedLanguage());
        assertEquals("de", german.selectedLanguage());
        assertEquals("de_DE", german.selectedLocale());
        assertFalse(german.fallback());
    }

    @Test
    void mixedScriptKeepsDetectedLanguageAndSelectsEnglish() {
        LanguageDetectionResult result = service.decide("fr", "fr_FR", "es", 1.0, 0.80, true);
        assertEquals("fr", result.detectedLanguage());
        assertEquals("fr_FR", result.detectedLocale());
        assertEquals("es", result.runnerUpLanguage());
        assertEquals("en", result.selectedLanguage());
        assertEquals("en_US", result.selectedLocale());
        assertEquals(1.0, result.winnerConfidence());
        assertEquals(0.80, result.runnerUpConfidence());
        assertTrue(result.fallback());

        LanguageDetectionResult lowRunnerUp = service.decide("hi", "hi_IN", "mr", 1.0, 0.20, true);
        assertEquals("hi", lowRunnerUp.detectedLanguage());
        assertEquals("hi_IN", lowRunnerUp.detectedLocale());
        assertEquals("mr", lowRunnerUp.runnerUpLanguage());
        assertEquals("en_US", lowRunnerUp.selectedLocale());
        assertTrue(lowRunnerUp.fallback());
    }

    @Test
    void runnerUpBelowThresholdKeepsDetectedLocale() {
        LanguageDetectionResult result = service.decide("de", "de_DE", "nl", 1.0, 0.79, false);
        assertEquals("de", result.detectedLanguage());
        assertEquals("de", result.selectedLanguage());
        assertEquals("de_DE", result.detectedLocale());
        assertEquals("de_DE", result.selectedLocale());
        assertEquals("nl", result.runnerUpLanguage());
        assertEquals(1.0, result.winnerConfidence());
        assertEquals(0.79, result.runnerUpConfidence());
        assertFalse(result.fallback());
    }

    @Test
    void singleLanguageResultHasNoRunnerUp() {
        LanguageDetectionResult result = service.decide("en", "en_US", "de", 1.0, null, false);
        assertEquals("en", result.detectedLanguage());
        assertEquals("en", result.selectedLanguage());
        assertEquals("en_US", result.selectedLocale());
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.runnerUpLanguage());
        assertNull(result.runnerUpConfidence());
        assertFalse(result.fallback());
    }

    @Test
    void unknownWinnerSelectsEnglishWithoutADetectedLanguage() {
        LanguageDetectionResult result = service.decide(null, null, null, 0.0, null, false);
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLanguage());
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLocale());
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.runnerUpLanguage());
        assertNull(result.runnerUpConfidence());
        assertEquals("en", result.selectedLanguage());
        assertEquals("en_US", result.selectedLocale());
        assertTrue(result.fallback());
    }

    @Test
    void letterScriptsIgnorePunctuationDigitsAndEmoji() {
        assertFalse(LanguageDetectionService.containsMultipleScripts("Comment puis-je vérifier les détails du client ?"));
        assertFalse(LanguageDetectionService.containsMultipleScripts("Explain OIS100 and CUNO"));
        assertFalse(LanguageDetectionService.containsMultipleScripts("Hello, world! 1001 😀"));
        assertTrue(LanguageDetectionService.containsMultipleScripts("मला customer details पाहिजेत"));
        assertTrue(LanguageDetectionService.containsMultipleScripts("मुझे ग्राहक की जानकारी कैसे देखनी है OIS100"));
    }

    @Test
    void mixedMarathiAndEnglishKeepsLinguaWinnerAndSelectsEnglish() {
        LanguageDetectionResult result = service.detect("मला customer details पाहिजेत");
        assertNotEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLanguage(), result::toString);
        assertNotEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLocale(), result::toString);
        assertEquals("en", result.selectedLanguage(), result::toString);
        assertEquals("en_US", result.selectedLocale(), result::toString);
        assertTrue(result.fallback(), result::toString);
    }

    @Test
    void englishWithM3IdentifiersSelectsEnglishWithoutFallback() {
        LanguageDetectionResult result = service.detect("Explain OIS100 and CUNO");
        assertEquals("en", result.detectedLanguage(), result::toString);
        assertEquals("en_US", result.detectedLocale(), result::toString);
        assertNotEquals(LanguageDetectionService.NO_LANGUAGE, result.runnerUpLanguage(), result::toString);
        assertEquals("en", result.selectedLanguage(), result::toString);
        assertEquals("en_US", result.selectedLocale(), result::toString);
        assertFalse(result.fallback(), result::toString);
    }

    @Test
    void hindiSentenceWithIdentifierKeepsDetectedLanguageAndSelectsEnglish() {
        LanguageDetectionResult result = service.detect("मुझे ग्राहक की जानकारी कैसे देखनी है OIS100");
        assertEquals("hi", result.detectedLanguage(), result::toString);
        assertEquals("hi_IN", result.detectedLocale(), result::toString);
        assertEquals("en", result.selectedLanguage(), result::toString);
        assertEquals("en_US", result.selectedLocale(), result::toString);
        assertTrue(result.fallback(), result::toString);
    }

    @Test
    void spokenLanguagesMapToALocaleAndUnknownStaysNone() {
        assertEquals(LanguageDetectionService.NO_LANGUAGE, service.languageCode(Language.UNKNOWN));
        assertEquals("en_US", service.localeFor(Language.UNKNOWN));
        for (Language language : Language.values()) {
            if (language == Language.UNKNOWN || language == Language.LATIN) {
                continue;
            }
            String code = service.languageCode(language);
            String locale = service.localeFor(language);
            assertTrue(code.matches("[a-z]{2}"), language.name());
            assertTrue(locale.matches("[a-z]{2}_[A-Z]{2}"), language.name() + " " + locale);
            if (language == Language.ESPERANTO) {
                assertEquals("eo", code);
                assertEquals("en_US", locale);
            } else if (language != Language.ENGLISH) {
                assertNotEquals("en_US", locale, language.name());
            }
        }
    }

    @Test
    void explicitLocalesCoverTheLanguagesAlreadyInScope() {
        assertEquals("en", service.languageCode(Language.ENGLISH));
        assertEquals("en_US", service.localeFor(Language.ENGLISH));
        assertEquals("de_DE", service.localeFor(Language.GERMAN));
        assertEquals("fr_FR", service.localeFor(Language.FRENCH));
        assertEquals("es_ES", service.localeFor(Language.SPANISH));
        assertEquals("hi_IN", service.localeFor(Language.HINDI));
        assertEquals("mr_IN", service.localeFor(Language.MARATHI));
        assertEquals("pt_PT", service.localeFor(Language.PORTUGUESE));
        assertEquals("zh_CN", service.localeFor(Language.CHINESE));
    }

    @Test
    void configuredDetectionLanguagesAreTheSixSupportedLanguages() {
        SupportedResponseLanguages supported = new SupportedResponseLanguages("");
        assertEquals(Set.of("en", "hi", "mr", "de", "es", "fr"), service.configuredDetectionLanguages());
        for (String code : service.configuredDetectionLanguages()) {
            assertTrue(supported.supportsLanguage(code), code);
        }
    }

    @Test
    void detectionLanguageOutsideTheResponsePipelineFailsStartup() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new LanguageDetectionService(0.80, "en,zu", new SupportedResponseLanguages("")));
        assertTrue(ex.getMessage().contains("zu"));
    }

    @Test
    void detectionLanguageExcludedByResponseConfigurationFailsStartup() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new LanguageDetectionService(
                        0.80,
                        "en,hi,mr,de,es,fr",
                        new SupportedResponseLanguages("en,de")
                ));
        assertTrue(ex.getMessage().contains("hi"));
    }

    @Test
    void latinTextOutsideTheSixIsUncertain() {
        assertUncertain(service.detect(
                "Buongiorno, vorrei sapere come consultare i dati del cliente nel processo di vendita prima di confermare l'ordine e l'indirizzo di consegna."
        ));
        assertUncertain(service.detect("Come posso visualizzare i dettagli del cliente Y00111?"));
    }

    @Test
    void romanizedMarathiAndHindiAreUncertain() {
        assertUncertain(service.detect("Hi tu mala kay madat karu shakatos"));
        assertUncertain(service.detect("tu kay bolotys samjat nahi"));
        assertUncertain(service.detect("Tum muze kis prakar madat kr sakte ho"));
        assertUncertain(service.detect("mala customer order details sang"));
        assertUncertain(service.detect("mujhe customer details batao"));
    }

    @Test
    void sharedTuIsNotEvidenceForFrenchOrSpanish() {
        assertUncertain(service.detect("tu tu tu tu"));
        assertFalse(LanguageDetectionService.hasSoleEvidence("tu tu tu tu", "fr"));
        assertFalse(LanguageDetectionService.hasSoleEvidence("tu tu tu tu", "es"));
        assertTrue(LanguageDetectionService.hasSoleEvidence("Show address of customer Y00111", "en"));
        assertFalse(LanguageDetectionService.hasSoleEvidence("mala customer order details sang", "en"));
    }

    @Test
    void winnerScoreDoesNotMakeLatinEvidenceCertain() {
        LanguageDetectionResult decided = service.decide("es", "es_ES", "fr", 1.0, 0.2, false);
        assertEquals(1.0, decided.winnerConfidence());
        assertFalse(LanguageDetectionService.hasSoleEvidence("Hi tu mala kay madat karu shakatos", "es"));
    }

    @Test
    void scriptOutsideTheSixKeepsTheUnknownLanguageFallback() {
        LanguageDetectionResult result = service.detect(
                "你好，我想了解如何查看客户资料并确认订单的交货地址和销售流程。"
        );
        assertNoDetection(result);
    }

    @Test
    void languageWithoutARegionKeepsItsCodeAndUsesEnUsLocale() {
        assertEquals("eo", service.languageCode(Language.ESPERANTO));
        assertEquals("en_US", service.localeFor(Language.ESPERANTO));
    }

    private static void assertClearSelection(String text, String language, String locale) {
        LanguageDetectionResult result = service.detect(text);
        assertEquals(language, result.detectedLanguage(), result::toString);
        assertEquals(locale, result.detectedLocale(), result::toString);
        assertEquals(language, result.selectedLanguage(), result::toString);
        assertEquals(locale, result.selectedLocale(), result::toString);
        assertFalse(result.fallback(), result::toString);
    }

    private static void assertUncertain(LanguageDetectionResult result) {
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLanguage(), result::toString);
        assertTrue(result.fallback(), result::toString);
        assertEquals("en", result.selectedLanguage());
    }

    private static void assertNoDetection(LanguageDetectionResult result) {
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLanguage());
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.detectedLocale());
        assertEquals(LanguageDetectionService.NO_LANGUAGE, result.runnerUpLanguage());
        assertNull(result.runnerUpConfidence());
        assertEquals("en", result.selectedLanguage());
        assertEquals("en_US", result.selectedLocale());
        assertTrue(result.fallback());
    }
}
