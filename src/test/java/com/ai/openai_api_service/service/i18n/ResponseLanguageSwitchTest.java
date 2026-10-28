package com.ai.openai_api_service.service.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseLanguageSwitchTest {

    private final SupportedResponseLanguages supported = new SupportedResponseLanguages("en,hi,mr,de,es,fr");

    @ParameterizedTest
    @CsvSource({
            "Please answer in English, en_US",
            "reply in French, fr_FR",
            "Speak Spanish, es_ES",
            "change language to German, de_DE",
            "answer in Marathi, mr_IN",
            "respond in Hindi, hi_IN"
    })
    void resolvePreferredLocale_commonPhrases(String message, String expectedLocale) {
        assertEquals(Optional.of(expectedLocale), ResponseLanguageSwitch.resolvePreferredLocale(message, supported));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Please answer in English",
            "reply in French.",
            "Speak Spanish!"
    })
    void isSwitchOnly_trueForPureCommands(String message) {
        assertTrue(ResponseLanguageSwitch.isSwitchOnly(message));
    }

    @Test
    void isSwitchOnly_falseWhenExtraQuestionPresent() {
        assertFalse(ResponseLanguageSwitch.isSwitchOnly(
                "Please answer in French. How do I create an item in M3?"));
        assertTrue(ResponseLanguageSwitch.resolvePreferredLocale(
                "Please answer in French. How do I create an item in M3?", supported).isPresent());
    }

    @Test
    void resolvePreferredLocale_emptyForOrdinaryQuestions() {
        assertTrue(ResponseLanguageSwitch.resolvePreferredLocale(
                "How do I create a new item in M3?", supported).isEmpty());
    }
}
