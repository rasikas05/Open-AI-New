package com.ai.openai_api_service.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Safe auth labels for logs — never invent Cognito identity for bypass.
 */
public final class AuthPrincipalLabels {

    private AuthPrincipalLabels() {
    }

    public static String forLog(Jwt jwt) {
        if (jwt == null) {
            return TenantAuthBypassSupport.PRINCIPAL_NAME;
        }
        String clientId = jwt.getClaimAsString("client_id");
        return clientId != null && !clientId.isBlank() ? clientId : jwt.getSubject();
    }
}
