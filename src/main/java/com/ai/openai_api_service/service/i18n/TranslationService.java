package com.ai.openai_api_service.service.i18n;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import com.ai.openai_api_service.service.timing.FlowTrace;
import com.ai.openai_api_service.service.timing.RoutingSummaryLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateException;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates an incoming question to English for the existing router, Lex bot, and retrieval.
 * The completed answer is not translated here.
 */
@Service
public class TranslationService {

    static final int MAX_UTF8_BYTES = 10_000;
    static final int ROMANIZED_MAX_COMPLETION_TOKENS = 512;
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final Pattern IDENTIFIER_TOKEN = Pattern.compile(
            "(?:[A-Z]{1,10}\\d{1,8}[A-Z]{0,4}|\\d+|[A-Z]{2,10})"
    );
    private static final String ROMANIZED_SYSTEM_PROMPT =
            "Translate the user message into English. Output only the English translation. "
                    + "Do not answer the question, add explanations, or invent details. "
                    + "Preserve the product name M3 exactly. "
                    + "Leave M3KEEP placeholders unchanged.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TranslateClient translateClient;
    private final LanguageDetectionService languageDetectionService;
    private final SupportedResponseLanguages supportedResponseLanguages;
    private final LlmChatCompletionsClient llmClient;

    public TranslationService(
            TranslateClient translateClient,
            LanguageDetectionService languageDetectionService,
            SupportedResponseLanguages supportedResponseLanguages,
            LlmChatCompletionsClient llmClient
    ) {
        this.translateClient = translateClient;
        this.languageDetectionService = languageDetectionService;
        this.supportedResponseLanguages = supportedResponseLanguages;
        this.llmClient = llmClient;
    }

    /**
     * Returns the English text used for routing and retrieval.
     * English, short, numeric, and identifier-only messages are returned unchanged.
     */
    public String normalize(String original, LanguageDetectionResult detected) {
        if (original == null || languageDetectionService.bypassesTranslation(original)) {
            FlowTrace.translationSkipped(null);
            return original;
        }
        String source = detected == null
                ? LanguageDetectionService.NO_LANGUAGE
                : detected.detectedLanguage();
        if ("en".equals(source)) {
            FlowTrace.translationSkipped(source);
            return original;
        }
        if (source == null
                || LanguageDetectionService.NO_LANGUAGE.equals(source)
                || "unknown".equalsIgnoreCase(source)
                || !supportedResponseLanguages.supportsLanguage(source)) {
            log.warn(
                    "TranslationService rejected source={} target=en utf8Bytes={} errorType=UnrecognizedLanguage",
                    source,
                    utf8Length(original)
            );
            FlowTrace.translationFailed(source, "UnrecognizedLanguage");
            throw new OpenAIException("The message language could not be recognized.", 422);
        }
        return translateToEnglish(original, source);
    }

