package com.ai.openai_api_service.exception;

public class OpenAIException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String errorCode;
    private final boolean nonRetryable;
    private final String technicalDetail;

    public OpenAIException(String message, int statusCode) {
        this(message, statusCode, null, false, null);
    }

    public OpenAIException(
            String message,
            int statusCode,
            String errorCode,
            boolean nonRetryable,
            String technicalDetail
    ) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.nonRetryable = nonRetryable;
        this.technicalDetail = technicalDetail;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isNonRetryable() {
        return nonRetryable;
    }

    public String getTechnicalDetail() {
        return technicalDetail;
    }

    public boolean isAiServiceUnavailable() {
        return nonRetryable || AiServiceErrors.ERROR_CODE.equals(errorCode);
    }
}
