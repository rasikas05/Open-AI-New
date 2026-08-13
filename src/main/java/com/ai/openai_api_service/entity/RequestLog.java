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
    @Column(name = "business_protected_text", columnDefinition = "TEXT")
    private String businessProtectedText;

    @Lob
    @Column(name = "pii_sanitized_text", columnDefinition = "TEXT")
    private String piiSanitizedText;

    @Lob
    @Column(name = "openai_response_raw", columnDefinition = "TEXT")
    private String openaiResponseRaw;

    @Lob
    @Column(name = "final_response", columnDefinition = "TEXT")
    private String finalResponse;

    @Column(name = "business_protection_flag", columnDefinition = "BIT(1)")
    private Boolean businessProtectionFlag;

    @Column(name = "business_entities_count")
    private Integer businessEntitiesCount;

    @Lob
    @Column(name = "business_entities_json", columnDefinition = "TEXT")
    private String businessEntitiesJson;

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
    @Column(name = "lex_intent", length = 64)
    private String lexIntent;
    @Column(name = "business_object", length = 64)
    private String businessObject;
    @Column(name = "business_identifier", length = 128)
    private String businessIdentifier;

    /**
     * When set, this turn was superseded by a later edit revision ({@code request_logs.id}).
     * Active turns have null.
     */
    @Column(name = "superseded_by_request_log_id")
    private Long supersededByRequestLogId;

    /**
     * Effective routing mode used for this execution: AUTO, M3, or DOCS.
     * Legacy rows may be null (treat as AUTO when reading).
     */
    @Column(name = "mode", length = 10)
    private String mode;

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

    public String getBusinessProtectedText() {
        return businessProtectedText;
    }

    public void setBusinessProtectedText(String businessProtectedText) {
        this.businessProtectedText = businessProtectedText;
    }

    public String getPiiSanitizedText() {
        return piiSanitizedText;
    }

    public void setPiiSanitizedText(String piiSanitizedText) {
        this.piiSanitizedText = piiSanitizedText;
    }

    public String getOpenaiResponseRaw() {
        return openaiResponseRaw;
    }

    public void setOpenaiResponseRaw(String openaiResponseRaw) {
        this.openaiResponseRaw = openaiResponseRaw;
    }

    public String getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(String finalResponse) {
        this.finalResponse = finalResponse;
    }

    public Boolean getBusinessProtectionFlag() {
        return businessProtectionFlag;
    }

    public void setBusinessProtectionFlag(Boolean businessProtectionFlag) {
        this.businessProtectionFlag = businessProtectionFlag;
    }

    public Integer getBusinessEntitiesCount() {
        return businessEntitiesCount;
    }

    public void setBusinessEntitiesCount(Integer businessEntitiesCount) {
        this.businessEntitiesCount = businessEntitiesCount;
    }

    public String getBusinessEntitiesJson() {
        return businessEntitiesJson;
    }

    public void setBusinessEntitiesJson(String businessEntitiesJson) {
        this.businessEntitiesJson = businessEntitiesJson;
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

    public String getLexIntent() {
        return lexIntent;
    }

    public void setLexIntent(String lexIntent) {
        this.lexIntent = lexIntent;
    }

    public String getBusinessObject() {
        return businessObject;
    }

    public void setBusinessObject(String businessObject) {
        this.businessObject = businessObject;
    }

    public String getBusinessIdentifier() {
        return businessIdentifier;
    }

    public void setBusinessIdentifier(String businessIdentifier) {
        this.businessIdentifier = businessIdentifier;
    }

    public Long getSupersededByRequestLogId() {
        return supersededByRequestLogId;
    }

    public void setSupersededByRequestLogId(Long supersededByRequestLogId) {
        this.supersededByRequestLogId = supersededByRequestLogId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}