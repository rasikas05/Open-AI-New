package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
import com.ai.openai_api_service.service.LlmChatCompletionsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
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
class VisionQueryGenerationServiceTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    );
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    private static final byte[] WEBP_BYTES = new byte[]{
            0x52, 0x49, 0x46, 0x46, 0x08, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };

    @Mock
    private LlmChatCompletionsClient llmClient;

    private VisionQueryGenerationService service;

    @BeforeEach
    void setUp() {
        service = new VisionQueryGenerationService(llmClient, new ObjectMapper(), 512);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_passesOriginalNonEnglishQuestionUnchangedAndNormalizedPngMime() {
        stubSuccess(validJson(4), usage(11, 7, 18), 321L);

        ValidatedImageAttachment image = new ValidatedImageAttachment(PNG_BYTES, "image/png", "a.png");
        VisionQueryGenerationResult result = service.generate("Was bedeutet Fehler in CRS610?", image);

        assertEquals(4, result.searchQueries().size());
        assertEquals(321L, result.elapsedMs());
        assertEquals(11, result.usage().getPromptTokens());

        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).completeMultimodal(messagesCaptor.capture(), eq(0.2), eq(512), eq(true), eq("VisionQuery"));
        List<Map<String, Object>> messages = messagesCaptor.getValue();
        assertEquals("system", messages.get(0).get("role"));
        Map<String, Object> user = messages.get(1);
        List<Map<String, Object>> content = (List<Map<String, Object>>) user.get("content");
        assertEquals("Was bedeutet Fehler in CRS610?", content.get(0).get("text"));
        Map<String, Object> imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        String dataUrl = (String) imageUrl.get("url");
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        assertFalse(exceptionLeaksBase64(new OpenAIException("ok", 400), dataUrl));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_replacesImageSentinelOnlyInModelPrompt() {
        stubSuccess(validJson(3), null, 50L);

        ValidatedImageAttachment image = new ValidatedImageAttachment(JPEG_BYTES, "image/jpeg", "b.jpg");
        VisionQueryGenerationResult result = service.generate("[Image]", image);

        assertEquals(3, result.searchQueries().size());
        assertEquals(0, result.usage().getPromptTokens());
        assertEquals(50L, result.elapsedMs());

        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).completeMultimodal(messagesCaptor.capture(), eq(0.2), eq(512), eq(true), eq("VisionQuery"));
        List<Map<String, Object>> content = (List<Map<String, Object>>) messagesCaptor.getValue().get(1).get("content");
        assertEquals(VisionQueryGenerationService.IMAGE_ONLY_USER_INSTRUCTION, content.get(0).get("text"));
        Map<String, Object> imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertTrue(((String) imageUrl.get("url")).startsWith("data:image/jpeg;base64,"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_webpUsesNormalizedMimeInDataUrl() {
        stubSuccess(validJson(4), usage(1, 1, 2), 10L);

        service.generate("Explain this screen", new ValidatedImageAttachment(WEBP_BYTES, "image/webp", "c.webp"));

        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).completeMultimodal(messagesCaptor.capture(), eq(0.2), eq(512), eq(true), eq("VisionQuery"));
        List<Map<String, Object>> content = (List<Map<String, Object>>) messagesCaptor.getValue().get(1).get("content");
        Map<String, Object> imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertTrue(((String) imageUrl.get("url")).startsWith("data:image/webp;base64,"));
    }

    @Test
    void generate_clampsToFourDistinctQueries() {
        stubSuccess("""
                {"imageSummary":"s","visibleText":["t"],"searchQueries":["a","b","c","d","e","a"]}
                """, usage(1, 1, 2), 12L);

        VisionQueryGenerationResult result = service.generate(
                "q",
                new ValidatedImageAttachment(PNG_BYTES, "image/png", "x.png")
        );
        assertEquals(List.of("a", "b", "c", "d"), result.searchQueries());
    }

    @Test
    void generate_rejectsFewerThanThreeQueriesWithoutLeakingBase64() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(completion("{\"imageSummary\":\"s\",\"visibleText\":[],\"searchQueries\":[\"only one\"]}", null, 9L));

        String fixtureB64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.generate("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "x.png"))
        );
        assertEquals(502, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(fixtureB64));
        assertFalse(ex.getMessage().contains("data:image/"));
    }

    @Test
    void generate_rejectsNonStringSearchQueryItems() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(completion(
                        "{\"imageSummary\":\"s\",\"visibleText\":[],\"searchQueries\":[\"a\",2,\"c\"]}",
                        null,
                        1L
                ));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.generate("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "x.png"))
        );
        assertEquals(502, ex.getStatusCode());
        assertTrue(ex.getMessage().toLowerCase().contains("string"));
    }

    @Test
    void generate_rejectsMalformedJsonWithoutLeakingBase64() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(completion("not-json", null, 1L));

        String fixtureB64 = Base64.getEncoder().encodeToString(PNG_BYTES);
        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.generate("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "x.png"))
        );
        assertEquals(502, ex.getStatusCode());
        assertFalse(ex.getMessage().contains(fixtureB64));
    }

    @Test
    void generate_propagatesProviderErrorWithoutRequestBody() {
        String leaky = "failed data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenThrow(new OpenAIException(leaky, 500));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.generate("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "x.png"))
        );
        assertEquals(500, ex.getStatusCode());
        assertFalse(ex.getMessage().contains("data:image/"));
        assertFalse(ex.getMessage().contains(Base64.getEncoder().encodeToString(PNG_BYTES)));
    }

    @Test
    void generate_propagatesTimeoutStatus() {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenThrow(new OpenAIException("Bedrock request timed out or failed after 10ms: read timed out", 504));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> service.generate("q", new ValidatedImageAttachment(PNG_BYTES, "image/png", "x.png"))
        );
        assertEquals(504, ex.getStatusCode());
    }

    @Test
    void generate_requiresAttachment() {
        OpenAIException ex = assertThrows(OpenAIException.class, () -> service.generate("q", (ValidatedImageAttachment) null));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_passesAllImagesInOrder() {
        stubSuccess(validJson(4), usage(2, 2, 4), 20L);
        List<ValidatedImageAttachment> images = List.of(
                new ValidatedImageAttachment(PNG_BYTES, "image/png", "a.png"),
                new ValidatedImageAttachment(JPEG_BYTES, "image/jpeg", "b.jpg")
        );

        service.generate("Compare screens", images);

        ArgumentCaptor<List<Map<String, Object>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).completeMultimodal(messagesCaptor.capture(), eq(0.2), eq(512), eq(true), eq("VisionQuery"));
        List<Map<String, Object>> content = (List<Map<String, Object>>) messagesCaptor.getValue().get(1).get("content");
        assertEquals(3, content.size());
        assertEquals("Compare screens", content.get(0).get("text"));
        assertTrue(((String) ((Map<String, Object>) content.get(1).get("image_url")).get("url"))
                .startsWith("data:image/png;base64,"));
        assertTrue(((String) ((Map<String, Object>) content.get(2).get("image_url")).get("url"))
                .startsWith("data:image/jpeg;base64,"));
    }

    private void stubSuccess(String json, OpenAIUsage usage, long elapsedMs) {
        when(llmClient.completeMultimodal(any(), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(completion(json, usage, elapsedMs));
    }

    private static LlmChatCompletionsClient.ChatCompletionResult completion(
            String content,
            OpenAIUsage usage,
            long elapsedMs
    ) {
        return new LlmChatCompletionsClient.ChatCompletionResult(
                content,
                false,
                usage,
                elapsedMs,
                Map.of()
        );
    }

    private static OpenAIUsage usage(int prompt, int completion, int total) {
        return new OpenAIUsage(prompt, completion, total, "global.openai.gpt-5.6-terra");
    }

    private static String validJson(int queryCount) {
        StringBuilder queries = new StringBuilder();
        for (int i = 1; i <= queryCount; i++) {
            if (i > 1) {
                queries.append(',');
            }
            queries.append("\"query ").append(i).append('"');
        }
        return "{\"imageSummary\":\"summary\",\"visibleText\":[\"label\"],\"searchQueries\":["
                + queries + "]}";
    }

    private static boolean exceptionLeaksBase64(OpenAIException ex, String dataUrl) {
        return ex.getMessage() != null && ex.getMessage().contains(dataUrl);
    }
}
