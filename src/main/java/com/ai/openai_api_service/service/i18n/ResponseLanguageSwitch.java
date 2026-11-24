package com.ai.openai_api_service.service.i18n;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects an explicit user request to change the response language.
 * Match against the <em>original</em> user message before translation or BIP.
 */
public final class ResponseLanguageSwitch {

    private static final Map<String, String> LANGUAGE_ALIASES = languageAliases();
    private static final Pattern SWITCH = buildSwitchPattern();

    private ResponseLanguageSwitch() {
    }

    /**
     * @return supported response locale when the message contains an explicit language-switch command
     */
    public static Optional<String> resolvePreferredLocale(String originalMessage, SupportedResponseLanguages supported) {
        if (originalMessage == null || originalMessage.isBlank() || supported == null) {
            return Optional.empty();
        }
        Matcher matcher = SWITCH.matcher(originalMessage.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String alias = matcher.group("lang");
        if (alias == null || alias.isBlank()) {
            return Optional.empty();
        }
        String code = LANGUAGE_ALIASES.get(alias.trim().toLowerCase(Locale.ROOT));
        if (code == null) {
            // Devanagari aliases are stored as-is (Locale.ROOT lowercasing does not change them)
            code = LANGUAGE_ALIASES.get(alias.trim());
        }
        if (code == null) {
            return Optional.empty();
        }
        String locale = supported.localeForLanguage(code);
        if (locale == null || !supported.isResponseLocale(locale)) {
            return Optional.empty();
        }
        return Optional.of(locale);
    }

    /**
     * True when the message is only a language-switch command (no other substantive text).
     */
    public static boolean isSwitchOnly(String originalMessage) {
        if (originalMessage == null || originalMessage.isBlank()) {
            return false;
        }
        String trimmed = originalMessage.trim();
        Matcher matcher = SWITCH.matcher(trimmed);
        if (!matcher.find()) {
            return false;
        }
        String remainder = (trimmed.substring(0, matcher.start()) + trimmed.substring(matcher.end()))
                .replaceAll("[\\p{Punct}\\s]+", "")
                .trim();
        return remainder.isEmpty();
    }

    static String languageCodeForAlias(String alias) {
        if (alias == null) {
            return null;
        }
        String lower = alias.trim().toLowerCase(Locale.ROOT);
        String code = LANGUAGE_ALIASES.get(lower);
        return code != null ? code : LANGUAGE_ALIASES.get(alias.trim());
    }

    private static Pattern buildSwitchPattern() {
        String langAlt = LANGUAGE_ALIASES.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElse("english");
        return Pattern.compile(
                "(?i)\\b(?:"
                        + "please\\s+)?"
                        + "(?:answer|reply|respond|speak|write|talk|change\\s+language(?:\\s+to)?|set\\s+language(?:\\s+to)?|"
                        + "switch\\s+(?:language\\s+)?(?:to)?|use)"
                        + "\\s+(?:in\\s+|to\\s+)?"
                        + "(?<lang>" + langAlt + ")"
                        + "\\b"
        );
    }

    private static Map<String, String> languageAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        put(aliases, "en", "english", "anglais", "inglés", "ingles", "englisch");
        put(aliases, "fr", "french", "français", "francais", "französisch", "franzosisch", "francés", "frances");
        put(aliases, "es", "spanish", "español", "espanol", "espagnol", "spanisch");
        put(aliases, "de", "german", "deutsch", "allemand", "alemán", "aleman");
        put(aliases, "hi", "hindi", "हिंदी", "हिन्दी");
        put(aliases, "mr", "marathi", "मराठी");
        return Map.copyOf(aliases);
    }

    private static void put(Map<String, String> aliases, String code, String... names) {
        for (String name : names) {
            aliases.put(name.toLowerCase(Locale.ROOT), code);
        }
    }
}
