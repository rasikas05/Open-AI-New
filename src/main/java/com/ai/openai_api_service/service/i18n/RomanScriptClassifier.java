package com.ai.openai_api_service.service.i18n;

import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import com.ai.openai_api_service.service.timing.FlowTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detection-only label for Latin-script Hindi or Marathi after Lingua returns no supported language.
 * The model text is never a user-facing answer.
 */
@Service
public class RomanScriptClassifier {

    static final int MAX_COMPLETION_TOKENS = 16;
    static final String HINGLISH = "HINGLISH";
    static final String ROMAN_MARATHI = "ROMAN_MARATHI";
    private static final String SYSTEM_PROMPT =
            "Reply with only one label and no other text: HINGLISH, ROMAN_MARATHI, or OTHER. "
                    + "HINGLISH means Hindi written in Latin script. "
                    + "ROMAN_MARATHI means Marathi written in Latin script. "
                    + "Use OTHER for every other language.";

    private static final Logger log = LoggerFactory.getLogger(RomanScriptClassifier.class);

    private final LlmChatCompletionsClient llmClient;

    public RomanScriptClassifier(LlmChatCompletionsClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * @return a label only when the model content is exactly {@code HINGLISH} or {@code ROMAN_MARATHI} after trim
     */
    public Optional<AcceptedRomanScript> classify(String text) {
        if (text == null || text.isBlank() || llmClient == null) {
            return Optional.empty();
        }
        String content;
        try {
            FlowTrace.recordLlmCall();
            LlmChatCompletionsClient.ChatCompletionResult result = llmClient.complete(
                    List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", text)
                    ),
                    0.0,
                    MAX_COMPLETION_TOKENS,
                    false,
                    "RomanClassify"
            );
            content = result == null ? null : result.content();
        } catch (RuntimeException ex) {
            log.warn("RomanScriptClassifier failed errorType={}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
        return accept(content);
    }

    /**
     * Exact match after trimming whitespace. A sentence that contains a label is rejected.
     */
    static Optional<AcceptedRomanScript> accept(String content) {
        if (content == null) {
            return Optional.empty();
        }
        String trimmed = content.trim();
        if (HINGLISH.equals(trimmed)) {
            return Optional.of(AcceptedRomanScript.hinglish());
        }
        if (ROMAN_MARATHI.equals(trimmed)) {
            return Optional.of(AcceptedRomanScript.marathi());
        }
        return Optional.empty();
    }
}
