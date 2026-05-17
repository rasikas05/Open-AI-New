package com.ai.openai_api_service.model;

import jakarta.validation.constraints.NotBlank;

public class SessionTitleUpdateRequest {

    @NotBlank(message = "tenantId is required")
    private String tenantId;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "title is required")
    private String title;

    public SessionTitleUpdateRequest() {
    }

    public SessionTitleUpdateRequest(String tenantId, String userId, String title) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.title = title;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
