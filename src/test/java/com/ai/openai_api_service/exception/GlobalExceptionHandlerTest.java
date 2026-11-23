package com.ai.openai_api_service.exception;

import com.ai.openai_api_service.config.RequestBodyTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedException_returnsGeneric500_withTraceId_andDoesNotLeakMessage() {
        RuntimeException leaked = new RuntimeException(
                "SQLException: SELECT * FROM secrets WHERE host=secret-host"
        );

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpectedException(leaked);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(GlobalExceptionHandler.UNEXPECTED_ERROR, body.get("error"));
        assertEquals(500, body.get("status"));
        assertNotNull(body.get("traceId"));
        assertFalse(String.valueOf(body.get("traceId")).isBlank());

        String bodyText = body.toString();
        assertFalse(bodyText.contains("SELECT"), "must not leak SQL");
        assertFalse(bodyText.contains("secret-host"), "must not leak host");
        assertFalse(bodyText.contains("SQLException"), "must not leak exception type in body values");
    }

    @Test
    void restClientException_returnsGeneric502_withTraceId_andDoesNotLeakMessage() {
        RestClientException leaked = new RestClientException(
                "I/O error on GET request for \"http://secret-host:8083/chat\": Connection refused"
        );

        ResponseEntity<Map<String, Object>> response = handler.handleRestClientException(leaked);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(GlobalExceptionHandler.UPSTREAM_ERROR, body.get("error"));
        assertEquals(502, body.get("status"));
        assertNotNull(body.get("traceId"));
        assertFalse(String.valueOf(body.get("traceId")).isBlank());

        String bodyText = body.toString();
        assertFalse(bodyText.contains("secret-host"), "must not leak upstream host");
        assertFalse(bodyText.contains("Connection refused"), "must not leak raw upstream message");
    }

    @Test
    void responseStatusException_keepsControlledReason_withoutRequiringTraceId() {
        ResponseStatusException ex = new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Session does not belong to this user"
        );

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Session does not belong to this user", body.get("error"));
        assertEquals(403, body.get("status"));
        assertFalse(body.containsKey("traceId"), "traceId not required on intentional 403");
    }

    @Test
    void openAiUnavailable_keepsSafe503Shape() {
        OpenAIException ex = AiServiceErrors.unavailable("provider quota detail must stay server-side");

        ResponseEntity<Map<String, Object>> response = handler.handleOpenAIException(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(AiServiceErrors.ERROR_CODE, body.get("errorCode"));
        assertEquals(AiServiceErrors.USER_MESSAGE, body.get("error"));
        assertEquals(503, body.get("status"));
        assertFalse(body.containsKey("traceId"));
        assertFalse(String.valueOf(body.get("error")).contains("provider quota"));
    }

    @Test
    void validationException_keepsErrorDetailsShape() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "tenantCode", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Validation failed", body.get("error"));
        assertEquals(400, body.get("status"));
        assertInstanceOf(Map.class, body.get("details"));
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) body.get("details");
        assertEquals("must not be blank", details.get("tenantCode"));
        assertFalse(body.containsKey("traceId"));
    }

    @Test
    void responseStatusException_payloadTooLarge_returns413SafeMessage() {
        ResponseStatusException ex = new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                RequestBodyTooLargeException.SAFE_MESSAGE
        );

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(RequestBodyTooLargeException.SAFE_MESSAGE, body.get("error"));
        assertEquals(413, body.get("status"));
        assertFalse(String.valueOf(body.get("error")).contains("base64"));
    }

    @Test
    void httpMessageNotReadable_withBodyTooLargeCause_returns413SafeMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error",
                new RequestBodyTooLargeException(),
                null
        );

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(RequestBodyTooLargeException.SAFE_MESSAGE, body.get("error"));
        assertEquals(413, body.get("status"));
        assertFalse(body.containsKey("traceId"));
        assertFalse(String.valueOf(body).toLowerCase().contains("base64"));
    }

    @Test
    void httpMessageNotReadable_withoutBodyTooLarge_returns400Not500() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Unexpected character",
                new RuntimeException("bad json token"),
                null
        );

        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(GlobalExceptionHandler.MALFORMED_BODY_ERROR, body.get("error"));
        assertEquals(400, body.get("status"));
        assertFalse(String.valueOf(body).contains("bad json token"));
    }

    @Test
    void unexpectedException_withBodyTooLargeCause_returns413Not500() {
        RuntimeException wrapped = new RuntimeException(
                "wrapper",
                new RequestBodyTooLargeException()
        );

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpectedException(wrapped);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(RequestBodyTooLargeException.SAFE_MESSAGE, body.get("error"));
        assertEquals(413, body.get("status"));
        assertFalse(body.containsKey("traceId"));
    }
}
