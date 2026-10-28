package com.ai.openai_api_service.service.i18n;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Response languages that Lingua can detect and Amazon Translate can translate into English.
 * English is always included. {@code chat.response-languages} may narrow the other codes.
 * This does not call Translate {@code ListLanguages}; unit tests have no AWS credentials.
 */
@Component
public class SupportedResponseLanguages {

    /**
     * Amazon Translate source-language codes. Regional variants such as {@code zh-TW} are omitted
     * because Lingua reports the ISO 639-1 code.
     */
    private static final Set<String> TRANSLATE_SOURCE_CODES = Set.of(
            "af", "am", "ar", "az", "bg", "bn", "bs", "ca", "cs", "cy", "da", "de", "el", "en",
            "es", "et", "fa", "fi", "fr", "ga", "gu", "ha", "he", "hi", "hr", "ht", "hu", "hy",
            "id", "is", "it", "ja", "ka", "kk", "kn", "ko", "lt", "lv", "mk", "ml", "mn", "mr",
            "ms", "mt", "nl", "no", "pa", "pl", "ps", "pt", "ro", "ru", "si", "sk", "sl", "so",
            "sq", "sr", "sv", "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "uz", "vi", "zh"
    );

    private final Map<String, String> languageToLocale;
    private final Set<String> locales;

    public SupportedResponseLanguages(@Value("${chat.response-languages:}") String configured) {
        Map<String, String> intersection = intersection();
        if (!intersection.containsKey("en")) {
            throw new IllegalStateException("English must be a supported response language");
        }
        this.languageToLocale = select(intersection, configured);
        this.locales = Set.copyOf(languageToLocale.values());
    }

    public boolean supportsLanguage(String languageCode) {
        return languageCode != null && languageToLocale.containsKey(languageCode);
    }

    public boolean isResponseLocale(String locale) {
        return locale != null && locales.contains(locale);
    }

    /**
     * @return the locale for a supported language, or null when the code is outside the set
     */
    public String localeForLanguage(String languageCode) {
        if (languageCode == null) {
            return null;
        }
        return languageToLocale.get(languageCode);
    }

    private static Map<String, String> select(Map<String, String> intersection, String configured) {
        if (configured == null || configured.isBlank()) {
            return intersection;
        }
        Map<String, String> selected = new LinkedHashMap<>();
        boolean sawEnglish = false;
        for (String raw : configured.split(",")) {
            String code = raw.trim().toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            if ("en".equals(code)) {
                sawEnglish = true;
            }
            String locale = intersection.get(code);
            if (locale == null) {
                throw new IllegalStateException(
                        "chat.response-languages contains a code outside the supported intersection: " + code
                );
            }
            selected.putIfAbsent(code, locale);
        }
        if (!sawEnglish) {
            throw new IllegalStateException("chat.response-languages must include en");
        }
        return Map.copyOf(selected);
    }

    private static Map<String, String> intersection() {
        Map<String, String> locales = new LinkedHashMap<>();
        LanguageDetectionService.detectionLocales().forEach((code, locale) -> {
            if (TRANSLATE_SOURCE_CODES.contains(code)) {
                locales.put(code, locale);
            }
        });
        return Map.copyOf(locales);
    }
}
