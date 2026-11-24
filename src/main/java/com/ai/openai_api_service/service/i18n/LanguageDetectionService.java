package com.ai.openai_api_service.service.i18n;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the language of the raw user message and logs nothing itself.
 * Models stay lazy-loaded: {@code withPreloadedLanguageModels()} is not used.
 * The detector contains only the configured response languages, currently English, Hindi,
 * Marathi, German, Spanish, and French. Those models still stay in memory after first use.
 * <p>
 * A single-script winner is selected even when the runner-up is at or above
 * {@code minimumConfidence}. Mixed letter scripts are a Phase 1 limitation, not proof
 * that the message is ambiguous: a Hindi sentence with an English product name or
 * {@code OIS100} can still be Hindi, and this check selects {@code en_US} anyway.
 * It does not separate Hindi from Marathi. Response language is resolved separately.
 */
@Service
public class LanguageDetectionService {

    static final String FALLBACK_LANGUAGE = "en";
    static final String FALLBACK_LOCALE = "en_US";
    public static final String NO_LANGUAGE = "none";
    static final String DEFAULT_DETECTION_LANGUAGES = "en,hi,mr,de,es,fr";
    private static final int MINIMUM_TEXT_LENGTH = 3;
    private static final int MINIMUM_ENGLISH_EVIDENCE = 2;
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Set<String> ENGLISH_FUNCTION = Set.of(
            "the", "and", "how", "what", "where", "when", "why", "with", "from", "this",
            "that", "for", "you", "your", "can", "of", "please"
    );
    private static final Set<String> ENGLISH_EVIDENCE = Set.of(
            "the", "and", "how", "what", "where", "when", "why", "show", "display", "explain", "customer",
            "order", "address", "with", "from", "this", "that", "for", "you", "your", "can",
            "please", "help", "create", "supplier", "check", "details", "cannot", "until",
            "has", "have", "every", "item", "confirmed", "delivery", "date", "buyer",
            "warehouse", "allocated", "released", "salesperson", "of"
    );
    private static final Set<String> GERMAN_EVIDENCE = Set.of(
            "bitte", "ich", "und", "der", "das", "nicht", "wie", "ein", "eine", "einen",
            "ist", "sind", "mit", "für", "auf", "den", "dem", "des", "wir", "von",
            "zeige", "zeigen", "kann", "mir", "erstelle", "überprüfen", "kundendaten",
            "kundenauftrag"
    );
    private static final Set<String> SPANISH_EVIDENCE = Set.of(
            "cómo", "como", "puedo", "consultar", "los", "las", "datos", "quisiera",
            "días", "buenos", "pedido", "dirección", "está", "están", "por"
    );
    private static final Set<String> FRENCH_EVIDENCE = Set.of(
            "bonjour", "je", "voudrais", "savoir", "comment", "vérifier", "informations",
            "processus", "avant", "confirmer", "commande", "adresse", "livraison",
            "vous", "nous", "dans", "avec", "est", "pas", "pour", "les", "des", "du",
            "au", "aux", "puis"
    );
    private static final Pattern IDENTIFIER_TOKEN = Pattern.compile(
            "(?:[A-Z]{1,10}\\d{1,8}[A-Z]{0,4}|\\d+|[A-Z]{2,10})"
    );
    private static final Map<Language, String> LOCALES = localeMap();

    private final double minimumConfidence;
    private final LanguageDetector detector;
    private final Set<String> detectionLanguages;

    LanguageDetectionService(double minimumConfidence) {
        this(minimumConfidence, DEFAULT_DETECTION_LANGUAGES, new SupportedResponseLanguages(""));
    }

