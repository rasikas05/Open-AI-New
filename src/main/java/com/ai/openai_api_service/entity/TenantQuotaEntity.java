package com.ai.openai_api_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_quota", indexes = {
        @Index(name = "idx_tenant_quota_tenant_id", columnList = "tenant_id")
})
public class TenantQuotaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 50)
    private String tenantId;

    @Column(name = "base_limit", nullable = false)
    private Integer baseLimit;

    @Column(name = "extra_tokens", nullable = false)
    private Integer extraTokens = 0;

    @Column(name = "tokens_used", nullable = false)
    private Integer tokensUsed = 0;

    @Column(name = "last_reset_at", nullable = false)
    private LocalDateTime lastResetAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getBaseLimit() {
        return baseLimit;
    }

    public void setBaseLimit(Integer baseLimit) {
        this.baseLimit = baseLimit;
    }

    public Integer getExtraTokens() {
        return extraTokens;
    }

    public void setExtraTokens(Integer extraTokens) {
        this.extraTokens = extraTokens;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public LocalDateTime getLastResetAt() {
        return lastResetAt;
    }

    public void setLastResetAt(LocalDateTime lastResetAt) {
        this.lastResetAt = lastResetAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
