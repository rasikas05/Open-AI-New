package com.ai.openai_api_service.service.i18n;

/**
 * Detection outcome for one chat message. The raw message is never stored here.
 *
 * @param detectedLanguage Lingua's winning language, or {@code none} when there is no winner
 * @param detectedLocale locale of the winning language, or {@code none} when there is no winner
 * @param runnerUpLanguage second language, or {@code none} when Lingua returned only one language
 * @param winnerConfidence relative score of the winning language. Lingua always reports this as 1.0.
 * @param runnerUpConfidence relative score of the second language, compared with the minimum-confidence setting.
 *        Null when there is no runner-up.
 */
public record LanguageDetectionResult(
        String detectedLanguage,
        String detectedLocale,
        String runnerUpLanguage,
        String selectedLanguage,
        String selectedLocale,
        double winnerConfidence,
        Double runnerUpConfidence,
        boolean fallback
) {
    public static LanguageDetectionResult fallback(double winnerConfidence) {
        return new LanguageDetectionResult(
                LanguageDetectionService.NO_LANGUAGE,
                LanguageDetectionService.NO_LANGUAGE,
                LanguageDetectionService.NO_LANGUAGE,
                LanguageDetectionService.FALLBACK_LANGUAGE,
                LanguageDetectionService.FALLBACK_LOCALE,
                winnerConfidence,
                null,
                true
        );
    }
}
