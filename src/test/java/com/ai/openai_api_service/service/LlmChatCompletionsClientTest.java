package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.OpenAIUsage;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
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

@ExtendWith(MockitoExtension.class)
class LlmChatCompletionsClientTest {

    @Mock
    private RestTemplate restTemplate;

    private LlmChatCompletionsClient client;

    @BeforeEach
    void setUp() {
        client = new LlmChatCompletionsClient();
        ReflectionTestUtils.setField(client, "provider", "openai");
        ReflectionTestUtils.setField(client, "openaiApiKey", "sk-openai-test");
        ReflectionTestUtils.setField(client, "openaiModel", "gpt-5.6-terra");
        ReflectionTestUtils.setField(client, "openaiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(client, "openaiTimeoutMs", 120_000);
        ReflectionTestUtils.setField(client, "openaiReasoningEffort", "none");
        ReflectionTestUtils.setField(client, "openaiMaxCompletionTokens", 4096);
        ReflectionTestUtils.setField(client, "bedrockApiKey", "bedrock-key-test");
        ReflectionTestUtils.setField(client, "bedrockModel", "global.openai.gpt-5.6-terra");
        ReflectionTestUtils.setField(
                client,
                "bedrockUrl",
                "https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions"
        );
        ReflectionTestUtils.setField(client, "bedrockTimeoutMs", 120_000);
        ReflectionTestUtils.setField(client, "bedrockReasoningEffort", "none");
        ReflectionTestUtils.setField(client, "bedrockMaxCompletionTokens", 4096);
        client.setRestTemplate(restTemplate);
    }

    @Test
    void activeProvider_selectsOpenAiSettingsByDefault() {
        assertEquals("openai", client.activeProvider());
        assertEquals("gpt-5.6-terra", client.activeModel());
        assertEquals("https://api.openai.com/v1/chat/completions", client.activeUrl());
        assertEquals("sk-openai-test", client.activeApiKey());
        assertFalse(client.isBedrock());
    }

    @Test
    void activeProvider_selectsBedrockSettingsWhenConfigured() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        assertEquals("bedrock", client.activeProvider());
        assertEquals("global.openai.gpt-5.6-terra", client.activeModel());
        assertTrue(client.activeUrl().contains("bedrock-runtime.eu-west-1"));
        assertEquals("bedrock-key-test", client.activeApiKey());
        assertTrue(client.isBedrock());
    }

    @Test
    void unsupportedProvider_failsFast() {
        ReflectionTestUtils.setField(client, "provider", "claude");
        IllegalStateException ex = assertThrows(IllegalStateException.class, client::validateProviderConfigured);
        assertTrue(ex.getMessage().contains("Unsupported llm.provider"));
    }

    @Test
    void bedrockWithoutKey_doesNotFallBackToOpenAiKey() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        ReflectionTestUtils.setField(client, "bedrockApiKey", "  ");
        OpenAIException ex = assertThrows(OpenAIException.class, client::requireApiKey);
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Bedrock"));
        assertFalse(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void complete_openai_parsesContentUsageAndTruncation() {
        Map<String, Object> response = sampleResponse("hello", "length", 10, 5, 15);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        LlmChatCompletionsClient.ChatCompletionResult result = client.complete(
                List.of(Map.of("role", "user", "content", "hi")),
                0.3,
                256,
                false
        );

        assertEquals("hello", result.content());
        assertTrue(result.truncated());
        assertEquals(10, result.usage().getPromptTokens());
        assertEquals(5, result.usage().getCompletionTokens());
        assertEquals(15, result.usage().getTotalTokens());
        assertEquals("gpt-5.6-terra", result.usage().getModel());

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://api.openai.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Map.class)
        );
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertEquals("gpt-5.6-terra", body.get("model"));
        assertFalse(body.containsKey("response_format"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void complete_bedrock_sendsJsonObjectResponseFormatAndGlobalModel() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        Map<String, Object> response = sampleResponse("{\"ok\":true}", "stop", 3, 2, 5);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        LlmChatCompletionsClient.ChatCompletionResult result = client.complete(
                List.of(Map.of("role", "user", "content", "plan")),
                0.3,
                256,
                true
        );

        assertEquals("{\"ok\":true}", result.content());
        assertFalse(result.truncated());
        assertEquals("global.openai.gpt-5.6-terra", result.usage().getModel());

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Map.class)
        );
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertEquals("global.openai.gpt-5.6-terra", body.get("model"));
        assertEquals(Map.of("type", "json_object"), body.get("response_format"));
        assertEquals("none", body.get("reasoning_effort"));
        assertEquals(256, body.get("max_completion_tokens"));
        String auth = entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertEquals("Bearer bedrock-key-test", auth);
    }

