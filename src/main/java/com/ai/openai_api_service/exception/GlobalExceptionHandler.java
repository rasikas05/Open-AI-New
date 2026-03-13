package com.ai.openai_api_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
