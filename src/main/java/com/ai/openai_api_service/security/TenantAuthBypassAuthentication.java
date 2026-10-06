package com.ai.openai_api_service.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;

/**
 * Temporary synthetic authentication for config-allowlisted tenants.
 * Not Cognito JWT and not M2M client credentials — app-created bypass only.
 */
public final class TenantAuthBypassAuthentication extends AbstractAuthenticationToken {

    private final String tenantCode;

    public TenantAuthBypassAuthentication(String tenantCode, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.tenantCode = Objects.requireNonNull(tenantCode, "tenantCode");
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return TenantAuthBypassSupport.PRINCIPAL_NAME;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    @Override
    public String getName() {
        return TenantAuthBypassSupport.PRINCIPAL_NAME;
    }
}
