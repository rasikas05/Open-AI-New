package com.ai.openai_api_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OpenAIException.class)
    public ResponseEntity<Map<String, Object>> handleOpenAIException(OpenAIException e) {
        int status = e.getStatusCode() == 401 ? 401 : 502;
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", e.getMessage(),
                        "status", status
                ));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleRestClientException(RestClientException e) {
        int status = HttpStatus.BAD_GATEWAY.value();
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", "Upstream service error: " + e.getMessage(),
                        "status", status
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception e) {
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", "Internal error: " + e.getMessage(),
                        "status", status
                ));
    }
}