    @Test
    @SuppressWarnings("unchecked")
    void complete_mapsUnauthorizedToProviderSpecificError() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        null,
                        "{\"message\":\"invalid\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                ));

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> client.complete(List.of(Map.of("role", "user", "content", "x")), null, null, false)
        );
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Bedrock"));
    }

    @Test
    void extractUsage_parsesOpenAiCompatibleResponse() {
        Map<String, Object> response = sampleResponse("x", "stop", 12, 4, 16);
        OpenAIUsage usage = client.extractUsage(response, "global.openai.gpt-5.6-terra");
        assertEquals(12, usage.getPromptTokens());
        assertEquals(4, usage.getCompletionTokens());
        assertEquals(16, usage.getTotalTokens());
        assertEquals("global.openai.gpt-5.6-terra", usage.getModel());
    }

    @Test
    void extractUsage_ignoresPromptTokensDetails_persistenceUnchanged() {
        Map<String, Object> response = sampleResponseWithCacheDetails(
                "x", "stop", 2000, 100, 2100, 900, 1100);
        OpenAIUsage usage = client.extractUsage(response, "global.openai.gpt-5.6-terra");
        assertEquals(2000, usage.getPromptTokens());
        assertEquals(100, usage.getCompletionTokens());
        assertEquals(2100, usage.getTotalTokens());
        // No cache fields on OpenAIUsage — observation dump only
        assertFalse(usage.toString().toLowerCase().contains("cache"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void complete_bedrock_outboundBodyHasNoExplicitCacheOptions() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        Map<String, Object> response = sampleResponse("ok", "stop", 10, 2, 12);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        client.complete(
                List.of(
                        Map.of("role", "system", "content", "sys"),
                        Map.of("role", "user", "content", "q")
                ),
                null,
                null,
                false,
                "Grounded"
        );

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Map.class)
        );
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertFalse(PromptCacheProbe.bodyHasExplicitCacheOptions(body));
        assertFalse(body.containsKey("prompt_cache_options"));
        assertFalse(body.containsKey("prompt_cache_key"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void complete_bedrock_withNestedCacheUsage_stillParsesOnlyTopLevelTokenFields() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        Map<String, Object> response = sampleResponseWithCacheDetails(
                "ok", "stop", 2000, 300, 2300, 1500, 0);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        LlmChatCompletionsClient.ChatCompletionResult result = client.complete(
                List.of(Map.of("role", "user", "content", "hi")),
                null,
                null,
                false
        );

        assertEquals("ok", result.content());
        assertEquals(2000, result.usage().getPromptTokens());
        assertEquals(300, result.usage().getCompletionTokens());
        assertEquals(2300, result.usage().getTotalTokens());
        // Phase 1: extractUsage unchanged — nested cache fields are not mapped onto OpenAIUsage
        Map<String, Object> rawUsage = (Map<String, Object>) result.rawResponse().get("usage");
        assertTrue(rawUsage.containsKey("prompt_tokens_details"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeMultimodal_bedrock_withNestedCacheUsage_preservesRawUsageForObservation() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        Map<String, Object> response = sampleResponseWithCacheDetails(
                "vision-ok", "stop", 1800, 120, 1920, 0, 1600);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", "describe"),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,aaa"))
                ))
        );

        LlmChatCompletionsClient.ChatCompletionResult result = client.completeMultimodal(
                messages, 0.2, 256, false);

        assertEquals("vision-ok", result.content());
        assertEquals(1800, result.usage().getPromptTokens());
        Map<String, Object> details = (Map<String, Object>) ((Map<String, Object>) result.rawResponse().get("usage"))
                .get("prompt_tokens_details");
        assertEquals(0, details.get("cached_tokens"));
        assertEquals(1600, details.get("cache_write_tokens"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeMultimodal_sendsImageUrlJsonObjectAndDoesNotRequireStringContent() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        Map<String, Object> response = sampleResponse("{\"searchQueries\":[\"a\",\"b\",\"c\"]}", "stop", 8, 4, 12);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(response));

        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", "question"),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,aaa"))
        );
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", "sys"),
                Map.of("role", "user", "content", content)
        );

        LlmChatCompletionsClient.ChatCompletionResult result = client.completeMultimodal(
                messages, 0.2, 512, true);

        assertTrue(result.content().contains("searchQueries"));
        assertEquals(8, result.usage().getPromptTokens());
        assertTrue(result.elapsedMs() >= 0);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("https://bedrock-runtime.eu-west-1.amazonaws.com/openai/v1/chat/completions"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Map.class)
        );
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertEquals("global.openai.gpt-5.6-terra", body.get("model"));
        assertEquals("none", body.get("reasoning_effort"));
        assertEquals(512, body.get("max_completion_tokens"));
        assertEquals(Map.of("type", "json_object"), body.get("response_format"));
        assertEquals(0.2, body.get("temperature"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeMultimodal_mapsTimeoutTo504WithoutLeakingDataUrlInClientMessagePath() {
        ReflectionTestUtils.setField(client, "provider", "bedrock");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", "q"),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,SECRETFIXTURE"))
                ))
        );

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> client.completeMultimodal(messages, null, 256, true)
        );
        assertEquals(504, ex.getStatusCode());
        assertFalse(ex.getMessage().contains("SECRETFIXTURE"));
    }

    private static Map<String, Object> sampleResponse(
            String content,
            String finishReason,
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {
        return Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", finishReason,
                        "message", Map.of("role", "assistant", "content", content)
                )),
                "usage", Map.of(
                        "prompt_tokens", promptTokens,
                        "completion_tokens", completionTokens,
                        "total_tokens", totalTokens
                )
        );
    }

    private static Map<String, Object> sampleResponseWithCacheDetails(
            String content,
            String finishReason,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            int cachedTokens,
            int cacheWriteTokens
    ) {
        return Map.of(
                "choices", List.of(Map.of(
                        "finish_reason", finishReason,
                        "message", Map.of("role", "assistant", "content", content)
                )),
                "usage", Map.of(
                        "prompt_tokens", promptTokens,
                        "completion_tokens", completionTokens,
                        "total_tokens", totalTokens,
                        "prompt_tokens_details", Map.of(
                                "cached_tokens", cachedTokens,
                                "cache_write_tokens", cacheWriteTokens
                        )
                )
        );
    }
}
