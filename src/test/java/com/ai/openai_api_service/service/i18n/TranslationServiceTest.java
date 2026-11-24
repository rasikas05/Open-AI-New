package com.ai.openai_api_service.service.i18n;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;
import software.amazon.awssdk.services.translate.model.UnsupportedLanguagePairException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private TranslateClient translateClient;
    @Mock
    private LanguageDetectionService languageDetectionService;
    @Mock
    private LlmChatCompletionsClient llmClient;

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(
                translateClient,
                languageDetectionService,
                new SupportedResponseLanguages(""),
                llmClient
        );
    }

    @Test
    void english_skipsTranslate() {
        when(languageDetectionService.bypassesTranslation("How do I check customer details?")).thenReturn(false);

        String result = translationService.normalize(
                "How do I check customer details?",
                detected("en")
        );

        assertEquals("How do I check customer details?", result);
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
    }

    @Test
    void identifierOnly_skipsTranslate() {
        when(languageDetectionService.bypassesTranslation("OIS100")).thenReturn(true);

        String result = translationService.normalize("OIS100", LanguageDetectionResult.fallback(0.0));

        assertEquals("OIS100", result);
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
    }

    @Test
    void german_translatesAndRestoresIdentifiers() {
        when(languageDetectionService.bypassesTranslation("Zeige Kunde Y00111 in OIS100")).thenReturn(false);
        when(translateClient.translateText(any(TranslateTextRequest.class))).thenAnswer(invocation -> {
            TranslateTextRequest request = invocation.getArgument(0);
            String translated = request.text().replace("Zeige Kunde", "Show customer").replace(" in ", " in ");
            return TranslateTextResponse.builder().translatedText(translated).build();
        });

        String result = translationService.normalize("Zeige Kunde Y00111 in OIS100", detected("de"));

        assertEquals("Show customer Y00111 in OIS100", result);
        ArgumentCaptor<TranslateTextRequest> captor = ArgumentCaptor.forClass(TranslateTextRequest.class);
        verify(translateClient).translateText(captor.capture());
        assertEquals("de", captor.getValue().sourceLanguageCode());
        assertEquals("en", captor.getValue().targetLanguageCode());
        assertTrue(captor.getValue().text().contains("M3KEEP"));
        assertTrue(!captor.getValue().text().contains("Y00111"));
        assertTrue(!captor.getValue().text().contains("OIS100"));
    }

    @Test
    void brokenPlaceholder_stopsWithoutReturningText() {
        when(languageDetectionService.bypassesTranslation("Zeige OIS100")).thenReturn(false);
        when(translateClient.translateText(any(TranslateTextRequest.class)))
                .thenReturn(TranslateTextResponse.builder().translatedText("Show the program").build());

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalize("Zeige OIS100", detected("de"))
        );

        assertEquals(422, ex.getStatusCode());
    }

    @Test
    void unknownLanguage_stopsBeforeTranslate() {
        when(languageDetectionService.bypassesTranslation("meaningful text here")).thenReturn(false);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalize("meaningful text here", LanguageDetectionResult.fallback(0.0))
        );

        assertEquals(422, ex.getStatusCode());
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
    }

    @Test
    void unsupportedLanguage_doesNotCallTranslate() {
        when(languageDetectionService.bypassesTranslation("Bonjour client")).thenReturn(false);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalize("Bonjour client", detected("zz"))
        );

        assertEquals(422, ex.getStatusCode());
        assertEquals("The message language could not be recognized.", ex.getMessage());
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
    }

    @Test
    void translateClientFailure_returnsControlledError() {
        when(languageDetectionService.bypassesTranslation("Bonjour client")).thenReturn(false);
        when(translateClient.translateText(any(TranslateTextRequest.class)))
                .thenThrow(UnsupportedLanguagePairException.builder()
                        .message("unsupported")
                        .statusCode(400)
                        .requestId("req-1")
                        .awsErrorDetails(AwsErrorDetails.builder()
                                .errorCode("UnsupportedLanguagePairException")
                                .errorMessage("unsupported")
                                .serviceName("Translate")
                                .build())
                        .build());

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalize("Bonjour client", detected("fr"))
        );

        assertEquals(422, ex.getStatusCode());
        assertEquals("The message could not be translated.", ex.getMessage());
    }

    @Test
    void oversizedInput_doesNotCallTranslate() {
        String oversized = "ä".repeat(TranslationService.MAX_UTF8_BYTES);
        when(languageDetectionService.bypassesTranslation(oversized)).thenReturn(false);

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalize(oversized, detected("de"))
        );

        assertEquals(422, ex.getStatusCode());
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
    }

    @Test
    void romanizedHinglish_usesLlmNotAmazonTranslateAndRestoresIds() {
        String original = "Customer Y00111 ani MMS001 ani 45000123 chi details sanga";
        when(languageDetectionService.bypassesTranslation(original)).thenReturn(false);
        when(llmClient.complete(anyList(), anyDouble(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
            List<Map<String, String>> messages = invocation.getArgument(0);
            String user = messages.get(1).get("content");
            assertTrue(user.contains("M3KEEP"));
            assertTrue(!user.contains("Y00111"));
            String shielded = user.substring(user.indexOf('\n') + 2);
            String translated = shielded
                    .replace("Customer", "Customer")
                    .replace(" ani ", " and ")
                    .replace(" chi details sanga", " details please");
            return new LlmChatCompletionsClient.ChatCompletionResult(translated, false, null, 1L, Map.of());
        });

        String result = translationService.normalizeRomanized(original, AcceptedRomanScript.hinglish());

        assertEquals("Customer Y00111 and MMS001 and 45000123 details please", result);
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(messagesCaptor.capture(), anyDouble(), anyInt(), anyBoolean());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = messagesCaptor.getValue();
        assertTrue(messages.get(0).get("content").contains("M3"));
    }

    @Test
    void romanizedLlmFailure_doesNotFallBackToAmazonTranslate() {
        String original = "M3 madhe customer master maintain kasa karaycha";
        when(languageDetectionService.bypassesTranslation(original)).thenReturn(false);
        when(llmClient.complete(anyList(), anyDouble(), anyInt(), anyBoolean()))
                .thenThrow(new OpenAIException("Bedrock down", 504));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> translationService.normalizeRomanized(original, AcceptedRomanScript.marathi())
        );

        assertEquals(422, ex.getStatusCode());
        assertEquals("The message could not be translated.", ex.getMessage());
        verify(translateClient, never()).translateText(any(TranslateTextRequest.class));
    }

    @Test
    void nativeScriptStillUsesAmazonTranslate() {
        when(languageDetectionService.bypassesTranslation("नया आइटम कैसे बनाएं")).thenReturn(false);
        when(translateClient.translateText(any(TranslateTextRequest.class))).thenReturn(
                TranslateTextResponse.builder().translatedText("How to create a new item").build()
        );

        String result = translationService.normalize("नया आइटम कैसे बनाएं", detected("hi"));

        assertEquals("How to create a new item", result);
        verify(translateClient).translateText(any(TranslateTextRequest.class));
        verify(llmClient, never()).complete(anyList(), anyDouble(), anyInt(), anyBoolean());
    }

    private static LanguageDetectionResult detected(String language) {
        return new LanguageDetectionResult(language, language, "none", language, language, 1.0, null, false);
    }
}
