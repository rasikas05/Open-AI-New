package com.ai.openai_api_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OpenAIException.class)
    public ResponseEntity<Map<String, Object>> handleOpenAIException(OpenAIException e) {
        int code = e.getStatusCode();
        int status = (code >= 400 && code < 500) ? code : 502;
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", e.getMessage(),
                        "status", status
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            validationErrors.put(error.getField(), error.getDefaultMessage());
        }
        int status = HttpStatus.BAD_REQUEST.value();
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", "Validation failed",
                        "status", status,
                        "details", validationErrors
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
