package com.ai.openai_api_service.service.i18n;

/**
 * Accepted Roman-script classification. This is not a Lingua score.
 */
public record AcceptedRomanScript(String languageCode, String locale) {

    public static AcceptedRomanScript hinglish() {
        return new AcceptedRomanScript("hi", "hi_IN");
    }

    public static AcceptedRomanScript marathi() {
        return new AcceptedRomanScript("mr", "mr_IN");
    }

    /**
     * Source-language carrier for locale/trace. Winner confidence is unused.
     * Romanized turns use {@link TranslationService#normalizeRomanized}, not Amazon Translate.
     */
    public LanguageDetectionResult detectionResult() {
        return new LanguageDetectionResult(
                languageCode,
                locale,
                LanguageDetectionService.NO_LANGUAGE,
                languageCode,
                locale,
                0.0,
                null,
                false
        );
    }
}
