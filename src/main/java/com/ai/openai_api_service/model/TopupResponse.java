package com.ai.openai_api_service.model;

public class TopupResponse {
    private String message;
    private String tenantCode;
    private int tokensAdded;
    private TokenUsageDto usage;

    public TopupResponse() {
    }

    public TopupResponse(String message, String tenantCode, int tokensAdded, TokenUsageDto usage) {
        this.message = message;
        this.tenantCode = tenantCode;
        this.tokensAdded = tokensAdded;
        this.usage = usage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public int getTokensAdded() {
        return tokensAdded;
    }

    public void setTokensAdded(int tokensAdded) {
        this.tokensAdded = tokensAdded;
    }

    public TokenUsageDto getUsage() {
        return usage;
    }

    public void setUsage(TokenUsageDto usage) {
        this.usage = usage;
    }
}
