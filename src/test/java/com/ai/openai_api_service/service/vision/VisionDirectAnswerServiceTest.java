package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionDirectAnswerServiceTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    );

    @Mock
    private LlmChatCompletionsClient llmClient;

    private VisionDirectAnswerService service;

    @BeforeEach
    void setUp() {
        service = new VisionDirectAnswerService(llmClient, 1024);
    }

    @Test
    @SuppressWarnings("unchecked")
    void answer_sendsOriginalQuestionAndImageWithoutJsonObject() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(new LlmChatCompletionsClient.ChatCompletionResult(
                        "Visible error is about CRS610.",
                        false,
                        new OpenAIUsage(5, 9, 14, "global.openai.gpt-5.6-terra"),
                        40L,
                        Map.of()
                ));

        ValidatedImageAttachment image = new ValidatedImageAttachment(PNG_BYTES, "image/png", "a.png");
        VisionDirectAnswerResult result = service.answer("Was ist der Fehler?", image, "de_DE");

        assertEquals("Visible error is about CRS610.", result.reply());
        assertEquals(14, result.usage().getTotalTokens());
        assertEquals(40L, result.elapsedMs());

        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).completeMultimodal(captor.capture(), eq(0.3), eq(1024), eq(false), eq("VisionAnswer"));
        List<Map<String, Object>> content = (List<Map<String, Object>>) captor.getValue().get(1).get("content");
        assertTrue(((String) content.get(0).get("text")).contains("Was ist der Fehler?"));
        assertTrue(((String) content.get(0).get("text")).contains("de_DE"));
        Map<String, Object> imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertTrue(((String) imageUrl.get("url")).startsWith("data:image/png;base64,"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void answer_passesAllImagesInOrder() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(new LlmChatCompletionsClient.ChatCompletionResult(
                        "Both screens show CRS610.",
                        false,
                        new OpenAIUsage(1, 1, 2, "global.openai.gpt-5.6-terra"),
                        12L,
                        Map.of()
                ));
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        List<ValidatedImageAttachment> images = List.of(
                new ValidatedImageAttachment(PNG_BYTES, "image/png", "a.png"),
                new ValidatedImageAttachment(jpeg, "image/jpeg", "b.jpg")
        );

        VisionDirectAnswerResult result = service.answer("Compare", images, null);

        assertEquals("Both screens show CRS610.", result.reply());
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).completeMultimodal(captor.capture(), eq(0.3), eq(1024), eq(false), eq("VisionAnswer"));
        List<Map<String, Object>> content = (List<Map<String, Object>>) captor.getValue().get(1).get("content");
        assertEquals(3, content.size());
        assertTrue(((String) ((Map<String, Object>) content.get(1).get("image_url")).get("url"))
                .startsWith("data:image/png;base64,"));
        assertTrue(((String) ((Map<String, Object>) content.get(2).get("image_url")).get("url"))
                .startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void answer_rejectsEmptyModelReply() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(new LlmChatCompletionsClient.ChatCompletionResult(
                        "   ",
                        false,
                        null,
                        1L,
                        Map.of()
                ));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.answer("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "a.png"), "en_US")
        );
        assertEquals(502, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(Base64.getEncoder().encodeToString(PNG_BYTES)));
    }

    @Test
    void answer_sanitizesProviderErrorsWithDataUrl() {
        String leak = "failed data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenThrow(new OpenAIException(leak, 504));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.answer("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "a.png"), null)
        );
        assertEquals(504, ex.getStatusCode());
        assertFalse(ex.getMessage().contains("data:image/"));
    }
}
