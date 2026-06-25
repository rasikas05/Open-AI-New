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
    @Column(name = "action_taken", length = 255)
    private String actionTaken;
    @Column(name = "sanitized_flag", columnDefinition = "BIT(1)")
    private Boolean sanitizedFlag;
    @Lob
    @Column(name = "openai_response", columnDefinition = "TEXT")
    private String openaiResponse;
    @Column(name = "tokens_used")
    private int tokensUsed;
    @Column(name = "prompt_tokens")
    private Integer promptTokens;
    @Column(name = "completion_tokens")
    private Integer completionTokens;
    @Column(name = "openai_model", length = 64)
    private String openaiModel;
    @Column(name = "retrieval_reason", length = 64)
    private String retrievalReason;
    @Column(name = "retrieval_time_ms")
    private Integer retrievalTimeMs;
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

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public Boolean getSanitizedFlag() {
        return sanitizedFlag;
    }

    public void setSanitizedFlag(Boolean sanitizedFlag) {
        this.sanitizedFlag = sanitizedFlag;
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

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public String getOpenaiModel() {
        return openaiModel;
    }

    public void setOpenaiModel(String openaiModel) {
        this.openaiModel = openaiModel;
    }

    public String getRetrievalReason() {
        return retrievalReason;
    }

    public void setRetrievalReason(String retrievalReason) {
        this.retrievalReason = retrievalReason;
    }

    public Integer getRetrievalTimeMs() {
        return retrievalTimeMs;
    }

    public void setRetrievalTimeMs(Integer retrievalTimeMs) {
        this.retrievalTimeMs = retrievalTimeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}