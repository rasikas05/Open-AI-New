package com.ai.openai_api_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OpenAIException.class)
    public ResponseEntity<Map<String, Object>> handleOpenAIException(OpenAIException e) {
        if (e.isAiServiceUnavailable()) {
            log.error(
                    "AI service unavailable | errorCode={} | technicalDetail={} | clientMessage={}",
                    AiServiceErrors.ERROR_CODE,
                    e.getTechnicalDetail() != null ? e.getTechnicalDetail() : e.getMessage(),
                    AiServiceErrors.USER_MESSAGE
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("errorCode", AiServiceErrors.ERROR_CODE);
            body.put("error", AiServiceErrors.USER_MESSAGE);
            body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String message = e.getReason() == null ? "Request failed" : e.getReason();
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", message,
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
