package com.ai.openai_api_service.model;

import java.time.LocalDateTime;

public class TenantResponse {

    private Long id;
    private String tenantId;
    private String name;
    private String status;
    private LocalDateTime createdAt;

    public TenantResponse() {
    }

    public TenantResponse(Long id, String tenantId, String name, String status, LocalDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
