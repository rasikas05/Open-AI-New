package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class UserResponse {

    private Long id;
    private String tenantCode;
    private String userId;
    private LocalDateTime createdAt;

    public UserResponse() {
    }

    public UserResponse(Long id, String tenantCode, String userId, LocalDateTime createdAt) {
        this.id = id;
        this.tenantCode = tenantCode;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