    /**
     * LLM translation for Latin-script Hindi/Marathi after {@link RomanScriptClassifier} acceptance.
     * Does not call Amazon Translate. On LLM failure returns the controlled 422 path (no Amazon retry).
     */
    public String normalizeRomanized(String original, AcceptedRomanScript script) {
        if (original == null || languageDetectionService.bypassesTranslation(original)) {
            FlowTrace.translationSkipped(script == null ? null : script.languageCode());
            return original;
        }
        if (script == null || script.languageCode() == null || script.languageCode().isBlank()) {
            FlowTrace.translationFailed(null, "MissingRomanScript");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        if (llmClient == null) {
            FlowTrace.translationFailed(script.languageCode(), "LlmUnavailable");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        return translateRomanizedWithLlm(original, script.languageCode());
    }

    private String translateRomanizedWithLlm(String original, String source) {
        int bytes = utf8Length(original);
        if (bytes > MAX_UTF8_BYTES) {
            log.warn(
                    "TranslationService.romanized rejected source={} target=en utf8Bytes={} errorType=InputTooLong",
                    source,
                    bytes
            );
            FlowTrace.translationFailed(source, "InputTooLong");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        Shield shield = shieldIdentifiers(original);
        if (utf8Length(shield.text()) > MAX_UTF8_BYTES) {
            log.warn(
                    "TranslationService.romanized rejected source={} target=en utf8Bytes={} errorType=ShieldedInputTooLong",
                    source,
                    utf8Length(shield.text())
            );
            FlowTrace.translationFailed(source, "ShieldedInputTooLong");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        String translated;
        try {
            FlowTrace.recordLlmCall();
            String userPrompt = "Source language code: " + source + "\n\n" + shield.text();
            LlmChatCompletionsClient.ChatCompletionResult result = llmClient.complete(
                    List.of(
                            Map.of("role", "system", "content", ROMANIZED_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    0.0,
                    ROMANIZED_MAX_COMPLETION_TOKENS,
                    false
            );
            translated = result == null ? null : result.content();
        } catch (RuntimeException ex) {
            log.warn(
                    "TranslationService.romanized failed source={} target=en utf8Bytes={} errorType={} (no Amazon Translate fallback)",
                    source,
                    bytes,
                    ex.getClass().getSimpleName()
            );
            FlowTrace.translationFailed(source, ex.getClass().getSimpleName());
            throw new OpenAIException("The message could not be translated.", 422);
        }
        String restored = restoreIdentifiers(translated, shield, source, bytes);
        log.info(
                "TranslationService.romanized source={} target=en utf8Bytes={} text='{}'",
                source,
                bytes,
                RoutingSummaryLog.questionForLog(restored)
        );
        FlowTrace.translationOk(source, restored);
        return restored;
    }

    private String translateToEnglish(String original, String source) {
        int bytes = utf8Length(original);
        if (bytes > MAX_UTF8_BYTES) {
            log.warn(
                    "TranslationService rejected source={} target=en utf8Bytes={} errorType=InputTooLong",
                    source,
                    bytes
            );
            FlowTrace.translationFailed(source, "InputTooLong");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        Shield shield = shieldIdentifiers(original);
        if (utf8Length(shield.text()) > MAX_UTF8_BYTES) {
            log.warn(
                    "TranslationService rejected source={} target=en utf8Bytes={} errorType=ShieldedInputTooLong",
                    source,
                    utf8Length(shield.text())
            );
            FlowTrace.translationFailed(source, "ShieldedInputTooLong");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        String translated;
        try {
            TranslateTextResponse response = translateClient.translateText(TranslateTextRequest.builder()
                    .sourceLanguageCode(source)
                    .targetLanguageCode("en")
                    .text(shield.text())
                    .build());
            translated = response == null ? null : response.translatedText();
        } catch (TranslateException ex) {
            AwsErrorDetails details = ex.awsErrorDetails();
            log.error(
                    "TranslationService failed source={} target=en utf8Bytes={} status={} errorCode={} awsMessage={} requestId={}",
                    source,
                    bytes,
                    ex.statusCode(),
                    details != null ? details.errorCode() : "unknown",
                    details != null ? details.errorMessage() : "unknown",
                    ex.requestId(),
                    ex
            );
            FlowTrace.translationFailed(source, ex.getClass().getSimpleName());
            throw new OpenAIException("The message could not be translated.", 422);
        } catch (RuntimeException ex) {
            log.warn(
                    "TranslationService failed source={} target=en utf8Bytes={} errorType={}",
                    source,
                    bytes,
                    ex.getClass().getSimpleName()
            );
            FlowTrace.translationFailed(source, ex.getClass().getSimpleName());
            throw new OpenAIException("The message could not be translated.", 422);
        }
        String restored = restoreIdentifiers(translated, shield, source, bytes);
        log.info(
                "TranslationService.translate source={} target=en utf8Bytes={} text='{}'",
                source,
                bytes,
                RoutingSummaryLog.questionForLog(restored)
        );
        FlowTrace.translationOk(source, restored);
        return restored;
    }

    private String restoreIdentifiers(String translated, Shield shield, String source, int bytes) {
        if (translated == null || translated.isBlank()) {
            log.warn(
                    "TranslationService failed source={} target=en utf8Bytes={} errorType=EmptyResult",
                    source,
                    bytes
            );
            FlowTrace.translationFailed(source, "EmptyResult");
            throw new OpenAIException("The message could not be translated.", 422);
        }
        int found = countPlaceholders(translated, shield.token());
        if (found != shield.placeholders().size()) {
            failRestore(source, bytes, "UnexpectedPlaceholder");
        }
        String restored = translated;
        for (int i = 0; i < shield.placeholders().size(); i++) {
            String placeholder = shield.placeholders().get(i);
            if (countOccurrences(restored, placeholder) != 1) {
                failRestore(source, bytes, "PlaceholderMismatch");
            }
            restored = restored.replace(placeholder, shield.values().get(i));
        }
        if (restored.isBlank() || countPlaceholders(restored, shield.token()) != 0) {
            failRestore(source, bytes, "RestoreInvalid");
        }
        for (String value : shield.values()) {
            if (!restored.contains(value)) {
                failRestore(source, bytes, "IdentifierMissing");
            }
        }
        return restored;
    }

    private void failRestore(String source, int bytes, String errorType) {
        log.warn(
                "TranslationService failed source={} target=en utf8Bytes={} errorType={}",
                source,
                bytes,
                errorType
        );
        FlowTrace.translationFailed(source, errorType);
        throw new OpenAIException("The message could not be translated.", 422);
    }

    /**
     * Runs {@code transform} on identifier-shielded text, then restores identifiers such as {@code Y00111}.
     * A missing placeholder is left unrestored. The raw input is not returned after {@code transform} runs.
     */
    public static String preserveIdentifiers(String text, java.util.function.UnaryOperator<String> transform) {
        if (text == null || text.isBlank() || transform == null) {
            return text;
        }
        Shield shield = shieldIdentifiers(text);
        String transformed = transform.apply(shield.text());
        if (transformed == null) {
            return "";
        }
        String restored = transformed;
        for (int i = 0; i < shield.placeholders().size(); i++) {
            restored = restored.replace(shield.placeholders().get(i), shield.values().get(i));
        }
        return restored;
    }

    private static Shield shieldIdentifiers(String original) {
        String token = requestToken(original);
        Matcher matcher = IDENTIFIER_TOKEN.matcher(original);
        StringBuilder shielded = new StringBuilder();
        List<String> values = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        int last = 0;
        int index = 0;
        while (matcher.find()) {
            shielded.append(original, last, matcher.start());
            String placeholder = "M3KEEP" + token + "X" + index;
            values.add(matcher.group());
            placeholders.add(placeholder);
            shielded.append(placeholder);
            last = matcher.end();
            index++;
        }
        shielded.append(original.substring(last));
        return new Shield(shielded.toString(), values, token, placeholders);
    }

    private static String requestToken(String original) {
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] bytes = new byte[4];
            RANDOM.nextBytes(bytes);
            StringBuilder token = new StringBuilder();
            for (byte value : bytes) {
                token.append(Character.forDigit((value >> 4) & 0xF, 16));
                token.append(Character.forDigit(value & 0xF, 16));
            }
            String candidate = token.toString().toUpperCase(java.util.Locale.ROOT);
            if (!original.contains(candidate)) {
                return candidate;
            }
        }
        throw new OpenAIException("The message could not be translated.", 422);
    }

    private static int countPlaceholders(String text, String token) {
        Matcher matcher = Pattern.compile("M3KEEP" + Pattern.quote(token) + "X\\d+").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while (from <= text.length()) {
            int found = text.indexOf(token, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + token.length();
        }
        return count;
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private record Shield(
            String text,
            List<String> values,
            String token,
            List<String> placeholders
    ) {
    }
}
