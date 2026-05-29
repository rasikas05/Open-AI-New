package com.ai.openai_api_service.model;

public class SessionCountDto {

    private String tenantCode;
    private String userId;
    private long sessionCount;

    public SessionCountDto() {
    }

    public SessionCountDto(String tenantCode, String userId, long sessionCount) {
        this.tenantCode = tenantCode;
        this.userId = userId;
        this.sessionCount = sessionCount;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
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