    @Autowired
    public LanguageDetectionService(
            @Value("${chat.language-detection.minimum-confidence:0.80}") double minimumConfidence,
            @Value("${chat.language-detection.languages:en,hi,mr,de,es,fr}") String detectionLanguages,
            SupportedResponseLanguages supportedResponseLanguages
    ) {
        if (minimumConfidence < 0.0 || minimumConfidence > 1.0) {
            throw new IllegalArgumentException(
                    "chat.language-detection.minimum-confidence must be between 0.0 and 1.0"
            );
        }
        List<Language> languages = resolveDetectionLanguages(detectionLanguages, supportedResponseLanguages);
        this.minimumConfidence = minimumConfidence;
        this.detectionLanguages = languageCodes(languages);
        this.detector = LanguageDetectorBuilder.fromLanguages(languages.toArray(Language[]::new)).build();
    }

    LanguageDetectionService(double minimumConfidence, LanguageDetector detector) {
        if (minimumConfidence < 0.0 || minimumConfidence > 1.0) {
            throw new IllegalArgumentException(
                    "chat.language-detection.minimum-confidence must be between 0.0 and 1.0"
            );
        }
        this.minimumConfidence = minimumConfidence;
        this.detector = detector;
        this.detectionLanguages = Set.of();
    }

    double minimumConfidence() {
        return minimumConfidence;
    }

    Set<String> configuredDetectionLanguages() {
        return detectionLanguages;
    }

