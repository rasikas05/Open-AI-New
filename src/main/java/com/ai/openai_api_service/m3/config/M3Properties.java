package com.ai.openai_api_service.m3.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

@ConfigurationProperties(prefix = "m3")
public class M3Properties {

    private static final Logger log = LoggerFactory.getLogger(M3Properties.class);

    private String baseUrl;
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    private String tenantId;
    private String scope = "Infor-M3";
    private String company = "100";
    private int tokenCacheBufferSeconds = 120;
    private int tokenTimeoutSeconds = 30;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public int getTokenCacheBufferSeconds() {
        return tokenCacheBufferSeconds;
    }

    public void setTokenCacheBufferSeconds(int tokenCacheBufferSeconds) {
        this.tokenCacheBufferSeconds = tokenCacheBufferSeconds;
    }

    public int getTokenTimeoutSeconds() {
        return tokenTimeoutSeconds;
    }

    public void setTokenTimeoutSeconds(int tokenTimeoutSeconds) {
        this.tokenTimeoutSeconds = tokenTimeoutSeconds;
    }

    @PostConstruct
    public void logProperties() {
        log.info("M3 Configuration Loaded: baseUrl={}, tokenUrl={}, clientId={}, username={}, tenantId={}, company={}, scope={}", 
                 baseUrl, tokenUrl, clientId, username, tenantId, company, scope);
    }
}
