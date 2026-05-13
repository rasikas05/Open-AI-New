package com.ai.openai_api_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "request_logs")
public class RequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 🔥 Proper relation instead of storing tenant/user/session again
    @ManyToOne
    @JoinColumn(name = "session_ref_id", nullable = false)
    private Session session;

    @Column(name = "title", length = 255)
    private String title;

    @Lob
    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;
    @Lob
    @Column(name = "sanitized_text", columnDefinition = "TEXT")
    private String sanitizedText;
    @Lob
    @Column(name = "openai_response", columnDefinition = "TEXT")
    private String openaiResponse;
    @Column(name = "tokens_used")
    private int tokensUsed;
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    // getters/setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getSanitizedText() {
        return sanitizedText;
    }

    public void setSanitizedText(String sanitizedText) {
        this.sanitizedText = sanitizedText;
    }

    public String getOpenaiResponse() {
        return openaiResponse;
    }

    public void setOpenaiResponse(String openaiResponse) {
        this.openaiResponse = openaiResponse;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}