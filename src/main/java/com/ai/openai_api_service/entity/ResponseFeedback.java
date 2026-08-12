package com.ai.openai_api_service.entity;

import com.ai.openai_api_service.model.FeedbackValue;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "response_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uk_response_feedback_request_log", columnNames = "request_log_id")
)
public class ResponseFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "request_log_id", nullable = false, unique = true)
    private RequestLog requestLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback", nullable = false, length = 10)
    private FeedbackValue feedback;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public RequestLog getRequestLog() {
        return requestLog;
    }

    public void setRequestLog(RequestLog requestLog) {
        this.requestLog = requestLog;
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
