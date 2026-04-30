package com.ai.openai_api_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_quota")
public class TenantQuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", unique = true)
    private String tenantId;
    private int baseLimit;
    private int extraTokens;
    private int tokensUsed;
    private String status;
    private LocalDateTime lastResetAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // getters/setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getBaseLimit() {
        return baseLimit;
    }

    public void setBaseLimit(int baseLimit) {
        this.baseLimit = baseLimit;
    }

    public int getExtraTokens() {
        return extraTokens;
    }

    public void setExtraTokens(int extraTokens) {
        this.extraTokens = extraTokens;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastResetAt() {
        return lastResetAt;
    }

    public void setLastResetAt(LocalDateTime lastResetAt) {
        this.lastResetAt = lastResetAt;
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
