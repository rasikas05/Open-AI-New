package com.ai.openai_api_service.service.i18n;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RomanScriptClassifierTest {

    @Mock
    private LlmChatCompletionsClient llmClient;

    private RomanScriptClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RomanScriptClassifier(llmClient);
    }

    @Test
    void exactLabelsAreAcceptedAfterTrim() {
        assertEquals("hi", RomanScriptClassifier.accept("HINGLISH").orElseThrow().languageCode());
        assertEquals("hi_IN", RomanScriptClassifier.accept("  HINGLISH\n").orElseThrow().locale());
        assertEquals("mr", RomanScriptClassifier.accept("ROMAN_MARATHI").orElseThrow().languageCode());
        assertEquals("mr_IN", RomanScriptClassifier.accept(" ROMAN_MARATHI ").orElseThrow().locale());
        assertEquals(0.0, RomanScriptClassifier.accept("HINGLISH").orElseThrow().detectionResult().winnerConfidence());
        assertFalse(RomanScriptClassifier.accept("HINGLISH").orElseThrow().detectionResult().fallback());
    }

    @Test
    void sentencesAndOtherLabelsAreRejected() {
        assertTrue(RomanScriptClassifier.accept("The language is HINGLISH").isEmpty());
        assertTrue(RomanScriptClassifier.accept("hinglish").isEmpty());
        assertTrue(RomanScriptClassifier.accept("OTHER").isEmpty());
        assertTrue(RomanScriptClassifier.accept("").isEmpty());
        assertTrue(RomanScriptClassifier.accept("   ").isEmpty());
        assertTrue(RomanScriptClassifier.accept(null).isEmpty());
        assertTrue(RomanScriptClassifier.accept("HINGLISH.").isEmpty());
    }

    @Test
    void classifySendsOnlyTheLabelPromptAndAcceptsAnExactLabel() {
        when(llmClient.complete(any(), eq(0.0), eq(RomanScriptClassifier.MAX_COMPLETION_TOKENS), eq(false)))
                .thenReturn(completion("HINGLISH"));

        Optional<AcceptedRomanScript> accepted = classifier.classify("mujhe customer details batao");

        assertEquals("hi", accepted.orElseThrow().languageCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> messages = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(messages.capture(), eq(0.0), eq(16), eq(false));
        assertEquals("system", messages.getValue().get(0).get("role"));
        assertTrue(messages.getValue().get(0).get("content").contains("HINGLISH"));
        assertFalse(messages.getValue().get(0).get("content").contains("CLEAR"));
        assertEquals("mujhe customer details batao", messages.getValue().get(1).get("content"));
    }

    @Test
    void classifyRejectsASentenceAndAModelFailure() {
        when(llmClient.complete(any(), any(), any(), anyBoolean()))
                .thenReturn(completion("This is HINGLISH, not a label."));

        assertTrue(classifier.classify("ciao come stai oggi").isEmpty());

        when(llmClient.complete(any(), any(), any(), anyBoolean()))
                .thenThrow(new OpenAIException("Bedrock request timed out", 504));

        assertTrue(classifier.classify("mala madat kar").isEmpty());
    }

    @Test
    void blankTextDoesNotCallTheModel() {
        assertTrue(classifier.classify("  ").isEmpty());
        verify(llmClient, never()).complete(any(), any(), any(), anyBoolean());
    }

    private static LlmChatCompletionsClient.ChatCompletionResult completion(String content) {
        return new LlmChatCompletionsClient.ChatCompletionResult(
                content,
                false,
                new OpenAIUsage(1, 1, 2, "gpt"),
                1L,
                Map.of()
        );
    }
}
