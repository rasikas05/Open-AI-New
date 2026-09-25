package com.ai.openai_api_service.service;

import com.ai.openai_api_service.exception.OpenAIException;
import com.ai.openai_api_service.model.python_rag.PythonRouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PythonRagServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private PythonRagService pythonRagService;

    @BeforeEach
    void setUp() {
        pythonRagService = new PythonRagService(180000);
        ReflectionTestUtils.setField(pythonRagService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(pythonRagService, "ragApiEnabled", true);
        ReflectionTestUtils.setField(pythonRagService, "pythonRagBaseUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(pythonRagService, "pythonRouteEndpoint", "/route");
        ReflectionTestUtils.setField(pythonRagService, "apiKey", "test-rag-key");
        ReflectionTestUtils.setField(pythonRagService, "apiKeyHeader", "x-api-key");
    }

    @Test
    void route_sendsXApiKeyHeader() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(PythonRouteResponse.class)))
                .thenReturn(ResponseEntity.ok(new PythonRouteResponse("rag")));

        pythonRagService.route("How to configure purchase settings");

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://localhost:8083/route"),
                entityCaptor.capture(),
                eq(PythonRouteResponse.class)
        );
        assertEquals("test-rag-key", entityCaptor.getValue().getHeaders().getFirst("x-api-key"));
    }

    @Test
    void route_emptyApiKey_throws() {
        ReflectionTestUtils.setField(pythonRagService, "apiKey", "  ");

        OpenAIException ex = assertThrows(
                OpenAIException.class,
                () -> pythonRagService.route("hello")
        );
        assertTrue(ex.getMessage().contains("python-rag.api.key"));
        assertEquals(503, ex.getStatusCode());
    }
}
