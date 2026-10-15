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
}