    private static List<Language> resolveDetectionLanguages(
            String configured,
            SupportedResponseLanguages supportedResponseLanguages
    ) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("chat.language-detection.languages must include en");
        }
        if (supportedResponseLanguages == null) {
            throw new IllegalStateException("Supported response languages are required");
        }
        Map<String, Language> linguaByCode = linguaByCode();
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String raw : configured.split(",")) {
            String code = raw.trim().toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            if (!supportedResponseLanguages.supportsLanguage(code) || !linguaByCode.containsKey(code)) {
                throw new IllegalStateException(
                        "chat.language-detection.languages contains a code the response pipeline does not support: " + code
                );
            }
            codes.add(code);
        }
        if (!codes.contains("en")) {
            throw new IllegalStateException("chat.language-detection.languages must include en");
        }
        if (codes.size() < 2) {
            throw new IllegalStateException(
                    "chat.language-detection.languages must include en and at least one other supported language"
            );
        }
        List<Language> languages = new ArrayList<>();
        for (String code : codes) {
            languages.add(linguaByCode.get(code));
        }
        return List.copyOf(languages);
    }

    private static Map<String, Language> linguaByCode() {
        Map<String, Language> codes = new java.util.LinkedHashMap<>();
        localeMap().forEach((language, locale) ->
                codes.put(language.getIsoCode639_1().name().toLowerCase(Locale.ROOT), language));
        return codes;
    }

    private static Set<String> languageCodes(List<Language> languages) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Language language : languages) {
            codes.add(language.getIsoCode639_1().name().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(codes);
    }

    /**
     * Short, numeric, and identifier-only messages skip both detection and translation.
     */
    public boolean bypassesTranslation(String text) {
        return skipsTranslation(text);
    }

    /**
     * Same rule as {@link #bypassesTranslation(String)} without a detector instance.
     */
    public static boolean skipsTranslation(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return skipsDetection(text.trim());
    }

    /**
     * ISO 639-1 code to locale for every language Lingua is asked to detect.
     * Building this map does not load language models.
     */
    static Map<String, String> detectionLocales() {
        Map<String, String> locales = new java.util.LinkedHashMap<>();
        localeMap().forEach((language, locale) ->
                locales.put(language.getIsoCode639_1().name().toLowerCase(Locale.ROOT), locale));
        return Map.copyOf(locales);
    }

    public LanguageDetectionResult detect(String text) {
        if (text == null || text.isBlank() || skipsDetection(text.trim())) {
            return LanguageDetectionResult.fallback(0.0);
        }
        String trimmed = text.trim();
        boolean mixedScript = containsMultipleScripts(trimmed);
        Map<Language, Double> scores = detector.computeLanguageConfidenceValues(trimmed);
        if (scores == null || scores.isEmpty()) {
            return LanguageDetectionResult.fallback(0.0);
        }
        Iterator<Map.Entry<Language, Double>> scoresInOrder = scores.entrySet().iterator();
        Map.Entry<Language, Double> winner = scoresInOrder.next();
        if (winner.getKey() == null || winner.getKey() == Language.UNKNOWN) {
            return LanguageDetectionResult.fallback(winner.getValue() == null ? 0.0 : winner.getValue());
        }
        String runnerUpLanguage = NO_LANGUAGE;
        Double runnerUpScore = null;
        if (scoresInOrder.hasNext()) {
            Map.Entry<Language, Double> runnerUp = scoresInOrder.next();
            runnerUpScore = runnerUp.getValue();
            runnerUpLanguage = languageCode(runnerUp.getKey());
        }
        LanguageDetectionResult decided = decide(
                languageCode(winner.getKey()),
                localeFor(winner.getKey()),
                runnerUpLanguage,
                winner.getValue(),
                runnerUpScore,
                mixedScript
        );
        if (decided.fallback() || !isLatinWinner(decided.detectedLanguage())) {
            return decided;
        }
        if (!hasSoleEvidence(trimmed, decided.detectedLanguage())) {
            return uncertain(decided);
        }
        return decided;
    }

    /**
     * Meaningful text whose Latin winner is not supported by evidence that distinguishes
     * that language. Short and identifier inputs never reach this result.
     */
    public static LanguageDetectionResult uncertain(LanguageDetectionResult decided) {
        return new LanguageDetectionResult(
                NO_LANGUAGE,
                NO_LANGUAGE,
                decided == null ? NO_LANGUAGE : decided.runnerUpLanguage(),
                FALLBACK_LANGUAGE,
                FALLBACK_LOCALE,
                decided == null ? 0.0 : decided.winnerConfidence(),
                decided == null ? null : decided.runnerUpConfidence(),
                true
        );
    }

    public static boolean isUncertain(LanguageDetectionResult detected) {
        return detected != null
                && detected.fallback()
                && NO_LANGUAGE.equals(detected.detectedLanguage());
    }

    static boolean hasSoleEvidence(String text, String winner) {
        if (text == null || winner == null || !isLatinWinner(winner)) {
            return false;
        }
        Set<String> matched = languagesWithEvidence(text);
        return matched.size() == 1 && matched.contains(winner);
    }

    private static boolean isLatinWinner(String language) {
        return "en".equals(language) || "de".equals(language) || "es".equals(language) || "fr".equals(language);
    }

    private static Set<String> languagesWithEvidence(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> words = words(lower);
        Set<String> matched = new LinkedHashSet<>();
        int englishWords = count(words, ENGLISH_EVIDENCE);
        if (englishWords >= MINIMUM_ENGLISH_EVIDENCE && count(words, ENGLISH_FUNCTION) > 0) {
            matched.add("en");
        }
        if (count(words, GERMAN_EVIDENCE) > 0 || containsAny(lower, "ä", "ö", "ü", "ß")) {
            matched.add("de");
        }
        if (count(words, SPANISH_EVIDENCE) > 0 || containsAny(lower, "ñ", "¿", "¡", "á")) {
            matched.add("es");
        }
        if (count(words, FRENCH_EVIDENCE) > 0 || containsAny(lower, "à", "â", "ç", "è", "ê", "ë", "î", "ï", "ô", "œ", "ù", "û", "ÿ")) {
            matched.add("fr");
        }
        return matched;
    }

    private static Set<String> words(String lower) {
        Set<String> words = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(lower);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private static int count(Set<String> words, Set<String> evidence) {
        int found = 0;
        for (String word : words) {
            if (evidence.contains(word)) {
                found++;
            }
        }
        return found;
    }

    private static boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selects a known single-script winner. {@code minimumConfidence} stays configured and is
     * not a reason to discard that winner. Mixed letter scripts keep the detected winner and
     * select English. The winner score is not a threshold: Lingua always sets it to 1.0.
     */
    LanguageDetectionResult decide(
            String detectedLanguage,
            String detectedLocale,
            String runnerUpLanguage,
            double winnerScore,
            Double runnerUpScore,
            boolean mixedScript
    ) {
        String detectedCode = presentOrNone(detectedLanguage);
        String detectedRegion = presentOrNone(detectedLocale);
        boolean hasRunnerUp = runnerUpScore != null;
        String secondLanguage = hasRunnerUp ? presentOrNone(runnerUpLanguage) : NO_LANGUAGE;
        if (NO_LANGUAGE.equals(detectedCode)) {
            return new LanguageDetectionResult(
                    NO_LANGUAGE,
                    NO_LANGUAGE,
                    secondLanguage,
                    FALLBACK_LANGUAGE,
                    FALLBACK_LOCALE,
                    winnerScore,
                    runnerUpScore,
                    true
            );
        }
        if (mixedScript) {
            return new LanguageDetectionResult(
                    detectedCode,
                    detectedRegion,
                    secondLanguage,
                    FALLBACK_LANGUAGE,
                    FALLBACK_LOCALE,
                    winnerScore,
                    runnerUpScore,
                    true
            );
        }
        String selectedLocale = NO_LANGUAGE.equals(detectedRegion) ? FALLBACK_LOCALE : detectedRegion;
        return new LanguageDetectionResult(
                detectedCode,
                detectedRegion,
                secondLanguage,
                detectedCode,
                selectedLocale,
                winnerScore,
                runnerUpScore,
                false
        );
    }

    private static String presentOrNone(String value) {
        if (value == null || value.isBlank() || NO_LANGUAGE.equals(value)) {
            return NO_LANGUAGE;
        }
        return value;
    }

    String languageCode(Language language) {
        if (language == null || language == Language.UNKNOWN) {
            return NO_LANGUAGE;
        }
        return language.getIsoCode639_1().name().toLowerCase(Locale.ROOT);
    }

    String localeFor(Language language) {
        if (language == null || language == Language.UNKNOWN) {
            return FALLBACK_LOCALE;
        }
        return LOCALES.getOrDefault(language, FALLBACK_LOCALE);
    }

    /**
     * Counts Unicode scripts of letters only. {@code COMMON} and {@code INHERITED} are ignored,
     * so punctuation, spaces, digits, and emoji do not add a script.
     * More than one script is a conservative Phase 1 signal: a Hindi sentence that also contains
     * an English product name or {@code OIS100} can still be Hindi, and it selects {@code en_US}.
     */
    static boolean containsMultipleScripts(String text) {
        EnumSet<Character.UnicodeScript> scripts = EnumSet.noneOf(Character.UnicodeScript.class);
        text.codePoints().forEach(codePoint -> {
            if (!Character.isLetter(codePoint)) {
                return;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED) {
                return;
            }
            scripts.add(script);
        });
        return scripts.size() > 1;
    }

    private static boolean skipsDetection(String trimmed) {
        return trimmed.length() < MINIMUM_TEXT_LENGTH
                || trimmed.matches("\\d+(?:\\s+\\d+)*")
                || isIdentifierOnly(trimmed);
    }

    private static boolean isIdentifierOnly(String trimmed) {
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0) {
            return true;
        }
        for (String token : tokens) {
            if (!IDENTIFIER_TOKEN.matcher(token).matches()) {
                return false;
            }
        }
        return true;
    }

    private static Map<Language, String> localeMap() {
        EnumMap<Language, String> locales = new EnumMap<>(Language.class);
        locales.put(Language.AFRIKAANS, "af_ZA");
        locales.put(Language.ALBANIAN, "sq_AL");
        locales.put(Language.ARABIC, "ar_SA");
        locales.put(Language.ARMENIAN, "hy_AM");
        locales.put(Language.AZERBAIJANI, "az_AZ");
        locales.put(Language.BASQUE, "eu_ES");
        locales.put(Language.BELARUSIAN, "be_BY");
        locales.put(Language.BENGALI, "bn_BD");
        locales.put(Language.BOKMAL, "nb_NO");
        locales.put(Language.BOSNIAN, "bs_BA");
        locales.put(Language.BULGARIAN, "bg_BG");
        locales.put(Language.CATALAN, "ca_ES");
        locales.put(Language.CHINESE, "zh_CN");
        locales.put(Language.CROATIAN, "hr_HR");
        locales.put(Language.CZECH, "cs_CZ");
        locales.put(Language.DANISH, "da_DK");
        locales.put(Language.DUTCH, "nl_NL");
        locales.put(Language.ENGLISH, "en_US");
        locales.put(Language.ESTONIAN, "et_EE");
        locales.put(Language.FINNISH, "fi_FI");
        locales.put(Language.FRENCH, "fr_FR");
        locales.put(Language.GANDA, "lg_UG");
        locales.put(Language.GEORGIAN, "ka_GE");
        locales.put(Language.GERMAN, "de_DE");
        locales.put(Language.GREEK, "el_GR");
        locales.put(Language.GUJARATI, "gu_IN");
        locales.put(Language.HEBREW, "he_IL");
        locales.put(Language.HINDI, "hi_IN");
        locales.put(Language.HUNGARIAN, "hu_HU");
        locales.put(Language.ICELANDIC, "is_IS");
        locales.put(Language.INDONESIAN, "id_ID");
        locales.put(Language.IRISH, "ga_IE");
        locales.put(Language.ITALIAN, "it_IT");
        locales.put(Language.JAPANESE, "ja_JP");
        locales.put(Language.KAZAKH, "kk_KZ");
        locales.put(Language.KOREAN, "ko_KR");
        locales.put(Language.LATVIAN, "lv_LV");
        locales.put(Language.LITHUANIAN, "lt_LT");
        locales.put(Language.MACEDONIAN, "mk_MK");
        locales.put(Language.MALAY, "ms_MY");
        locales.put(Language.MAORI, "mi_NZ");
        locales.put(Language.MARATHI, "mr_IN");
        locales.put(Language.MONGOLIAN, "mn_MN");
        locales.put(Language.NYNORSK, "nn_NO");
        locales.put(Language.PERSIAN, "fa_IR");
        locales.put(Language.POLISH, "pl_PL");
        locales.put(Language.PORTUGUESE, "pt_PT");
        locales.put(Language.PUNJABI, "pa_IN");
        locales.put(Language.ROMANIAN, "ro_RO");
        locales.put(Language.RUSSIAN, "ru_RU");
        locales.put(Language.SERBIAN, "sr_RS");
        locales.put(Language.SHONA, "sn_ZW");
        locales.put(Language.SLOVAK, "sk_SK");
        locales.put(Language.SLOVENE, "sl_SI");
        locales.put(Language.SOMALI, "so_SO");
        locales.put(Language.SOTHO, "st_LS");
        locales.put(Language.SPANISH, "es_ES");
        locales.put(Language.SWAHILI, "sw_KE");
        locales.put(Language.SWEDISH, "sv_SE");
        locales.put(Language.TAGALOG, "tl_PH");
        locales.put(Language.TAMIL, "ta_IN");
        locales.put(Language.TELUGU, "te_IN");
        locales.put(Language.THAI, "th_TH");
        locales.put(Language.TSONGA, "ts_ZA");
        locales.put(Language.TSWANA, "tn_BW");
        locales.put(Language.TURKISH, "tr_TR");
        locales.put(Language.UKRAINIAN, "uk_UA");
        locales.put(Language.URDU, "ur_PK");
        locales.put(Language.VIETNAMESE, "vi_VN");
        locales.put(Language.WELSH, "cy_GB");
        locales.put(Language.XHOSA, "xh_ZA");
        locales.put(Language.YORUBA, "yo_NG");
        locales.put(Language.ZULU, "zu_ZA");
        return locales;
    }
}
