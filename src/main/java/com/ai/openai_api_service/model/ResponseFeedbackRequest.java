package com.ai.openai_api_service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ResponseFeedbackRequest {

    @NotBlank(message = "tenantCode is required")
    @Schema(example = "infor")
    private String tenantCode;

    @NotBlank(message = "userId is required")
    @Schema(example = "rasika")
    private String userId;

    @NotBlank(message = "sessionId is required")
    @Schema(example = "session-001")
    private String sessionId;

    @NotNull(message = "requestLogId is required")
    @Schema(example = "12345")
    private Long requestLogId;

    @NotNull(message = "feedback is required")
    @Schema(example = "good", allowableValues = {"good", "bad"})
    private FeedbackValue feedback;

    @Size(max = 500, message = "comment must be at most 500 characters")
    @Schema(example = "This answer was exactly what I needed.")
    private String comment;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getRequestLogId() {
        return requestLogId;
    }

    public void setRequestLogId(Long requestLogId) {
        this.requestLogId = requestLogId;
    }

    public FeedbackValue getFeedback() {
        return feedback;
    }

    public void setFeedback(FeedbackValue feedback) {
        this.feedback = feedback;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
