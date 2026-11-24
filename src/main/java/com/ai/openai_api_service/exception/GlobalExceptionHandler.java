package com.ai.openai_api_service.exception;

import com.ai.openai_api_service.config.RequestBodyTooLargeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String UNEXPECTED_ERROR = "An unexpected error occurred.";
    static final String UPSTREAM_ERROR = "An upstream service is temporarily unavailable.";
    static final String MALFORMED_BODY_ERROR = "Malformed request body.";

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
        String traceId = newTraceId();
        log.error("Upstream service error. traceId={}", traceId, e);
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(safeBody(HttpStatus.BAD_GATEWAY.value(), UPSTREAM_ERROR, traceId));
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

    /**
     * When Jackson reads a limited servlet body and hits {@link RequestBodyTooLargeException},
     * Spring wraps it as {@link HttpMessageNotReadableException} inside DispatcherServlet.
     * Map that cause chain to a safe 413 (not 400/500).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        if (findRequestBodyTooLarge(e) != null) {
            log.info("Rejected request: body exceeded configured maximum (via message conversion)");
            return payloadTooLargeResponse();
        }
        int status = HttpStatus.BAD_REQUEST.value();
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", MALFORMED_BODY_ERROR,
                        "status", status
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception e) {
        if (findRequestBodyTooLarge(e) != null) {
            log.info("Rejected request: body exceeded configured maximum");
            return payloadTooLargeResponse();
        }
        String traceId = newTraceId();
        log.error("Unexpected error. traceId={}", traceId, e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(safeBody(HttpStatus.INTERNAL_SERVER_ERROR.value(), UNEXPECTED_ERROR, traceId));
    }

    private static ResponseEntity<Map<String, Object>> payloadTooLargeResponse() {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "error", RequestBodyTooLargeException.SAFE_MESSAGE,
                        "status", HttpStatus.PAYLOAD_TOO_LARGE.value()
                ));
    }

    private static RequestBodyTooLargeException findRequestBodyTooLarge(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof RequestBodyTooLargeException tooLarge) {
                return tooLarge;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString();
    }

    private static Map<String, Object> safeBody(int status, String error, String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("status", status);
        body.put("traceId", traceId);
        return body;
    }
}
