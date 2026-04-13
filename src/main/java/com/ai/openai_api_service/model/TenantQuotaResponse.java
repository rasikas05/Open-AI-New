package com.ai.openai_api_service.model;

public class TenantQuotaResponse {
    private String tenantId;
    private int baseLimit;
    private int extraTokens;
    private int tokensUsed;
    private String status;
    private TokenUsageDto usage;

    public TenantQuotaResponse() {
    }

    public TenantQuotaResponse(String tenantId, int baseLimit, int extraTokens, int tokensUsed, String status, TokenUsageDto usage) {
        this.tenantId = tenantId;
        this.baseLimit = baseLimit;
        this.extraTokens = extraTokens;
        this.tokensUsed = tokensUsed;
        this.status = status;
        this.usage = usage;
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

    public TokenUsageDto getUsage() {
        return usage;
    }

    public void setUsage(TokenUsageDto usage) {
        this.usage = usage;
    }
}
