package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class ResponseFeedbackResponse {

    private Long requestLogId;
    private FeedbackValue feedback;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ResponseFeedbackResponse() {
    }

    public ResponseFeedbackResponse(
            Long requestLogId,
            FeedbackValue feedback,
            String comment,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.requestLogId = requestLogId;
        this.feedback = feedback;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
