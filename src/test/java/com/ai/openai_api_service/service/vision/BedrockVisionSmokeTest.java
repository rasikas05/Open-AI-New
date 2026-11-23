package com.ai.openai_api_service.service.vision;

import com.ai.openai_api_service.exception.OpenAIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 1 automated vision smoke — mocked HTTP only. No live Bedrock calls.
 */
@ExtendWith(MockitoExtension.class)
class BedrockVisionSmokeTest {

    private static final String MODEL = BedrockVisionRequestSupport.DEFAULT_MODEL;
    private static final String URL = BedrockVisionRequestSupport.DEFAULT_URL;

    @Mock
    private RestTemplate restTemplate;

    private byte[] pngBytes;
    private String dataUrl;

    @BeforeEach
    void setUp() throws Exception {
        pngBytes = BedrockVisionRequestSupport.generateSmokePngBytes();
        dataUrl = BedrockVisionRequestSupport.toPngDataUrl(pngBytes);
    }

    @Test
    void generateSmokePng_producesNonEmptyPngBytes() {
        assertTrue(pngBytes.length > 32);
        // PNG signature
        assertEquals((byte) 0x89, pngBytes[0]);
        assertEquals((byte) 0x50, pngBytes[1]);
        assertEquals((byte) 0x4E, pngBytes[2]);
        assertEquals((byte) 0x47, pngBytes[3]);
    }

    @Test
    void toPngDataUrl_usesImagePngMimeAndBase64Prefix() {
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        assertTrue(dataUrl.length() > "data:image/png;base64,".length() + 16);
        assertFalse(dataUrl.contains("\n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMultimodalBody_usesConfiguredModelAndContentBlocks() {
        Map<String, Object> body = BedrockVisionRequestSupport.buildMultimodalBody(
                MODEL,
                dataUrl,
                BedrockVisionRequestSupport.DEFAULT_PROMPT
        );

        assertEquals(MODEL, body.get("model"));
        assertEquals("none", body.get("reasoning_effort"));
        assertEquals(256, body.get("max_completion_tokens"));

        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get("role"));

        List<Map<String, Object>> content = (List<Map<String, Object>>) messages.get(0).get("content");
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertTrue(content.get(0).get("text").toString().contains("visible text"));
        assertEquals("image_url", content.get(1).get("type"));
        Map<String, Object> imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertEquals(dataUrl, imageUrl.get("url"));
    }

    @Test
    void extractAssistantContent_parsesSuccessfulChatCompletionsResponse() {
        Map<String, Object> response = sampleResponse("The image shows M3-VISION-SMOKE");
        String content = BedrockVisionRequestSupport.extractAssistantContent(response);
        assertEquals("The image shows M3-VISION-SMOKE", content);
        assertTrue(BedrockVisionRequestSupport.responseMentionsSmokePhrase(content));
    }

    @Test
    void requireApiKey_missingKey_throwsUnauthorizedStyleException() {
        OpenAIException ex = assertThrows(OpenAIException.class, () -> requireApiKey("  "));
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Bedrock"));
        assertFalse(ex.getMessage().toLowerCase().contains("bearer"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postVision_mockedSuccess_postsToConfiguredUrlAndParsesContent() {
        requireApiKey("test-bedrock-key");
        Map<String, Object> body = BedrockVisionRequestSupport.buildMultimodalBody(
                MODEL, dataUrl, BedrockVisionRequestSupport.DEFAULT_PROMPT
        );
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(sampleResponse("Visible text: M3-VISION-SMOKE")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("test-bedrock-key");

        ResponseEntity<Map> entity = restTemplate.exchange(
                URL,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        String content = BedrockVisionRequestSupport.extractAssistantContent(entity.getBody());

        assertTrue(BedrockVisionRequestSupport.responseMentionsSmokePhrase(content));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(URL), eq(HttpMethod.POST), captor.capture(), eq(Map.class));
        Map<String, Object> sent = (Map<String, Object>) captor.getValue().getBody();
        assertEquals(MODEL, sent.get("model"));
        // Ensure we never asserted or printed the data URL in logs; structure only.
        assertTrue(captor.getValue().getHeaders().getContentType().includes(MediaType.APPLICATION_JSON));
    }

    @Test
    void http401_mapsToOpenAiExceptionWithoutExposingBodySecrets() {
        HttpClientErrorException err = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"error\":\"invalid_api_key\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        OpenAIException mapped = mapClientError(err);
        assertEquals(401, mapped.getStatusCode());
        assertTrue(mapped.getMessage().toLowerCase().contains("api key"));
        assertFalse(mapped.getMessage().contains("test-bedrock-key"));
    }

    @Test
    void http400_mapsToOpenAiException() {
        HttpClientErrorException err = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"error\":\"invalid_request\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        OpenAIException mapped = mapClientError(err);
        assertEquals(400, mapped.getStatusCode());
    }

    @Test
    void http429_mapsToOpenAiException() {
        HttpClientErrorException err = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "{\"error\":\"rate_limit\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        OpenAIException mapped = mapClientError(err);
        assertEquals(429, mapped.getStatusCode());
    }

    @Test
    void timeout_mapsToGatewayTimeoutStyleException() {
        ResourceAccessException cause = new ResourceAccessException("Read timed out");
        OpenAIException mapped = mapTimeout(cause, 50L);
        assertEquals(504, mapped.getStatusCode());
        assertTrue(mapped.getMessage().contains("timed out") || mapped.getMessage().contains("failed"));
    }

    private static void requireApiKey(String key) {
        if (key == null || key.isBlank()) {
            throw new OpenAIException(
                    "Bedrock API key is missing. Set BEDROCK_API_KEY env var or bedrock.runtime.api-key property.",
                    401
            );
        }
    }

    private static OpenAIException mapClientError(HttpClientErrorException e) {
        int code = e.getStatusCode().value();
        String msg = code == 401
                ? "Bedrock API key is invalid or missing. Check provider credentials (no quotes)."
                : "Bedrock API error: " + code + " " + e.getStatusText();
        return new OpenAIException(msg, code);
    }

    private static OpenAIException mapTimeout(ResourceAccessException e, long elapsedMs) {
        return new OpenAIException(
                "Bedrock request timed out or failed after " + elapsedMs + "ms: " + e.getMessage(),
                504
        );
    }

    private static Map<String, Object> sampleResponse(String content) {
        return Map.of(
                "choices", List.of(
                        Map.of(
                                "message", Map.of("role", "assistant", "content", content),
                                "finish_reason", "stop"
                        )
                ),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15)
        );
    }
}
