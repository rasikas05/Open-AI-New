package com.ai.openai_api_service.model;

public class SessionCountDto {

    private String tenantId;
    private String userId;
    private long sessionCount;

    public SessionCountDto() {
    }

    public SessionCountDto(String tenantId, String userId, long sessionCount) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.sessionCount = sessionCount;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(long sessionCount) {
        this.sessionCount = sessionCount;
    }
}
